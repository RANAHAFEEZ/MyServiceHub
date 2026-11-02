package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Commission account. Providers collect cash from customers, so they owe the platform its commission.
 * Reads are limited by the database (provider sees own lines, admin sees all); writes are RPC-only.
 */
class LedgerRepository(private val client: SupabaseClient = supabase) {

    suspend fun mySummary(): Result<LedgerSummaryDto> = runCatching {
        client.postgrest.rpc("my_ledger_summary").decodeList<LedgerSummaryDto>().first()
    }

    suspend fun myEntries(): Result<List<LedgerEntryDto>> = runCatching {
        val id = client.auth.currentUserOrNull()?.id ?: error("Not signed in")
        client.from("provider_ledger").select { filter { eq("provider_id", id) } }
            .decodeList<LedgerEntryDto>().sortedByDescending { it.createdAt }
    }

    // ---- admin
    suspend fun adminBalances(): Result<List<ProviderBalanceDto>> = runCatching {
        client.postgrest.rpc("admin_provider_balances").decodeList<ProviderBalanceDto>()
    }

    suspend fun entriesFor(providerId: String): Result<List<LedgerEntryDto>> = runCatching {
        client.from("provider_ledger").select { filter { eq("provider_id", providerId) } }
            .decodeList<LedgerEntryDto>().sortedByDescending { it.createdAt }
    }

    suspend fun recordPayment(providerId: String, amountMinor: Long, note: String?): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "admin_record_payment",
            buildJsonObject {
                put("p_provider", providerId)
                put("p_amount_minor", amountMinor)
                put("p_note", note ?: "")
            }
        )
        Unit
    }

    suspend fun adjust(providerId: String, amountMinor: Long, note: String): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "admin_adjust_ledger",
            buildJsonObject {
                put("p_provider", providerId)
                put("p_amount_minor", amountMinor)
                put("p_note", note)
            }
        )
        Unit
    }

    suspend fun setCreditLimit(limitMinor: Long): Result<Unit> = runCatching {
        client.postgrest.rpc("admin_set_credit_limit", buildJsonObject { put("p_limit_minor", limitMinor) })
        Unit
    }

    /** The current limit (0 = no limit). */
    suspend fun creditLimit(): Result<Long> = runCatching {
        client.postgrest.rpc("my_ledger_summary").decodeList<LedgerSummaryDto>().first().limitMinor
    }
}
