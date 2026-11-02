package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

/** Admin-only reads. The database refuses these for everybody else. */
class AdminOpsRepository(private val client: SupabaseClient = supabase) {

    suspend fun stats(): Result<AdminStatsDto> = runCatching {
        client.postgrest.rpc("admin_stats").decodeList<AdminStatsDto>().first()
    }

    suspend fun allBookings(): Result<List<BookingDto>> = runCatching {
        client.from("bookings").select().decodeList<BookingDto>().sortedByDescending { it.createdAt }
    }

    suspend fun bookingsByIds(ids: List<String>): Result<List<BookingDto>> = runCatching {
        if (ids.isEmpty()) emptyList()
        else client.from("bookings").select { filter { isIn("id", ids) } }.decodeList<BookingDto>()
    }

    suspend fun profilesByIds(ids: List<String>): Result<List<ProfileDto>> = runCatching {
        if (ids.isEmpty()) emptyList()
        else client.from("profiles").select { filter { isIn("id", ids) } }.decodeList<ProfileDto>()
    }

    /** Requests nobody answered in time become "expired". Returns how many. */
    suspend fun expireStaleRequests(): Result<Int> = runCatching {
        client.postgrest.rpc("expire_stale_requests").decodeAs<Int>()
    }
}
