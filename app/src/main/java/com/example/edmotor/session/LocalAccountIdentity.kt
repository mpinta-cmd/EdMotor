package com.example.edmotor.session

import com.example.edmotor.model.UserRole
import java.util.UUID

object LocalAccountIdentity {
    private const val PREFIX = "local-account:"
    internal const val OWNER_KEY = "local-account:owner"

    fun isKey(key: String?): Boolean = key == OWNER_KEY || isClientKey(key)

    fun isClientKey(key: String?): Boolean {
        if (key == null || !key.startsWith(PREFIX)) return false
        val id = key.removePrefix(PREFIX)
        return runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    }

    internal fun createClientKey(): String = "$PREFIX${UUID.randomUUID()}"

    internal fun matchesRole(key: String?, role: String): Boolean = when (role) {
        UserRole.WORKSHOP.name -> key == OWNER_KEY
        UserRole.CLIENT.name -> isClientKey(key)
        else -> false
    }
}
