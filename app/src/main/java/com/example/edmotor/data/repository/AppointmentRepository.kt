package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.AppointmentCacheEntity
import com.example.edmotor.data.local.AppointmentDao
import com.example.edmotor.data.local.AppointmentEntity
import com.example.edmotor.data.local.AppointmentWorkEntity
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.remote.MockApiConnection
import com.example.edmotor.data.remote.MockAppointmentAvailability
import com.example.edmotor.data.remote.MockAppointmentCreateDto
import com.example.edmotor.data.remote.MockAppointmentDto
import com.example.edmotor.data.remote.MockAppointmentStatusDto
import com.example.edmotor.data.remote.checkAppointmentAvailability
import com.example.edmotor.data.remote.isDefinitiveMockAppointmentFailure
import com.example.edmotor.data.remote.listAppointments
import com.example.edmotor.data.remote.mockAppointmentFailureMessage
import com.example.edmotor.data.remote.mockAppointmentId
import com.example.edmotor.data.remote.submitAppointment
import com.example.edmotor.data.remote.submitAppointmentStatus
import com.example.edmotor.model.APPOINTMENT_MINIMUM_NOTICE_MILLIS
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentOperationResult
import com.example.edmotor.model.AppointmentServiceType
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle
import com.example.edmotor.model.isValidAppointmentStart
import com.example.edmotor.session.DemoOwnerProfile
import com.example.edmotor.session.LocalAccountIdentity
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

internal data class AppointmentAccount(
    val profileKey: String,
    val profileId: String,
    val role: UserRole,
    val localUserId: Int? = null,
    val cacheKey: String = profileKey
)

internal data class AppointmentSnapshot(
    val appointments: List<Appointment>,
    val syncState: AppointmentSyncState
)

class AppointmentRepository(
    private val appointmentDao: AppointmentDao,
    private val mockApi: MockApiConnection?,
    private val sessionManager: SessionManager,
    private val localDatabase: EdMotorDatabase? = null,
    private val workDatabase: EdMotorDatabase? = localDatabase
) {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val remoteWrites = Mutex()
    private val pendingSubmissions = mutableMapOf<RemoteBooking, RemoteSubmission>()
    private var lastRemoteCreationMillis = 0L

    internal fun accountFor(user: UserEntity?): AppointmentAccount? {
        val profileKey = user?.remoteAuthId ?: return null
        if (user.id <= 0) return null
        val role = UserRole.entries.firstOrNull { it.name == user.role } ?: return null
        if (localDatabase != null) {
            if (!LocalAccountIdentity.matchesRole(profileKey, role.name)) return null
            return AppointmentAccount(profileKey, user.id.toString(), role, localUserId = user.id)
        }
        val connection = mockApi ?: return null
        val profileId = connection.profileId(profileKey)
        if (role == UserRole.WORKSHOP) {
            if (user.username != DemoOwnerProfile.USERNAME ||
                !user.displayName.trim().equals(DemoOwnerProfile.DISPLAY_NAME, ignoreCase = true)
            ) return null
            if (profileId == null) {
                if (profileKey != DEMO_LOCAL_OWNER_KEY && !isStoredDemoMockApiProfileKey(profileKey)) return null
                // Conserva la cuenta Room y separa la agenda por proyecto.
                val agendaKey = "$DEMO_LOCAL_OWNER_KEY:${connection.projectKey}"
                return AppointmentAccount(profileKey, "", role, cacheKey = agendaKey)
            }
        }
        return profileId?.let { AppointmentAccount(profileKey, it, role) }
    }

    internal fun observeAppointments(account: AppointmentAccount): Flow<AppointmentSnapshot> {
        val userId = account.localUserId
        val snapshots = if (userId != null) {
            appointmentDao.observeLocalAppointments(userId, account.profileKey, account.role.name).map { rows ->
                val visible = if (accountFor(sessionManager.currentUser.value) == account) rows else emptyList()
                AppointmentSnapshot(visible.map { it.toModel() }, AppointmentSyncState.UP_TO_DATE)
            }
        } else {
            combine(
                appointmentDao.observeCachedAppointments(account.cacheKey, account.role.name),
                observeSync(account)
            ) { rows, syncState ->
                AppointmentSnapshot(rows.map { it.toModel() }, syncState)
            }
        }
        return snapshots.retryWhen { _, _ ->
            emit(AppointmentSnapshot(emptyList(), AppointmentSyncState.UNAVAILABLE))
            delay(POLL_INTERVAL_MS)
            true
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    internal fun refreshAppointments() {
        refreshRequests.tryEmit(Unit)
    }

    private fun observeSync(account: AppointmentAccount): Flow<AppointmentSyncState> = channelFlow {
        val requests = Channel<Unit>(Channel.CONFLATED)
        launch {
            refreshRequests.collect { requests.trySend(Unit) }
        }

        send(AppointmentSyncState.LOADING)
        while (isActive) {
            send(refreshCache(account))
            withTimeoutOrNull(POLL_INTERVAL_MS) { requests.receive() }
        }
    }.distinctUntilChanged()

    private suspend fun refreshCache(account: AppointmentAccount): AppointmentSyncState {
        val connection = mockApi ?: return AppointmentSyncState.NOT_CONFIGURED
        return try {
            remoteWrites.withLock {
                val user = activeUser(account)
                val profileFilter = account.profileId.takeIf { account.role == UserRole.CLIENT }
                val snapshot = connection.service.listAppointments(profileFilter)
                    .map { it.toCacheEntity(connection, account) }
                    .sortedWith(compareBy(AppointmentCacheEntity::scheduledAtMillis, AppointmentCacheEntity::id))

                currentCoroutineContext().ensureActive()
                requireActiveAccount(account, user)
                appointmentDao.replaceCachedAppointments(account.cacheKey, account.role.name, snapshot)
                AppointmentSyncState.UP_TO_DATE
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppointmentSyncState.UNAVAILABLE
        }
    }

    suspend fun createAppointment(
        client: UserEntity,
        vehicle: Vehicle,
        scheduledAtMillis: Long,
        serviceType: String
    ): AppointmentOperationResult {
        val account = accountFor(client)
        if (account == null || account.role != UserRole.CLIENT) {
            val message = if (localDatabase != null) "Entra como cliente para solicitar una cita."
            else "Selecciona un perfil de cliente en línea para solicitar una cita."
            return AppointmentOperationResult.Error(message)
        }
        val now = System.currentTimeMillis()
        val insufficientNotice = AppointmentOperationResult.Error(
            "Selecciona una fecha y hora con al menos 15 minutos de anticipación."
        )
        if (!isValidAppointmentStart(scheduledAtMillis)) return insufficientNotice
        if (serviceType !in AppointmentServiceType.options) {
            return AppointmentOperationResult.Error("Selecciona un tipo de servicio.")
        }
        if (vehicle.id <= 0 || vehicle.ownerUserId != client.id) {
            return AppointmentOperationResult.Error("Selecciona un vehículo registrado en tu garaje.")
        }

        val activeAppointment = AppointmentOperationResult.Error(
            "Ya tienes una cita activa. Puedes gestionarla en la agenda antes de solicitar otra."
        )
        localDatabase?.let { database ->
            if (scheduledAtMillis < now + APPOINTMENT_MINIMUM_NOTICE_MILLIS) return insufficientNotice
            return performLocalOperation(database, account) { localClient ->
                val registeredVehicle = database.vehicleDao().findById(vehicle.id)
                    ?.takeIf { it.ownerUserId == localClient.id }
                    ?: return@performLocalOperation AppointmentOperationResult.Error(
                        "Selecciona un vehículo registrado en tu garaje."
                    )
                if (appointmentDao.hasActiveLocalAppointment(localClient.id, account.profileKey)) {
                    return@performLocalOperation activeAppointment
                }
                val savedId = appointmentDao.insertLocalAppointment(
                    AppointmentEntity(
                        clientLocalUserId = localClient.id,
                        clientAuthId = account.profileKey,
                        clientName = localClient.displayName,
                        clientUsername = localClient.username,
                        vehicleBrand = registeredVehicle.brand,
                        vehicleModel = registeredVehicle.model,
                        vehicleYear = registeredVehicle.year,
                        vehiclePlate = "",
                        scheduledAtMillis = scheduledAtMillis,
                        description = serviceType,
                        status = AppointmentStatus.PENDING,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
                check(savedId in 1..Int.MAX_VALUE.toLong())
                saveAppointmentWork(
                    database,
                    AppointmentWorkEntity(
                        clientAuthId = account.profileKey,
                        appointmentId = savedId,
                        appointmentCreatedAtMillis = now,
                        vehicleId = registeredVehicle.id
                    )
                )
                AppointmentOperationResult.Success("Solicitud de cita guardada en este dispositivo.")
            }
        }

        val database = workDatabase
            ?: return AppointmentOperationResult.Error("No fue posible guardar el vínculo con el vehículo.")
        return performRemoteOperation(
            account,
            fallback = "No fue posible comprobar la cita. Actualiza la agenda antes de volver a solicitarla."
        ) { connection, user ->
            check(user.id == client.id)
            if (database.vehicleDao().findById(vehicle.id)?.ownerUserId != user.id) {
                return@performRemoteOperation AppointmentOperationResult.Error(
                    "Selecciona un vehículo registrado en tu garaje."
                )
            }
            val booking = RemoteBooking(account.profileKey, vehicle.id, scheduledAtMillis, serviceType)
            val sendTime = System.currentTimeMillis()
            pendingSubmissions.keys.removeAll { it.scheduledAtMillis <= sendTime }
            val previous = pendingSubmissions[booking]
            if (previous == null && scheduledAtMillis < sendTime + APPOINTMENT_MINIMUM_NOTICE_MILLIS) {
                return@performRemoteOperation insufficientNotice
            }
            val recovered = previous?.appointment ?: when (
                val availability = connection.service.checkAppointmentAvailability(account.profileId, previous?.request)
            ) {
                MockAppointmentAvailability.Available -> null
                MockAppointmentAvailability.HasActive -> return@performRemoteOperation activeAppointment
                is MockAppointmentAvailability.PreviouslySubmitted -> availability.appointment
            }
            if (recovered == null && scheduledAtMillis < System.currentTimeMillis() + APPOINTMENT_MINIMUM_NOTICE_MILLIS) {
                return@performRemoteOperation insufficientNotice
            }

            val createdAt = maxOf(sendTime, lastRemoteCreationMillis + 1)
            val body = previous?.request ?: MockAppointmentCreateDto(
                profileId = account.profileId,
                serviceType = serviceType,
                vehicleBrand = SHARED_VEHICLE_BRAND,
                vehicleModel = SHARED_VEHICLE_MODEL,
                vehicleYear = SHARED_VEHICLE_YEAR,
                scheduledAtMillis = scheduledAtMillis,
                status = AppointmentStatus.PENDING,
                createdAtMillis = createdAt,
                updatedAtMillis = createdAt
            )
            val submitted = recovered ?: run {
                requireActiveAccount(account, user)
                lastRemoteCreationMillis = maxOf(lastRemoteCreationMillis, body.createdAtMillis)
                pendingSubmissions[booking] = RemoteSubmission(body)
                try {
                    connection.service.submitAppointment(body)
                } catch (error: HttpException) {
                    if (isDefinitiveMockAppointmentFailure(error.code())) pendingSubmissions.remove(booking)
                    throw error
                }
            }
            pendingSubmissions[booking] = RemoteSubmission(body, submitted)
            database.withTransaction {
                requireActiveAccount(account, user)
                val registeredVehicle = database.vehicleDao().findById(vehicle.id)?.takeIf { it.ownerUserId == user.id }
                saveAppointmentWork(
                    database,
                    AppointmentWorkEntity(
                        clientAuthId = account.profileKey,
                        appointmentId = requireNotNull(mockAppointmentId(submitted.id)),
                        appointmentCreatedAtMillis = submitted.createdAtMillis,
                        vehicleId = registeredVehicle?.id,
                        finishedAtMillis = submitted.updatedAtMillis.takeIf {
                            submitted.status == AppointmentStatus.COMPLETED && it > 0
                        }
                    )
                )
                appointmentDao.upsertCachedAppointment(submitted.toCacheEntity(connection, account))
                requireActiveAccount(account, user)
            }
            pendingSubmissions.remove(booking)
            val message = if (recovered == null) "Solicitud de cita enviada."
            else "La solicitud anterior ya estaba registrada."
            AppointmentOperationResult.Success(message)
        }
    }

    suspend fun acceptAppointment(workshop: UserEntity, appointmentId: Long): AppointmentOperationResult =
        changeAppointment(workshop, appointmentId, AppointmentAction.CONFIRM)

    suspend fun finishAppointment(
        workshop: UserEntity,
        appointmentId: Long,
        expected: Appointment? = null
    ): AppointmentOperationResult {
        val account = accountFor(workshop)?.takeIf { it.role == UserRole.WORKSHOP }
            ?: return AppointmentOperationResult.Error("Entra como dueño para terminar la cita.")
        if (appointmentId <= 0L) return AppointmentOperationResult.Error("La cita no es válida.")
        val requestedFinish = System.currentTimeMillis()
        val changed = AppointmentOperationResult.Error("La cita cambió. Actualiza la agenda y vuelve a intentarlo.")

        localDatabase?.let { database ->
            return performLocalOperation(database, account) {
                val target = appointmentDao.findLocalAppointment(appointmentId)
                    ?: return@performLocalOperation AppointmentOperationResult.Error("La cita ya no está disponible.")
                if (!expected.matchesAppointment(appointmentId, target.clientAuthId, target.createdAtMillis)) {
                    return@performLocalOperation changed
                }
                applyAppointmentFinish(appointmentId, target.status, target.updatedAtMillis, requestedFinish) { finishedAt ->
                    if (target.status != AppointmentStatus.COMPLETED) {
                        check(appointmentDao.updateLocalAppointment(
                            appointmentId = appointmentId,
                            previousStatus = AppointmentStatus.CONFIRMED,
                            status = AppointmentStatus.COMPLETED,
                            scheduledAtMillis = null,
                            updatedAtMillis = finishedAt
                        ) == 1)
                    }
                    saveAppointmentWork(
                        database,
                        AppointmentWorkEntity(
                            clientAuthId = target.clientAuthId,
                            appointmentId = appointmentId,
                            appointmentCreatedAtMillis = target.createdAtMillis,
                            finishedAtMillis = finishedAt
                        )
                    )
                }
            }
        }

        val database = workDatabase
            ?: return AppointmentOperationResult.Error("No fue posible guardar el cierre en este dispositivo.")
        return performRemoteOperation(
            account,
            fallback = "No fue posible comprobar el cierre. Actualiza la agenda y vuelve a abrir la cita."
        ) { connection, user ->
            val target = connection.service.getAppointment(appointmentId.toString())
            check(mockAppointmentId(target.id) == appointmentId)
            val clientAuthId = connection.profileKey(target.profileId)
            if (!expected.matchesAppointment(appointmentId, clientAuthId, target.createdAtMillis)) {
                return@performRemoteOperation changed
            }
            applyAppointmentFinish(appointmentId, target.status, target.updatedAtMillis, requestedFinish) { finishedAt ->
                requireActiveAccount(account, user)
                val closed = if (target.status == AppointmentStatus.COMPLETED) target else {
                    connection.service.submitAppointmentStatus(
                        target,
                        MockAppointmentStatusDto(AppointmentStatus.COMPLETED, finishedAt)
                    )
                }
                database.withTransaction {
                    requireActiveAccount(account, user)
                    val workId = saveAppointmentWork(
                        database,
                        AppointmentWorkEntity(
                            clientAuthId = clientAuthId,
                            appointmentId = appointmentId,
                            appointmentCreatedAtMillis = closed.createdAtMillis,
                            finishedAtMillis = closed.updatedAtMillis
                        )
                    )
                    requireActiveAccount(account, user)
                    workId
                }
            }
        }
    }

    private suspend fun applyAppointmentFinish(
        appointmentId: Long,
        currentStatus: String,
        updatedAtMillis: Long,
        requestedFinish: Long,
        persist: suspend (finishedAtMillis: Long) -> Long
    ): AppointmentOperationResult {
        val finishedAt = when (currentStatus) {
            AppointmentStatus.CONFIRMED -> requestedFinish
            AppointmentStatus.COMPLETED -> updatedAtMillis
            else -> return AppointmentOperationResult.Error("Solo puedes terminar una cita confirmada.")
        }
        if (finishedAt <= 0L) {
            return AppointmentOperationResult.Error("No se encontró la hora en que se terminó esta cita.")
        }
        val workId = persist(finishedAt)
        return AppointmentOperationResult.Success("Cita #$appointmentId finalizada.", workId)
    }

    private fun Appointment?.matchesAppointment(id: Long, clientAuthId: String, createdAtMillis: Long): Boolean =
        this == null || (this.id == id && this.clientAuthId == clientAuthId && this.createdAtMillis == createdAtMillis)

    private suspend fun saveAppointmentWork(database: EdMotorDatabase, incoming: AppointmentWorkEntity): Long {
        val dao = database.appointmentWorkDao()
        val existing = dao.findForAppointment(
            incoming.clientAuthId, incoming.appointmentId, incoming.appointmentCreatedAtMillis
        )
        if (existing == null) return dao.insertIfAbsent(incoming).also { check(it > 0L) }

        val merged = existing.copy(
            vehicleId = existing.vehicleId ?: incoming.vehicleId,
            finishedAtMillis = existing.finishedAtMillis ?: incoming.finishedAtMillis
        )
        if (merged != existing) check(dao.update(merged) == 1)
        return existing.id
    }

    suspend fun rejectAppointment(workshop: UserEntity, appointmentId: Long): AppointmentOperationResult =
        changeAppointment(workshop, appointmentId, AppointmentAction.REJECT)

    suspend fun cancelAppointment(user: UserEntity, appointmentId: Long): AppointmentOperationResult =
        changeAppointment(user, appointmentId, AppointmentAction.CANCEL)

    suspend fun rescheduleAppointment(
        user: UserEntity,
        appointmentId: Long,
        scheduledAtMillis: Long
    ): AppointmentOperationResult =
        changeAppointment(user, appointmentId, AppointmentAction.RESCHEDULE, scheduledAtMillis)

    private suspend fun changeAppointment(
        user: UserEntity,
        appointmentId: Long,
        action: AppointmentAction,
        newStart: Long? = null
    ): AppointmentOperationResult {
        val account = accountFor(user)
            ?: return AppointmentOperationResult.Error("Entra con tu perfil para gestionar las citas.")
        if (action.workshopOnly && account.role != UserRole.WORKSHOP) {
            return AppointmentOperationResult.Error("Entra como dueño para revisar las solicitudes de cita.")
        }
        if (appointmentId <= 0L) return AppointmentOperationResult.Error("La cita no es válida.")

        val unavailable = AppointmentOperationResult.Error("La cita no está disponible para este perfil.")
        localDatabase?.let { database ->
            return performLocalOperation(database, account) { localUser ->
                val target = appointmentDao.findLocalAppointment(appointmentId) ?: return@performLocalOperation unavailable
                if (account.role == UserRole.CLIENT &&
                    (target.clientLocalUserId != localUser.id || target.clientAuthId != account.profileKey)
                ) {
                    return@performLocalOperation unavailable
                }
                applyAppointmentChange(
                    action, appointmentId, account.role, target.status, target.scheduledAtMillis, newStart
                ) { status, scheduledAtMillis, now ->
                    check(appointmentDao.updateLocalAppointment(
                        appointmentId = appointmentId,
                        previousStatus = target.status,
                        status = status,
                        scheduledAtMillis = scheduledAtMillis,
                        updatedAtMillis = now
                    ) == 1)
                }
            }
        }

        return performRemoteOperation(
            account,
            fallback = "No fue posible comprobar el cambio. Actualiza la agenda antes de volver a intentarlo."
        ) { connection, active ->
            val id = appointmentId.toString()
            val target = connection.service.getAppointment(id)
            check(target.id == id)
            val targetProfile = connection.profileKey(target.profileId)
            if (account.role == UserRole.CLIENT && targetProfile != account.profileKey) {
                return@performRemoteOperation unavailable
            }
            var latest = target
            val result = applyAppointmentChange(
                action, appointmentId, account.role, target.status, target.scheduledAtMillis, newStart
            ) { status, scheduledAtMillis, now ->
                requireActiveAccount(account, active)
                latest = connection.service.submitAppointmentStatus(
                    target,
                    MockAppointmentStatusDto(status.takeIf { it != target.status }, now, scheduledAtMillis)
                )
            }
            if (result is AppointmentOperationResult.Success && account.role == UserRole.CLIENT) {
                currentCoroutineContext().ensureActive()
                requireActiveAccount(account, active)
                appointmentDao.upsertCachedAppointment(latest.toCacheEntity(connection, account))
            }
            result
        }
    }

    private suspend fun applyAppointmentChange(
        action: AppointmentAction,
        appointmentId: Long,
        actorRole: UserRole,
        currentStatus: String,
        currentStart: Long,
        newStart: Long?,
        save: suspend (status: String, scheduledAtMillis: Long?, updatedAtMillis: Long) -> Unit
    ): AppointmentOperationResult {
        val now = System.currentTimeMillis()
        val newStatus = when (action) {
            AppointmentAction.CONFIRM, AppointmentAction.REJECT -> {
                val reviewedStatus = if (action == AppointmentAction.CONFIRM) AppointmentStatus.CONFIRMED
                else AppointmentStatus.REJECTED
                if (currentStatus != reviewedStatus && currentStatus != AppointmentStatus.PENDING) {
                    return AppointmentOperationResult.Error("La solicitud ya no está pendiente.")
                }
                if (action == AppointmentAction.CONFIRM && currentStatus == AppointmentStatus.PENDING &&
                    (!isValidAppointmentStart(currentStart) || currentStart <= now)
                ) {
                    return AppointmentOperationResult.Error("Ajusta la cita a una hora futura antes de confirmarla.")
                }
                reviewedStatus
            }

            AppointmentAction.CANCEL -> {
                if (currentStatus != AppointmentStatus.CANCELLED && !AppointmentStatus.isActive(currentStatus)) {
                    return AppointmentOperationResult.Error("Esta cita ya no se puede cancelar.")
                }
                AppointmentStatus.CANCELLED
            }

            AppointmentAction.RESCHEDULE -> {
                if (!AppointmentStatus.isActive(currentStatus)) {
                    return AppointmentOperationResult.Error("Esta cita ya no se puede reagendar.")
                }
                if (newStart == currentStart) {
                    return AppointmentOperationResult.Success("La cita #$appointmentId ya tiene ese horario.")
                }
                if (newStart == null || !isValidAppointmentStart(newStart) || newStart <= now) {
                    return AppointmentOperationResult.Error("Selecciona una fecha y hora futuras.")
                }
                if (actorRole == UserRole.CLIENT && newStart < now + APPOINTMENT_MINIMUM_NOTICE_MILLIS) {
                    return AppointmentOperationResult.Error("Selecciona una hora con al menos 15 minutos de anticipación.")
                }
                if (actorRole == UserRole.CLIENT) AppointmentStatus.PENDING else currentStatus
            }
        }
        if (newStatus != currentStatus || newStart != null) save(newStatus, newStart, now)
        val confirmation = "Cita #$appointmentId ${action.resultDescription}."
        return if (action == AppointmentAction.RESCHEDULE && actorRole == UserRole.CLIENT) {
            AppointmentOperationResult.Success("$confirmation El taller revisará el nuevo horario.")
        } else {
            AppointmentOperationResult.Success(confirmation)
        }
    }

    private suspend fun performLocalOperation(
        database: EdMotorDatabase,
        account: AppointmentAccount,
        operation: suspend (UserEntity) -> AppointmentOperationResult
    ): AppointmentOperationResult {
        return try {
            val user = activeUser(account)
            database.withTransaction {
                requireActiveAccount(account, user)
                val persisted = database.userDao().findById(requireNotNull(account.localUserId))
                if (persisted == null || accountFor(persisted) != account) {
                    return@withTransaction AppointmentOperationResult.Error("El perfil activo no tiene permiso para realizar esta acción.")
                }
                val result = operation(persisted)
                requireActiveAccount(account, user)
                result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppointmentOperationResult.Error("No fue posible guardar el cambio en este dispositivo.")
        }
    }

    private suspend fun performRemoteOperation(
        account: AppointmentAccount,
        fallback: String,
        request: suspend (MockApiConnection, UserEntity) -> AppointmentOperationResult
    ): AppointmentOperationResult {
        val connection = mockApi ?: return AppointmentOperationResult.Error("MockAPI no está configurado.")
        val user = try {
            activeUser(account)
        } catch (_: IllegalStateException) {
            return AppointmentOperationResult.Error("El perfil activo cambió. Vuelve a abrir la agenda.")
        }
        return try {
            remoteWrites.withLock {
                requireActiveAccount(account, user)
                val result = request(connection, user)
                currentCoroutineContext().ensureActive()
                requireActiveAccount(account, user)
                result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            e.response()?.errorBody()?.close()
            AppointmentOperationResult.Error(mockAppointmentFailureMessage(e.code(), fallback))
        } catch (_: Exception) {
            AppointmentOperationResult.Error(fallback)
        } finally {
            if (sessionManager.currentUser.value === user) refreshAppointments()
        }
    }

    private fun activeUser(account: AppointmentAccount): UserEntity {
        val user = checkNotNull(sessionManager.currentUser.value)
        check(accountFor(user) == account)
        return user
    }

    private fun requireActiveAccount(account: AppointmentAccount, user: UserEntity) {
        check(sessionManager.currentUser.value === user && accountFor(user) == account)
    }

    private fun MockAppointmentDto.toCacheEntity(
        connection: MockApiConnection,
        account: AppointmentAccount
    ): AppointmentCacheEntity {
        val ownerKey = connection.profileKey(profileId)
        val remoteId = requireNotNull(mockAppointmentId(id))

        return AppointmentCacheEntity(
            accountAuthId = account.cacheKey,
            accountRole = account.role.name,
            id = remoteId,
            clientAuthId = ownerKey,
            clientName = "Cliente #$profileId",
            clientUsername = "cliente-$profileId",
            clientPhone = null,
            vehicleBrand = SHARED_VEHICLE_BRAND,
            vehicleModel = SHARED_VEHICLE_MODEL,
            vehicleYear = SHARED_VEHICLE_YEAR,
            vehiclePlate = "",
            scheduledAtMillis = scheduledAtMillis,
            description = serviceType?.takeIf { it in AppointmentServiceType.options } ?: "Servicio general",
            status = status,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    private fun AppointmentCacheEntity.toModel() = Appointment(
        id = id,
        clientAuthId = clientAuthId,
        clientName = clientName.replace("Cliente demo ", "Cliente #"),
        clientUsername = clientUsername,
        clientPhone = clientPhone,
        vehicleBrand = vehicleBrand,
        vehicleModel = vehicleModel,
        vehicleYear = vehicleYear,
        vehiclePlate = vehiclePlate,
        scheduledAtMillis = scheduledAtMillis,
        description = description.takeIf { it in AppointmentServiceType.options } ?: "Servicio general",
        status = status,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    private fun AppointmentEntity.toModel() = Appointment(
        id = id.toLong(),
        clientAuthId = clientAuthId,
        clientName = clientName,
        clientUsername = clientUsername,
        clientPhone = clientPhone,
        vehicleBrand = vehicleBrand,
        vehicleModel = vehicleModel,
        vehicleYear = vehicleYear,
        vehiclePlate = vehiclePlate,
        scheduledAtMillis = scheduledAtMillis,
        description = description,
        status = status,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    private data class RemoteBooking(
        val profileKey: String,
        val vehicleId: Int,
        val scheduledAtMillis: Long,
        val serviceType: String
    )

    private data class RemoteSubmission(
        val request: MockAppointmentCreateDto,
        val appointment: MockAppointmentDto? = null
    )

    private enum class AppointmentAction(val workshopOnly: Boolean, val resultDescription: String) {
        CONFIRM(true, "confirmada"),
        REJECT(true, "rechazada"),
        CANCEL(false, "cancelada"),
        RESCHEDULE(false, "reagendada")
    }

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        const val SHARED_VEHICLE_BRAND = "Vehículo"
        const val SHARED_VEHICLE_MODEL = "registrado"
        const val SHARED_VEHICLE_YEAR = 0
    }
}
