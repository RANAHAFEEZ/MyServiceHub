package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/** One shared client for the whole app. */
val supabase: SupabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl = AppConfig.SUPABASE_URL,
        supabaseKey = AppConfig.SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }
}
