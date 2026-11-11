
package com.example.edmotor.core.common

sealed class EdMotorException(message: String) : Exception(message) {

    class Validation(message: String) : EdMotorException(message)

    class PermissionDenied(
        message: String = "No tienes permisos para realizar esta operación."
    ) : EdMotorException(message)

    class NotFound(
        message: String = "No se encontró el registro solicitado."
    ) : EdMotorException(message)
}
