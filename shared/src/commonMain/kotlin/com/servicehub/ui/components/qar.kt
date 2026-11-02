package com.servicehub.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.servicehub.data.AppConfig
import com.servicehub.model.Money
import com.servicehub.ui.theme.Brand

/** "12.50 QAR" */
fun qar(minor: Long): String = "${Money(minor).format()} ${AppConfig.CURRENCY}"

/** A rounded card with a thin border. Use it instead of Card everywhere. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 6.dp))
}

enum class PillKind { Good, Warn, Bad, Neutral, Gold }

/** A small coloured status label. */
@Composable
fun Pill(text: String, kind: PillKind = PillKind.Neutral) {
    val color: Color = when (kind) {
        PillKind.Good -> Brand.Good
        PillKind.Warn -> Brand.Warn
        PillKind.Bad -> Brand.Bad
        PillKind.Gold -> Brand.Gold
        PillKind.Neutral -> Brand.Muted
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Big number + small label. */
@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    AppCard(modifier = modifier, highlight = accent) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Pill-shaped tab bar that scrolls sideways when there are many tabs. */
@Composable
fun AppTabs(names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        names.forEachIndexed { i, name ->
            val on = i == selected
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}

/** Brand header at the top of every signed-in screen. */
@Composable
fun BrandHeader(title: String, subtitle: String, roleLabel: String, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Pill(roleLabel, PillKind.Gold)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onSignOut, shape = MaterialTheme.shapes.small) { Text("Sign out") }
    }
}

@Composable
fun ErrorText(text: String?) {
    if (text != null) Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun NoticeText(text: String?) {
    if (text != null) Text(text, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
}
