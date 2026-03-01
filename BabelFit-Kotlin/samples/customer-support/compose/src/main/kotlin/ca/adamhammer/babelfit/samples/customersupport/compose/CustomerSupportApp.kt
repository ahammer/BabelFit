package ca.adamhammer.babelfit.samples.customersupport.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.adamhammer.babelfit.samples.common.*
import ca.adamhammer.babelfit.samples.customersupport.models.AgentType

private val AgentColors = mapOf(
    AgentType.ROUTING to Color(0xFF42A5F5),
    AgentType.TECHNICAL to Color(0xFF66BB6A),
    AgentType.BILLING to Color(0xFFFFA726),
    AgentType.GENERAL to Color(0xFF78909C),
    AgentType.ESCALATION to Color(0xFFEF5350)
)

@Composable
fun CustomerSupportApp() {
    val scope = rememberCoroutineScope()
    val controller = remember { ComposeSessionController(scope) }
    var started by remember { mutableStateOf(false) }

    MaterialTheme(colors = babelFitDarkColors()) {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkSurface) {
            if (!started) {
                SetupScreen(controller) {
                    controller.startSession()
                    started = true
                }
            } else {
                ChatScreen(controller)
            }
        }
    }
}

@Composable
private fun SetupScreen(controller: ComposeSessionController, onStart: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.widthIn(max = 500.dp),
            backgroundColor = DarkCardColor,
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("BabelFit Customer Support", style = MaterialTheme.typography.h5, color = BrightText)
                Text("Agentic support demo for WidgetCo", color = DimText)

                Divider(color = DimText.copy(alpha = 0.2f))

                Text("Customer", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow("Name", controller.customerContext.name)
                    InfoRow("Account", controller.customerContext.accountId)
                    InfoRow("Product", "Widget ${controller.customerContext.productModel}")
                    InfoRow("Serial", controller.customerContext.serialNumber)
                }

                Divider(color = DimText.copy(alpha = 0.2f))

                Text("AI Provider", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
                VendorSelector(
                    vendors = Vendor.entries,
                    selected = controller.vendor,
                    onSelect = {
                        controller.vendor = it
                        controller.model = it.defaultModel
                    }
                )
                ModelDropdown(
                    models = controller.vendor.models,
                    selected = controller.model,
                    onSelect = { controller.model = it }
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                ) {
                    Text("Start Support Session")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text("$label: ", color = DimText, fontSize = 13.sp)
        Text(value, color = BrightText, fontSize = 13.sp)
    }
}

@Composable
private fun ChatScreen(controller: ComposeSessionController) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar
        Sidebar(controller, modifier = Modifier.width(260.dp).fillMaxHeight())

        // Main chat area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ChatMessages(controller, modifier = Modifier.weight(1f))
            ChatInput(controller)
        }
    }
}

@Composable
private fun Sidebar(controller: ComposeSessionController, modifier: Modifier) {
    Surface(modifier = modifier, color = DarkCardAlt) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Customer Support", style = MaterialTheme.typography.h6, color = BrightText, fontSize = 16.sp)

            DarkCard {
                Text("Customer", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
                Spacer(Modifier.height(6.dp))
                Text(controller.customerContext.name, color = BrightText, fontSize = 13.sp)
                Text(controller.customerContext.accountId, color = DimText, fontSize = 12.sp)
                Text("Widget ${controller.customerContext.productModel}", color = DimText, fontSize = 12.sp)
            }

            DarkCard {
                Text("Active Agent", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
                Spacer(Modifier.height(6.dp))
                AgentBadge(controller.currentAgent)
            }

            DarkCard {
                Text("Agents", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
                Spacer(Modifier.height(6.dp))
                AgentType.entries.forEach { agent ->
                    val isActive = controller.currentAgent == agent
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        AvatarCircle(
                            agent.displayName,
                            AgentColors[agent] ?: Color.Gray,
                            size = 24
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            agent.displayName,
                            color = if (isActive) BrightText else DimText,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentBadge(agentType: AgentType) {
    val color = AgentColors[agentType] ?: Color.Gray
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AvatarCircle(agentType.displayName, color, size = 28)
            Text(agentType.displayName, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ChatMessages(controller: ComposeSessionController, modifier: Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(controller.chatEntries.size) {
        if (controller.chatEntries.isNotEmpty()) {
            listState.animateScrollToItem(controller.chatEntries.lastIndex)
        }
    }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(controller.chatEntries) { entry ->
            when (entry) {
                is ChatEntry.UserMessage -> UserBubble(entry.text)
                is ChatEntry.AgentMessage -> AgentBubble(entry)
                is ChatEntry.TransferNotice -> TransferCard(entry)
                is ChatEntry.ToolCall -> ToolCallCard(entry)
                is ChatEntry.EscalationNotice -> EscalationCard(entry.ticket)
                is ChatEntry.ErrorMessage -> ErrorCard(entry.error)
            }
        }

        if (controller.isProcessing) {
            item {
                Text("Thinking...", color = DimText, fontStyle = FontStyle.Italic, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colors.primary.copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
        ) {
            Text(
                text,
                modifier = Modifier.padding(12.dp).widthIn(max = 500.dp),
                color = BrightText,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun AgentBubble(entry: ChatEntry.AgentMessage) {
    val color = AgentColors[entry.agentType] ?: Color.Gray
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        AvatarCircle(entry.agentType.displayName, color, size = 32)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.widthIn(max = 550.dp)) {
            Text(entry.agentType.displayName, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Surface(
                color = DarkCardColor,
                shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(entry.response.message, color = BrightText, fontSize = 14.sp)
                    if (entry.response.suggestedActions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        entry.response.suggestedActions.forEach { action ->
                            Text("• $action", color = DimText, fontSize = 12.sp)
                        }
                    }
                    if (entry.response.resolved) {
                        Spacer(Modifier.height(4.dp))
                        Text("Issue resolved", color = SuccessColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferCard(entry: ChatEntry.TransferNotice) {
    val toColor = AgentColors[entry.to] ?: Color.Gray
    ColorBorderCard(accentColor = toColor, alpha = 0.12f) {
        Column {
            Text(
                "Transferred: ${entry.from.displayName} → ${entry.to.displayName}",
                color = toColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            val shortSummary = if (entry.summary.length > 120) entry.summary.take(120) + "..." else entry.summary
            Text(shortSummary, color = DimText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ToolCallCard(entry: ChatEntry.ToolCall) {
    val color = AgentColors[entry.agentType] ?: Color.Gray
    Surface(
        color = color.copy(alpha = 0.06f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚙ ", color = color, fontSize = 13.sp)
            Text(entry.toolName, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            val shortResult = if (entry.result.length > 60) entry.result.take(60) + "..." else entry.result
            Text(shortResult, color = DimText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EscalationCard(ticket: String) {
    ColorBorderCard(accentColor = Color(0xFFEF5350), alpha = 0.15f) {
        Text("Escalation ticket created: $ticket", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ErrorCard(error: String) {
    ColorBorderCard(accentColor = ErrorColor, alpha = 0.12f) {
        Text(error, color = ErrorColor, fontSize = 13.sp)
    }
}

@Composable
private fun ChatInput(controller: ComposeSessionController) {
    var text by remember { mutableStateOf("") }

    Surface(color = DarkCardAlt, elevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                        controller.sendMessage(text.trim())
                        text = ""
                        true
                    } else false
                },
                placeholder = { Text("Type your support request...", color = DimText.copy(alpha = 0.5f)) },
                colors = darkTextFieldColors(),
                singleLine = true,
                enabled = !controller.isProcessing
            )
            Button(
                onClick = {
                    controller.sendMessage(text.trim())
                    text = ""
                },
                enabled = text.isNotBlank() && !controller.isProcessing,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
            ) {
                Text("Send")
            }
        }
    }
}
