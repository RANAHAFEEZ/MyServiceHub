package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository(private val client: SupabaseClient = supabase) {

    /** Authenticated / NotAuthenticated / Initializing ... (the app screen follows this) */
    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    /** Email of the signed-in user (shown in the header so you always know which account is active). */
    fun currentEmail(): String? = client.auth.currentUserOrNull()?.email

    /** role must be "consumer" or "provider". The database trigger creates the profile automatically. */
    suspend fun signUp(emailValue: String, passwordValue: String, fullName: String, role: String): Result<Unit> =
        runCatching {
            client.auth.signUpWith(Email) {
                email = emailValue
                password = passwordValue
                data = buildJsonObject {
                    put("full_name", fullName)
                    put("role", role)
                }
            }
            Unit
        }

    suspend fun signIn(emailValue: String, passwordValue: String): Result<Unit> =
        runCatching {
            client.auth.signInWith(Email) {
                email = emailValue
                password = passwordValue
            }
            Unit
        }

    suspend fun signOut(): Result<Unit> = runCatching { client.auth.signOut() }

    /** The role always comes from the database (profiles.role), never from user metadata. */
    suspend fun getUserRole(): String =
        myProfile().getOrNull()?.role ?: "consumer"

    suspend fun myProfile(): Result<ProfileDto?> = runCatching {
        val uid = client.auth.currentUserOrNull()?.id ?: return@runCatching null
        client.from("profiles").select {
            filter { eq("id", uid) }
        }.decodeSingleOrNull<ProfileDto>()
    }
}