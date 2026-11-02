package com.servicehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.servicehub.data.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(auth: AuthRepository) {
    val scope = rememberCoroutineScope()
    var signUpMode by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("consumer") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ServiceHub", style = MaterialTheme.typography.headlineLarge)
        Text(if (signUpMode) "Create your account" else "Welcome back", style = MaterialTheme.typography.titleMedium)

        if (signUpMode) {
            OutlinedTextField(
                value = fullName, onValueChange = { fullName = it },
                label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Text("I want to", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = role == "consumer", onClick = { role = "consumer" }, label = { Text("Book services") })
                FilterChip(selected = role == "provider", onClick = { role = "provider" }, label = { Text("Offer my services") })
            }
        }
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password (min 6 characters)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            enabled = !busy && email.isNotBlank() && password.length >= 6 && (!signUpMode || fullName.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                message = null
                scope.launch {
                    val result = if (signUpMode) auth.signUp(email.trim(), password, fullName.trim(), role)
                    else auth.signIn(email.trim(), password)
                    busy = false
                    result.onFailure { message = it.message ?: "Something went wrong" }
                    // On success the session status changes and the app moves to the main screen by itself.
                    // (If "Confirm email" is ON in Supabase, sign-up needs the email confirmed before signing in.)
                    if (result.isSuccess && signUpMode) message = null
                }
            }
        ) { Text(if (busy) "Please wait..." else if (signUpMode) "Sign up" else "Sign in") }

        TextButton(onClick = { signUpMode = !signUpMode; message = null }) {
            Text(if (signUpMode) "I already have an account" else "New here? Create an account")
        }
    }
}
