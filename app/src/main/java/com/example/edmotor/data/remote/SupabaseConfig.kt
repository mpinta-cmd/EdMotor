package com.example.edmotor.data.remote

/**
 * Credenciales públicas del proyecto Supabase de EdMotor.
 *
 * La publishable key está diseñada para estar en aplicaciones cliente.
 * Nunca colocar aquí una service_role/secret key.
 */
object SupabaseConfig {
    const val PROJECT_URL = "https://dcccmffdqjckrqstkuhw.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_SsPbM4Fl1yanAy1DY5kWAA_lPWwUgMi"

    const val AUTH_EMAIL_DOMAIN = "users.edmotor.app"
    const val REALTIME_POLL_FALLBACK_MS = 5_000L
}
