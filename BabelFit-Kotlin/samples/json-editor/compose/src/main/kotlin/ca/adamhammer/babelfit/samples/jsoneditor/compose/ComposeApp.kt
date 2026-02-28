@file:Suppress("LongMethod", "TooManyFunctions")
package ca.adamhammer.babelfit.samples.jsoneditor.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.adamhammer.babelfit.samples.common.*
import kotlinx.serialization.json.*
import java.awt.Cursor

// ── DnD-free accent colours for the JSON editor ────────────────────────────

private val AgentColor = Color(0xFF5C6BC0)       // Indigo
private val ToolColor = Color(0xFF26A69A)         // Teal
private val UserColor = Color(0xFF7E57C2)         // Purple
private val SystemMsgColor = Color(0xFF90A4AE)    // Grey
private val KeyColor = Color(0xFF82AAFF)          // Soft blue for JSON keys
private val StringValColor = Color(0xFFC3E88D)    // Green for string values
private val NumberValColor = Color(0xFFFFCB6B)    // Amber for numbers
private val BoolValColor = Color(0xFFF78C6C)      // Salmon for booleans
private val NullValColor = Color(0xFF89DDFF)       // Cyan for null

private val EditorColors = babelFitDarkColors(
    primary = AgentColor,
    primaryVariant = UserColor,
    secondary = ToolColor
)

// ── Root composable ─────────────────────────────────────────────────────────

@Composable
fun JsonEditorApp(controller: ComposeEditorController) {
    MaterialTheme(colors = EditorColors) {
        Column(modifier = Modifier.fillMaxSize().background(DarkSurface)) {
            // Slim toolbar: vendor/model + view toggle
            Toolbar(controller)

            // Resizable two-panel body
            ResizableSplitPane(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                left = { ChatPanel(controller, Modifier.fillMaxSize()) },
                right = { ContentPanel(controller, Modifier.fillMaxSize()) }
            )

            // Status bar
            StatusBar(controller)
        }
    }
}

// ── Toolbar ─────────────────────────────────────────────────────────────────

@Composable
private fun Toolbar(controller: ComposeEditorController) {
    Surface(color = DarkCardColor, elevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact vendor dropdown
            CompactDropdown(
                items = Vendor.entries.filter { it.isAvailable() },
                selected = controller.vendor,
                label = { it.displayName },
                onSelect = { controller.selectVendor(it) }
            )

            // Model dropdown
            CompactDropdown(
                items = controller.vendor.models,
                selected = controller.vendor.models.find { it.id == controller.model }
                    ?: controller.vendor.models.first(),
                label = { it.displayName },
                onSelect = { controller.selectModel(it.id) }
            )

            Spacer(Modifier.weight(1f))

            // View mode toggle
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(
                    onClick = { if (controller.viewMode != ContentViewMode.TREE) controller.toggleViewMode() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (controller.viewMode == ContentViewMode.TREE) AgentColor else DimText
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 28.dp)
                ) { Text("Tree", fontSize = 11.sp) }

                TextButton(
                    onClick = { if (controller.viewMode != ContentViewMode.RAW) controller.toggleViewMode() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (controller.viewMode == ContentViewMode.RAW) AgentColor else DimText
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 28.dp)
                ) { Text("Raw", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun <T> CompactDropdown(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.defaultMinSize(minHeight = 28.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = BrightText)
        ) {
            Text(label(selected), fontSize = 11.sp)
            Text(" ▾", color = DimText, fontSize = 10.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(onClick = { onSelect(item); expanded = false }) {
                    Text(label(item), fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Resizable Split Pane ────────────────────────────────────────────────────

@Composable
private fun ResizableSplitPane(
    modifier: Modifier = Modifier,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit
) {
    var splitFraction by remember { mutableStateOf(0.55f) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val leftWidthDp = with(density) { (totalWidthPx * splitFraction).toDp() }
        val dividerWidth = 5.dp
        val rightWidthDp = with(density) {
            (totalWidthPx * (1f - splitFraction)).toDp()
        } - dividerWidth

        Row(Modifier.fillMaxSize()) {
            // Left pane
            Box(Modifier.width(leftWidthDp).fillMaxHeight()) { left() }

            // Draggable divider
            Box(
                modifier = Modifier
                    .width(dividerWidth)
                    .fillMaxHeight()
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            splitFraction = (splitFraction + dragAmount.x / totalWidthPx)
                                .coerceIn(0.2f, 0.8f)
                        }
                    }
            ) {
                // Visible 1dp line centered in the 5dp hit area
                Box(
                    Modifier.width(1.dp).fillMaxHeight()
                        .align(Alignment.Center)
                        .background(DimText.copy(alpha = 0.3f))
                )
            }

            // Right pane
            Box(Modifier.width(rightWidthDp).fillMaxHeight()) { right() }
        }
    }
}

// ── Status Bar ──────────────────────────────────────────────────────────────

@Composable
private fun StatusBar(controller: ComposeEditorController) {
    Surface(color = DarkCardAlt, elevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: file path
            Text(
                controller.filePath ?: "untitled.json",
                color = DimText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Center: vendor + model
            Text(
                "${controller.vendor.displayName} · ${controller.model}",
                color = DimText,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Right: usage stats or busy indicator
            if (controller.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = AgentColor,
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(6.dp))
                Text("Working…", color = AgentColor, fontSize = 11.sp)
            } else if (controller.usageTracker.totalRequests() > 0) {
                val total = controller.usageTracker
                Text(
                    "${total.totalRequests()} req · ${formatTokens(total.totalTokens())} tok" +
                        if (total.totalCost() > 0.0) " · $${formatCost(total.totalCost())}" else "",
                    color = DimText,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ── Chat Panel (left) ───────────────────────────────────────────────────────

@Composable
private fun ChatPanel(controller: ComposeEditorController, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(controller.chatEntries.size) {
        if (controller.chatEntries.isNotEmpty()) {
            listState.animateScrollToItem(controller.chatEntries.lastIndex)
        }
    }

    Column(modifier = modifier.background(DarkSurface)) {
        // Chat history
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(controller.chatEntries, key = { it.timestamp }) { entry ->
                ChatEntryCard(entry)
            }
        }

        // Input row
        ChatInput(controller)
    }
}

@Composable
private fun ChatEntryCard(entry: ChatEntry) {
    when (entry) {
        is ChatEntry.UserMessage -> ColorBorderCard(accentColor = UserColor) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarCircle("You", UserColor, size = 24)
                Text(entry.text, color = BrightText, style = MaterialTheme.typography.body2)
            }
        }

        is ChatEntry.AgentResponse -> ColorBorderCard(accentColor = AgentColor) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarCircle("AI", AgentColor, size = 24)
                Text(entry.text, color = BrightText, style = MaterialTheme.typography.body2)
            }
        }

        is ChatEntry.ToolCall -> ColorBorderCard(accentColor = ToolColor, alpha = 0.06f) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "⚙ ${entry.name}",
                    color = ToolColor,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold
                )
                if (entry.args.isNotBlank()) {
                    Text(
                        entry.args,
                        color = DimText,
                        style = MaterialTheme.typography.overline,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    entry.result.take(200),
                    color = DimText.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.overline,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        is ChatEntry.ErrorMessage -> ColorBorderCard(accentColor = ErrorColor) {
            Text("✗ ${entry.text}", color = ErrorColor, style = MaterialTheme.typography.body2)
        }

        is ChatEntry.SystemMessage -> ColorBorderCard(accentColor = SystemMsgColor, alpha = 0.04f) {
            Text(
                entry.text,
                color = SystemMsgColor,
                style = MaterialTheme.typography.caption,
                fontStyle = FontStyle.Italic
            )
        }

        is ChatEntry.DocumentLoaded -> ColorBorderCard(accentColor = SuccessColor, alpha = 0.06f) {
            Text(
                "📄 Loaded: ${entry.filePath}",
                color = SuccessColor,
                style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChatInput(controller: ComposeEditorController) {
    Surface(color = DarkCardColor, elevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = controller.inputText,
                onValueChange = { controller.inputText = it },
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter && event.type == KeyEventType.KeyDown &&
                        !event.isShiftPressed
                    ) {
                        controller.sendMessage()
                        true
                    } else false
                },
                placeholder = { Text("Ask the AI to edit your JSON…", color = DimText.copy(alpha = 0.5f)) },
                colors = darkTextFieldColors(),
                singleLine = true,
                enabled = !controller.isBusy
            )

            Button(
                onClick = { controller.sendMessage() },
                enabled = !controller.isBusy && controller.inputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AgentColor,
                    disabledBackgroundColor = AgentColor.copy(alpha = 0.3f)
                )
            ) {
                if (controller.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send", color = Color.White)
                }
            }
        }
    }
}

// ── Content Panel (right) ───────────────────────────────────────────────────

@Composable
private fun ContentPanel(controller: ComposeEditorController, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(DarkSurface)) {
        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            when (controller.viewMode) {
                ContentViewMode.TREE -> JsonTreeView(controller.document.root)
                ContentViewMode.RAW -> RawJsonView(controller.document.root)
            }
        }
    }
}

// ── Raw JSON View ───────────────────────────────────────────────────────────

@Composable
private fun RawJsonView(root: JsonElement) {
    val prettyJson = Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), root)
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxSize(),
        backgroundColor = DarkCardColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        SelectionContainer {
            Text(
                text = prettyJson,
                color = BrightText,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp).verticalScroll(scrollState)
            )
        }
    }
}

// ── JSON Tree View ──────────────────────────────────────────────────────────

@Composable
private fun JsonTreeView(root: JsonElement) {
    val scrollState = rememberScrollState()
    Card(
        modifier = Modifier.fillMaxSize(),
        backgroundColor = DarkCardColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).verticalScroll(scrollState)
        ) {
            JsonNode(key = null, element = root, depth = 0)
        }
    }
}

@Composable
private fun JsonNode(key: String?, element: JsonElement, depth: Int) {
    when (element) {
        is JsonObject -> JsonObjectNode(key, element, depth)
        is JsonArray -> JsonArrayNode(key, element, depth)
        is JsonPrimitive -> JsonPrimitiveNode(key, element, depth)
    }
}

@Composable
private fun JsonObjectNode(key: String?, obj: JsonObject, depth: Int) {
    var expanded by remember { mutableStateOf(depth < 2) }
    val indent = (depth * 16).dp

    Row(
        modifier = Modifier.padding(start = indent).fillMaxWidth()
            .clickable { expanded = !expanded }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (expanded) "▾ " else "▸ ",
            color = DimText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
        if (key != null) {
            Text("$key: ", color = KeyColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Text(
            "{${obj.size}}",
            color = DimText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }

    if (expanded) {
        obj.entries.forEach { (k, v) ->
            JsonNode(key = k, element = v, depth = depth + 1)
        }
    }
}

@Composable
private fun JsonArrayNode(key: String?, arr: JsonArray, depth: Int) {
    var expanded by remember { mutableStateOf(depth < 2) }
    val indent = (depth * 16).dp

    Row(
        modifier = Modifier.padding(start = indent).fillMaxWidth()
            .clickable { expanded = !expanded }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (expanded) "▾ " else "▸ ",
            color = DimText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
        if (key != null) {
            Text("$key: ", color = KeyColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Text(
            "[${arr.size}]",
            color = DimText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }

    if (expanded) {
        arr.forEachIndexed { index, v ->
            JsonNode(key = index.toString(), element = v, depth = depth + 1)
        }
    }
}

@Composable
private fun JsonPrimitiveNode(key: String?, prim: JsonPrimitive, depth: Int) {
    val indent = (depth * 16).dp
    val (display, color) = when {
        prim.isString -> "\"${prim.content}\"" to StringValColor
        prim.booleanOrNull != null -> prim.content to BoolValColor
        prim.content == "null" -> "null" to NullValColor
        else -> prim.content to NumberValColor
    }

    Row(modifier = Modifier.padding(start = indent, top = 1.dp, bottom = 1.dp)) {
        Text("  ", fontFamily = FontFamily.Monospace, fontSize = 13.sp) // alignment spacer
        if (key != null) {
            Text("$key: ", color = KeyColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Text(display, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}
