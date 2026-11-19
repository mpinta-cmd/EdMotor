package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.AppointmentCacheEntity
import com.example.edmotor.data.local.AppointmentDao
import com.example.edmotor.data.local.AppointmentEntity
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.remote.MockApiConnection
import com.example.edmotor.data.remote.MockAppointmentCreateDto
import com.example.edmotor.data.remote.MockAppointmentDto
import com.example.edmotor.data.remote.MockAppointmentStatusDto
import com.example.edmotor.data.remote.findSubmittedAppointment
import com.example.edmotor.data.remote.hasConfirmedOverlap
import com.example.edmotor.data.remote.isDefinitiveMockAppointmentFailure
import com.example.edmotor.data.remote.listAppointments
import com.example.edmotor.data.remote.mockAppointmentFailureMessage
import com.example.edmotor.data.remote.mockAppointmentId
import com.example.edmotor.data.remote.submitAppointment
import com.example.edmotor.data.remote.submitAppointmentStatus
import com.example.edmotor.model.APPOINTMENT_DURATION_MILLIS
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
    private val localDatabase: EdMotorDatabase? = null
) {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val remoteWrites = Mutex()
    private val pendingSubmissions = mutableMapOf<RemoteBooking, MockAppointmentCreateDto>()
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
            val user = activeUser(account)
            val profileFilter = account.profileId.takeIf { account.role == UserRole.CLIENT }
            val snapshot = connection.service.listAppointments(profileFilter)
                .map { it.toCacheEntity(connection, account) }
                .sortedWith(compareBy(AppointmentCacheEntity::scheduledAtMillis, AppointmentCacheEntity::id))

            currentCoroutineContext().ensureActive()
            requireActiveAccount(account, user)
            appointmentDao.replaceCachedAppointments(account.cacheKey, account.role.name, snapshot)
            AppointmentSyncState.UP_TO_DATE
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
        if (scheduledAtMillis < now + APPOINTMENT_MINIMUM_NOTICE_MILLIS || !isValidAppointmentStart(scheduledAtMillis)) {
            return AppointmentOperationResult.Error("Selecciona una fecha y hora con al menos 15 minutos de anticipación.")
        }
        if (serviceType !in AppointmentServiceType.options) {
            return AppointmentOperationResult.Error("Selecciona un tipo de servicio.")
        }
        if (vehicle.id <= 0 || vehicle.ownerUserId != client.id) {
            return AppointmentOperationResult.Error("Selecciona un vehículo registrado en tu garaje.")
        }

        localDatabase?.let { database ->
            return performLocalOperation(database, account) { localClient ->
                val registeredVehicle = database.vehicleDao().findById(vehicle.id)
                    ?.takeIf { it.ownerUserId == localClient.id }
                    ?: return@performLocalOperation AppointmentOperationResult.Error(
                        "Selecciona un vehículo registrado en tu garaje."
                    )
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
                AppointmentOperationResult.Success("Solicitud de cita guardada en este dispositivo.")
            }
        }

        return performRemoteOperation(
            account,
            fallback = "No fue posible comprobar el envío. Actualiza las citas antes de volver a solicitarla."
        ) { connection, user ->
            check(user.id == client.id)
            val booking = RemoteBooking(account.profileKey, vehicle.id, scheduledAtMillis, serviceType)
            val sendTime = System.currentTimeMillis()
            pendingSubmissions.keys.removeAll { it.scheduledAtMillis <= sendTime }
            val previous = pendingSubmissions[booking]
            if (previous != null && connection.service.findSubmittedAppointment(previous) != null) {
                pendingSubmissions.remove(booking)
                return@performRemoteOperation AppointmentOperationResult.Success(
                    "La solicitud anterior ya estaba registrada."
                )
            }
            if (scheduledAtMillis < System.currentTimeMillis() + APPOINTMENT_MINIMUM_NOTICE_MILLIS) {
                return@performRemoteOperation AppointmentOperationResult.Error(
                    "Selecciona una fecha y hora con al menos 15 minutos de anticipación."
                )
            }

            val createdAt = maxOf(sendTime, lastRemoteCreationMillis + 1)
            val body = previous ?: MockAppointmentCreateDto(
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
            requireActiveAccount(account, user)
            lastRemoteCreationMillis = maxOf(lastRemoteCreationMillis, body.createdAtMillis)
            pendingSubmissions[booking] = body
            try {
                connection.service.submitAppointment(body)
            } catch (error: HttpException) {
                if (isDefinitiveMockAppointmentFailure(error.code())) pendingSubmissions.remove(booking)
                throw error
            }
            pendingSubmissions.remove(booking)
            AppointmentOperationResult.Success("Solicitud de cita enviada.")
        }
    }

    suspend fun acceptAppointment(workshop: UserEntity, appointmentId: Long): AppointmentOperationResult =
        reviewAppointment(workshop, appointmentId, AppointmentStatus.CONFIRMED)

    suspend fun rejectAppointment(workshop: UserEntity, appointmentId: Long): AppointmentOperationResult =
        reviewAppointment(workshop, appointmentId, AppointmentStatus.REJECTED)

    private suspend fun reviewAppointment(
        workshop: UserEntity,
        appointmentId: Long,
        newStatus: String
    ): AppointmentOperationResult {
        val account = accountFor(workshop)
        if (account == null || account.role != UserRole.WORKSHOP) {
            val message = if (localDatabase != null) "Entra como dueño para administrar las citas."
            else "Selecciona un perfil de dueño en línea para administrar las citas."
            return AppointmentOperationResult.Error(message)
        }
        if (appointmentId <= 0L) return AppointmentOperationResult.Error("La cita no es válida.")

        localDatabase?.let { database ->
            return performLocalOperation(database, account) {
                val target = appointmentDao.findLocalAppointment(appointmentId)
                if (target == null || target.status != AppointmentStatus.PENDING) {
                    return@performLocalOperation AppointmentOperationResult.Error("La solicitud ya no está pendiente.")
                }
                val now = System.currentTimeMillis()
                if (newStatus == AppointmentStatus.CONFIRMED) {
                    val start = target.scheduledAtMillis
                    if (!isValidAppointmentStart(start) || start <= now) {
                        return@performLocalOperation AppointmentOperationResult.Error("La hora de esta solicitud ya pasó o es inválida.")
                    }
                    if (appointmentDao.hasConfirmedOverlap(start - APPOINTMENT_DURATION_MILLIS, start + APPOINTMENT_DURATION_MILLIS)) {
                        return@performLocalOperation AppointmentOperationResult.Error("Ese horario ya está ocupado en la agenda.")
                    }
                }
                check(appointmentDao.updatePendingLocalAppointment(appointmentId, newStatus, now) == 1)
                val action = if (newStatus == AppointmentStatus.CONFIRMED) "confirmada" else "rechazada"
                AppointmentOperationResult.Success("Cita #$appointmentId $action.")
            }
        }

        return performRemoteOperation(
            account,
            fallback = "No fue posible comprobar el cambio. Actualiza la agenda antes de volver a intentarlo."
        ) { connection, user ->
            val id = appointmentId.toString()
            val target = connection.service.getAppointment(id)
            check(target.id == id)
            connection.profileKey(target.profileId)
            val action = if (newStatus == AppointmentStatus.CONFIRMED) "confirmada" else "rechazada"
            val success = AppointmentOperationResult.Success("Cita #$id $action.")
            if (target.status == newStatus) return@performRemoteOperation success
            if (target.status != AppointmentStatus.PENDING) {
                return@performRemoteOperation AppointmentOperationResult.Error("La solicitud ya no está pendiente.")
            }

            if (newStatus == AppointmentStatus.CONFIRMED) {
                val start = target.scheduledAtMillis
                if (!isValidAppointmentStart(start) || start <= System.currentTimeMillis()) {
                    return@performRemoteOperation AppointmentOperationResult.Error("La hora de esta solicitud ya pasó o es inválida.")
                }
                if (connection.service.hasConfirmedOverlap(target)) {
                    return@performRemoteOperation AppointmentOperationResult.Error("Ese horario ya está ocupado en la agenda.")
                }
                if (start <= System.currentTimeMillis()) {
                    return@performRemoteOperation AppointmentOperationResult.Error("La hora de esta solicitud ya pasó.")
                }
            }

            requireActiveAccount(account, user)
            connection.service.submitAppointmentStatus(
                target,
                MockAppointmentStatusDto(status = newStatus, updatedAtMillis = System.currentTimeMillis())
            )
            success
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

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        const val SHARED_VEHICLE_BRAND = "Vehículo"
        const val SHARED_VEHICLE_MODEL = "registrado"
        const val SHARED_VEHICLE_YEAR = 0
    }
}
