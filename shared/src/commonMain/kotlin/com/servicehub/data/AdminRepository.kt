package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Only works for a signed-in ADMIN. Row Level Security and the database functions enforce that. */
class AdminRepository(private val client: SupabaseClient = supabase) {

    suspend fun providersByStatus(status: String): Result<List<AdminProviderRow>> = runCatching {
        val providers = client.from("provider_profiles").select {
            filter { eq("verification", status) }
        }.decodeList<ProviderProfileDto>()
        if (providers.isEmpty()) return@runCatching emptyList<AdminProviderRow>()

        val ids = providers.map { it.userId }
        val profiles = client.from("profiles").select {
            filter { isIn("id", ids) }
        }.decodeList<ProfileDto>().associateBy { it.id }

        providers.map { AdminProviderRow(it, profiles[it.userId]) }
    }

    /** status: approved | rejected | suspended | pending */
    suspend fun setVerification(providerId: String, status: String): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "admin_set_provider_verification",
            buildJsonObject {
                put("p_provider", providerId)
                put("p_status", status)
            }
        )

    }
}
