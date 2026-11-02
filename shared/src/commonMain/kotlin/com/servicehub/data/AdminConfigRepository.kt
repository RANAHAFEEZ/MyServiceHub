package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Admin-only settings and moderation. Every change is checked and logged on the server. */
class AdminConfigRepository(private val client: SupabaseClient = supabase) {

    suspend fun categories(): Result<List<CategoryDto>> = runCatching {
        client.from("categories").select().decodeList<CategoryDto>().sortedBy { it.sortOrder }
    }

    suspend fun updateCategory(
        id: String, enabled: Boolean, pricingType: String,
        commissionBps: Int, minMinutes: Int, visitFeeMinor: Long
    ): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "admin_update_category",
            buildJsonObject {
                put("p_id", id)
                put("p_enabled", enabled)
                put("p_pricing_type", pricingType)
                put("p_commission_bps", commissionBps)
                put("p_min_minutes", minMinutes)
                put("p_visit_fee_minor", visitFeeMinor)
            }
        )
        Unit
    }

    suspend fun settings(): Result<AppSettingsDto> = runCatching {
        client.from("app_settings").select().decodeSingle<AppSettingsDto>()
    }

    suspend fun updateSettings(
        currency: String, expiryMinutes: Int, maxOpen: Int, minNotice: Int,
        maxDays: Int, maxMinutes: Int, maxQuantity: Int
    ): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "admin_update_settings",
            buildJsonObject {
                put("p_currency", currency)
                put("p_expiry", expiryMinutes)
                put("p_max_open", maxOpen)
                put("p_min_notice", minNotice)
                put("p_max_days", maxDays)
                put("p_max_minutes", maxMinutes)
                put("p_max_quantity", maxQuantity)
            }
        )
        Unit
    }

    suspend fun users(role: String): Result<List<ProfileDto>> = runCatching {
        client.from("profiles").select {
            filter { eq("role", role) }
        }.decodeList<ProfileDto>().sortedBy { it.fullName.lowercase() }
    }

    suspend fun setBlocked(userId: String, blocked: Boolean): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "admin_set_user_blocked",
            buildJsonObject {
                put("p_user", userId)
                put("p_blocked", blocked)
            }
        )
        Unit
    }

    suspend fun tickets(status: String): Result<List<TicketDto>> = runCatching {
        client.from("support_tickets").select {
            filter { eq("status", status) }
        }.decodeList<TicketDto>().sortedByDescending { it.createdAt }
    }

    /** status: open | pending | closed */
    suspend fun replyTicket(ticketId: String, reply: String, status: String): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "admin_reply_ticket",
            buildJsonObject {
                put("p_ticket", ticketId)
                put("p_reply", reply)
                put("p_status", status)
            }
        )
        Unit
    }

    suspend fun audit(): Result<List<AuditDto>> = runCatching {
        client.from("admin_audit").select().decodeList<AuditDto>().sortedByDescending { it.id }.take(30)
    }
}
