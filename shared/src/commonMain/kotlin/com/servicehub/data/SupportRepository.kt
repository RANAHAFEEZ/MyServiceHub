package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from

/** Customers and providers send queries to the admin ("Support" tab). */
class SupportRepository(private val client: SupabaseClient = supabase) {

    suspend fun createTicket(subject: String, message: String): Result<Unit> = runCatching {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Not signed in")
        client.from("support_tickets").insert(TicketInsert(uid, subject, message))
        Unit
    }

    /** Row Level Security returns only MY tickets. */
    suspend fun myTickets(): Result<List<TicketDto>> = runCatching {
        client.from("support_tickets").select().decodeList<TicketDto>().sortedByDescending { it.createdAt }
    }
}
