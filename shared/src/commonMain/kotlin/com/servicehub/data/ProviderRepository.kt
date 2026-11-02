package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProviderRepository(private val client: SupabaseClient = supabase) {

    private fun uid(): String = client.auth.currentUserOrNull()?.id ?: error("Not signed in")

    suspend fun myProviderProfile(): Result<ProviderProfileDto?> = runCatching {
        val id = uid()
        client.from("provider_profiles").select {
            filter { eq("user_id", id) }
        }.decodeSingleOrNull<ProviderProfileDto>()
    }

    /** A provider has at most ONE service (one trade). */
    suspend fun myService(): Result<ProviderServiceDto?> = runCatching {
        val id = uid()
        client.from("provider_services").select {
            filter { eq("provider_id", id) }
        }.decodeList<ProviderServiceDto>().firstOrNull()
    }

    /** Saves name/phone/city (profiles) and experience/bio (provider_profiles). */
    suspend fun saveProfile(
        fullName: String, phone: String, city: String, experienceYears: Int, bio: String
    ): Result<Unit> = runCatching {
        val id = uid()
        client.from("profiles").update({
            set("full_name", fullName)
            set("phone", phone)
            set("city", city)
        }) {
            filter { eq("id", id) }
        }
        client.from("provider_profiles").update({
            set("experience_years", experienceYears)
            set("bio", bio)
        }) {
            filter { eq("user_id", id) }
        }
        Unit
    }

    /**
     * Sets the provider's one trade and rate. The server refuses a trade change while jobs are open
     * and sends the profile back to "waiting for approval".
     */
    suspend fun setMyService(categoryId: String, rateMinor: Long, active: Boolean): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "set_my_service",
            buildJsonObject {
                put("p_category", categoryId)
                put("p_rate_minor", rateMinor)
                put("p_active", active)
            }
        )
        Unit
    }
}
