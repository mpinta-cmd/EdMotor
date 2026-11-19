package com.example.edmotor.data.repository

import com.example.edmotor.data.local.AppointmentCacheEntity
import com.example.edmotor.data.local.AppointmentDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.remote.MockApiConnection
import com.example.edmotor.data.remote.MockAppointmentCreateDto
import com.example.edmotor.data.remote.MockAppointmentDto
import com.example.edmotor.data.remote.MockAppointmentStatusDto
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentOperationResult
import com.example.edmotor.model.AppointmentServiceType
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle
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
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class AppointmentAccount(val profileKey: String, val profileId: String, val role: UserRole)

internal data class AppointmentSnapshot(
    val appointments: List<Appointment>,
    val syncState: AppointmentSyncState
)

class AppointmentRepository(
    private val appointmentDao: AppointmentDao,
    private val mockApi: MockApiConnection?,
    private val sessionManager: SessionManager
) {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    internal fun accountFor(user: UserEntity?): AppointmentAccount? {
        val profileKey = user?.remoteAuthId ?: return null
        val profileId = mockApi?.profileId(profileKey) ?: return null
        val role = UserRole.entries.firstOrNull { it.name == user.role } ?: return null
        return AppointmentAccount(profileKey, profileId, role)
    }

    internal fun observeAppointments(account: AppointmentAccount): Flow<AppointmentSnapshot> =
        combine(
            appointmentDao.observeCachedAppointments(account.profileKey, account.role.name),
            observeSync(account)
        ) { rows, syncState ->
            AppointmentSnapshot(rows.map { it.toModel() }, syncState)
        }.retryWhen { _, _ ->
            emit(AppointmentSnapshot(emptyList(), AppointmentSyncState.UNAVAILABLE))
            delay(POLL_INTERVAL_MS)
            true
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

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
            requireActiveAccount(account)
            val profileFilter = account.profileId.takeIf { account.role == UserRole.CLIENT }
            val snapshot = connection.service.getAppointments(profileFilter)
                .filter { profileFilter == null || it.profileId == profileFilter }
                .map { it.toCacheEntity(connection, account) }
                .sortedWith(compareBy(AppointmentCacheEntity::scheduledAtMillis, AppointmentCacheEntity::id))

            currentCoroutineContext().ensureActive()
            requireActiveAccount(account)
            appointmentDao.replaceCachedAppointments(account.profileKey, account.role.name, snapshot)
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
            return AppointmentOperationResult.Error("Selecciona un perfil de cliente de MockAPI para solicitar citas de prueba.")
        }
        val now = System.currentTimeMillis()
        if (scheduledAtMillis < now + MINIMUM_NOTICE_MS || !isValidStart(scheduledAtMillis)) {
            return AppointmentOperationResult.Error("Selecciona una fecha y hora con al menos 15 minutos de anticipación.")
        }
        if (serviceType !in AppointmentServiceType.options) {
            return AppointmentOperationResult.Error("Selecciona un servicio de demostración.")
        }
        if (vehicle.id <= 0 || vehicle.ownerUserId != client.id) {
            return AppointmentOperationResult.Error("Selecciona un vehículo registrado en tu garaje.")
        }

        return performRemoteOperation(
            account,
            fallback = "No fue posible comprobar el envío. Actualiza las citas antes de volver a solicitarla."
        ) { connection ->
            val body = MockAppointmentCreateDto(
                profileId = account.profileId,
                serviceType = serviceType,
                vehicleBrand = DEMO_VEHICLE_BRAND,
                vehicleModel = DEMO_VEHICLE_MODEL,
                vehicleYear = DEMO_VEHICLE_YEAR,
                scheduledAtMillis = scheduledAtMillis,
                status = AppointmentStatus.PENDING,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            val created = connection.service.createAppointment(body)
            check(appointmentId(created.id) != null && created.profileId == account.profileId)
            check(created.status == AppointmentStatus.PENDING && created.scheduledAtMillis == scheduledAtMillis)
            AppointmentOperationResult.Success("Solicitud de demostración enviada.")
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
            return AppointmentOperationResult.Error("Selecciona un perfil de taller de MockAPI para administrar citas de prueba.")
        }
        if (appointmentId <= 0L) return AppointmentOperationResult.Error("La cita no es válida.")

        return performRemoteOperation(
            account,
            fallback = "No fue posible comprobar el cambio. Actualiza la agenda antes de volver a intentarlo."
        ) { connection ->
            val id = appointmentId.toString()
            val agenda = if (newStatus == AppointmentStatus.CONFIRMED) connection.service.getAppointments() else null
            val target = if (agenda == null) connection.service.getAppointment(id) else agenda.singleOrNull { it.id == id }
            if (target == null || target.id != id || target.status != AppointmentStatus.PENDING) {
                return@performRemoteOperation AppointmentOperationResult.Error("La solicitud ya no está pendiente.")
            }

            if (agenda != null) {
                val start = target.scheduledAtMillis
                if (!isValidStart(start) || start <= System.currentTimeMillis()) {
                    return@performRemoteOperation AppointmentOperationResult.Error("La hora de esta solicitud ya pasó o es inválida.")
                }
                val overlaps = agenda.any { appointment ->
                    appointment.status == AppointmentStatus.CONFIRMED &&
                        isValidStart(appointment.scheduledAtMillis) &&
                        appointment.scheduledAtMillis < start + APPOINTMENT_DURATION_MS &&
                        appointment.scheduledAtMillis + APPOINTMENT_DURATION_MS > start
                }
                if (overlaps) {
                    return@performRemoteOperation AppointmentOperationResult.Error("Ese horario ya está ocupado en la agenda de prueba.")
                }
            }

            requireActiveAccount(account)
            val updated = connection.service.updateAppointment(
                id,
                MockAppointmentStatusDto(status = newStatus, updatedAtMillis = System.currentTimeMillis())
            )
            check(updated.id == id && updated.status == newStatus)
            val message = if (newStatus == AppointmentStatus.CONFIRMED) {
                "Cita de prueba #$id confirmada."
            } else {
                "Cita de prueba #$id rechazada."
            }
            AppointmentOperationResult.Success(message)
        }
    }

    private suspend fun performRemoteOperation(
        account: AppointmentAccount,
        fallback: String,
        request: suspend (MockApiConnection) -> AppointmentOperationResult
    ): AppointmentOperationResult {
        val connection = mockApi ?: return AppointmentOperationResult.Error("MockAPI no está configurado.")
        return try {
            requireActiveAccount(account)
            val result = request(connection)
            currentCoroutineContext().ensureActive()
            requireActiveAccount(account)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppointmentOperationResult.Error(fallback)
        } finally {
            if (accountFor(sessionManager.currentUser.value) == account) refreshAppointments()
        }
    }

    private fun requireActiveAccount(account: AppointmentAccount) {
        check(accountFor(sessionManager.currentUser.value) == account)
    }

    private fun MockAppointmentDto.toCacheEntity(
        connection: MockApiConnection,
        account: AppointmentAccount
    ): AppointmentCacheEntity {
        val ownerKey = connection.profileKey(profileId)
        val remoteId = requireNotNull(appointmentId(id))
        check(isValidStart(scheduledAtMillis))
        check(status == AppointmentStatus.PENDING || status == AppointmentStatus.CONFIRMED || status == AppointmentStatus.REJECTED)

        return AppointmentCacheEntity(
            accountAuthId = account.profileKey,
            accountRole = account.role.name,
            id = remoteId,
            clientAuthId = ownerKey,
            clientName = "Cliente demo $profileId",
            clientUsername = "demo-$profileId",
            clientPhone = null,
            vehicleBrand = DEMO_VEHICLE_BRAND,
            vehicleModel = DEMO_VEHICLE_MODEL,
            vehicleYear = DEMO_VEHICLE_YEAR,
            vehiclePlate = "",
            scheduledAtMillis = scheduledAtMillis,
            description = serviceType.takeIf { it in AppointmentServiceType.options } ?: "Servicio de demostración",
            status = status,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    private fun AppointmentCacheEntity.toModel() = Appointment(
        id = id,
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

    private fun appointmentId(value: String): Long? =
        value.toLongOrNull()?.takeIf { it > 0L && it.toString() == value }

    private fun isValidStart(start: Long): Boolean = start in 1..(Long.MAX_VALUE - APPOINTMENT_DURATION_MS)

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        const val MINIMUM_NOTICE_MS = 15 * 60_000L
        const val APPOINTMENT_DURATION_MS = 60 * 60_000L
        const val DEMO_VEHICLE_BRAND = "Demostración"
        const val DEMO_VEHICLE_MODEL = "Vehículo de prueba"
        const val DEMO_VEHICLE_YEAR = 2020
    }
}
