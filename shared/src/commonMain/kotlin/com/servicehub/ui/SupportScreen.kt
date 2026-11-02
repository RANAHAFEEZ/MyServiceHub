package com.servicehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.servicehub.data.SupportRepository
import com.servicehub.data.TicketDto
import com.servicehub.model.formatLocalDateTime
import com.servicehub.model.friendlyBookingError
import kotlinx.coroutines.launch

/** For customers and providers: send a question to the admin and read the answers. */
@Composable
fun SupportScreen() {
    val repo = remember { SupportRepository() }
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var tickets by remember { mutableStateOf<List<TicketDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        repo.myTickets().onSuccess { tickets = it }.onFailure { error = friendlyBookingError(it.message); tickets = emptyList() }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Contact support", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(message, { message = it }, label = { Text("Your question or problem") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(
            enabled = !busy && subject.isNotBlank() && message.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    repo.createTicket(subject.trim(), message.trim())
                        .onSuccess { subject = ""; message = ""; notice = "Sent. We will reply here."; reloadKey++ }
                        .onFailure { error = friendlyBookingError(it.message) }
                    busy = false
                }
            }
        ) { Text(if (busy) "Sending..." else "Send") }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Your messages", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { reloadKey++ },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, LuxuryGold),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LuxuryGold,
                    containerColor = LuxurySurface // #1E1E1E
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Refresh",
                    color = LuxuryGold,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        val list = tickets
        if (list == null) Text("Loading...")
        if (list != null && list.isEmpty()) Text("No messages yet.")
        list?.forEach { t ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t.subject, style = MaterialTheme.typography.titleSmall)
                        Text(t.status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(formatLocalDateTime(t.createdAt), style = MaterialTheme.typography.bodySmall)
                    Text(t.message, style = MaterialTheme.typography.bodyMedium)
                    if (!t.adminReply.isNullOrBlank()) {
                        Text("Support: ${t.adminReply}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
