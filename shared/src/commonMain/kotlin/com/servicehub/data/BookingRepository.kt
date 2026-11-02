package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class BookingRepository(private val client: SupabaseClient = supabase) {

    /** Price preview from the server (same maths the booking will use). */
    suspend fun quote(providerId: String, categoryId: String, minutes: Int, quantity: Int): Result<QuoteDto> =
        runCatching {
            client.postgrest.rpc(
                "get_quote",
                buildJsonObject {
                    put("p_provider", providerId)
                    put("p_category", categoryId)
                    put("p_minutes", minutes)
                    put("p_quantity", quantity)
                }
            ).decodeList<QuoteDto>().first()
        }

    /** Returns the new booking id. scheduledAt looks like 2026-11-02T09:00:00+03:00 */
    suspend fun createBooking(
        providerId: String, categoryId: String, scheduledAt: String, address: String,
        notes: String, minutes: Int, quantity: Int
    ): Result<String> = runCatching {
        client.postgrest.rpc(
            "create_booking",
            buildJsonObject {
                put("p_provider", providerId)
                put("p_category", categoryId)
                put("p_scheduled_at", scheduledAt)
                put("p_address", address)
                if (notes.isNotBlank()) put("p_notes", notes)
                put("p_minutes", minutes)
                put("p_quantity", quantity)
            }
        ).decodeAs<String>()
    }

    /** Row Level Security returns only MY bookings (as consumer or as provider). */
    suspend fun myBookings(): Result<List<BookingDto>> = runCatching {
        client.from("bookings").select().decodeList<BookingDto>().sortedByDescending { it.createdAt }
    }

    /** newStatus: accepted, declined, on_the_way, in_progress, completed, paid, cancelled ... */
    suspend fun changeStatus(bookingId: String, newStatus: String): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "change_booking_status",
            buildJsonObject {
                put("p_booking", bookingId)
                put("p_new", newStatus)
            }
        )
        Unit
    }

    suspend fun submitReview(bookingId: String, rating: Int, comment: String): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "submit_review",
            buildJsonObject {
                put("p_booking", bookingId)
                put("p_rating", rating)
                if (comment.isNotBlank()) put("p_comment", comment)
            }
        )
        Unit
    }

    /** The database only returns profiles I am allowed to see (the other side of an accepted booking). */
    suspend fun profilesByIds(ids: List<String>): Result<List<ProfileDto>> = runCatching {
        client.from("profiles").select {
            filter { isIn("id", ids) }
        }.decodeList<ProfileDto>()
    }
}
