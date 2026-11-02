package com.servicehub.data

/**
 * Copy this file to AppConfig.kt and fill in your values.
 * Supabase Dashboard > Project Settings > API:
 *  - Project URL
 *  - the PUBLISHABLE key (safe inside the app because Row Level Security protects the data)
 * NEVER put the secret / service_role key here.
 */
object AppConfig {
    const val SUPABASE_URL = "https://YOUR-PROJECT-REF.supabase.co"
    const val SUPABASE_PUBLISHABLE_KEY = "PASTE-YOUR-PUBLISHABLE-KEY-HERE"
    const val CURRENCY = "QAR"
}
