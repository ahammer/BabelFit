@file:Suppress("TooManyFunctions")
package ca.adamhammer.babelfit.samples.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.adamhammer.babelfit.UsageTracker

// ── DarkCard ────────────────────────────────────────────────────────────────

@Composable
fun DarkCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = DarkCardColor,
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

// ── ColorBorderCard ─────────────────────────────────────────────────────────

@Composable
fun ColorBorderCard(
    accentColor: Color,
    alpha: Float = 0.08f,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor)
        )
        Surface(
            modifier = Modifier.weight(1f),
            color = accentColor.copy(alpha = alpha),
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
        ) {
            Box(modifier = Modifier.padding(10.dp)) { content() }
        }
    }
}

// ── AvatarCircle ────────────────────────────────────────────────────────────

@Composable
fun AvatarCircle(name: String, color: Color, size: Int = 32) {
    val initials = name.take(2).uppercase()
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            color = Color.White,
            fontSize = (size / 2.5).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── VendorSelector ──────────────────────────────────────────────────────────

@Composable
fun VendorSelector(
    vendors: List<Vendor>,
    selected: Vendor,
    onSelect: (Vendor) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        vendors.forEach { vendor ->
            val available = vendor.isAvailable()
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == vendor,
                    onClick = { if (available) onSelect(vendor) },
                    enabled = available,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colors.primary
                    )
                )
                Column {
                    Text(
                        vendor.displayName,
                        color = if (available) DimText else DimText.copy(alpha = 0.3f)
                    )
                    if (!available) {
                        Text(
                            vendor.envVarName + " not set",
                            style = MaterialTheme.typography.overline,
                            color = ErrorColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// ── ModelDropdown ────────────────────────────────────────────────────────────

@Composable
fun ModelDropdown(
    models: List<ModelOption>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = models.find { it.id == selected }?.displayName ?: selected

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrightText)
        ) {
            Text(selectedLabel)
            Spacer(Modifier.width(4.dp))
            Text("▾", color = DimText)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { model ->
                DropdownMenuItem(onClick = {
                    onSelect(model.id)
                    expanded = false
                }) {
                    Text(model.displayName)
                }
            }
        }
    }
}

// ── UsagePane ───────────────────────────────────────────────────────────────

@Composable
fun UsagePane(tracker: UsageTracker, modifier: Modifier = Modifier) {
    val stats = tracker.stats()
    val totalCost = tracker.totalCost()
    val totalTokens = tracker.totalTokens()
    val totalRequests = tracker.totalRequests()

    Card(
        modifier = modifier.widthIn(min = 180.dp, max = 260.dp),
        backgroundColor = DarkCardAlt.copy(alpha = 0.95f),
        shape = RoundedCornerShape(10.dp),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Usage",
                style = MaterialTheme.typography.subtitle2,
                color = MaterialTheme.colors.primary
            )

            stats.values.forEach { model ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        model.model.take(20),
                        style = MaterialTheme.typography.overline,
                        color = BrightText
                    )
                    Text(
                        "${model.requestCount} req",
                        style = MaterialTheme.typography.overline,
                        color = DimText
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${formatTokens(model.totalInputTokens)}↑ " +
                            "${formatTokens(model.totalOutputTokens)}↓",
                        style = MaterialTheme.typography.overline,
                        color = DimText
                    )
                    if (model.totalCost > 0.0) {
                        Text(
                            "$${formatCost(model.totalCost)}",
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.secondary
                        )
                    }
                }
            }

            if (stats.isNotEmpty()) {
                Divider(color = DimText.copy(alpha = 0.2f), thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$totalRequests req · ${formatTokens(totalTokens)} tok",
                        style = MaterialTheme.typography.overline,
                        color = BrightText
                    )
                    if (totalCost > 0.0) {
                        Text(
                            "$${formatCost(totalCost)}",
                            style = MaterialTheme.typography.overline,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.secondary
                        )
                    }
                }
            }

            if (stats.isEmpty()) {
                Text(
                    "No usage yet",
                    style = MaterialTheme.typography.overline,
                    color = DimText
                )
            }
        }
    }
}

// ── Formatting Helpers ──────────────────────────────────────────────────────

fun formatTokens(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

fun formatCost(cost: Double): String = "%.4f".format(cost)
