package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ComplaintRepository(private val client: SupabaseClient = supabase) {

    /** Consumer or provider of that booking reports a problem. The database checks they belong to the booking. */
    suspend fun raise(bookingId: String, reason: String, details: String): Result<Unit> = runCatching {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Not signed in")
        client.from("complaints").insert(ComplaintInsert(bookingId, uid, reason, details.ifBlank { null }))
        Unit
    }

    /** Admin only (Row Level Security): complaints with a given status. */
    suspend fun byStatus(status: String): Result<List<ComplaintDto>> = runCatching {
        client.from("complaints").select {
            filter { eq("status", status) }
        }.decodeList<ComplaintDto>().sortedByDescending { it.createdAt }
    }

    /** status: open | in_review | resolved | rejected */
    suspend fun resolve(complaintId: String, status: String, notes: String): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "admin_resolve_complaint",
            buildJsonObject {
                put("p_complaint", complaintId)
                put("p_status", status)
                if (notes.isNotBlank()) put("p_notes", notes)
            }
        )
        Unit
    }
}
