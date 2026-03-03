@file:Suppress("LongMethod", "MaxLineLength", "TooManyFunctions")
package ca.adamhammer.babelfit.samples.dnd.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.adamhammer.babelfit.UsageTracker
import ca.adamhammer.babelfit.samples.common.*
import org.jetbrains.skia.Image as SkiaImage
import java.util.Base64

// ── Color Palette ───────────────────────────────────────────────────────────

private val DmColor = Color(0xFF7E57C2)         // Purple for DM narration
private val SceneColor = Color(0xFF5C6BC0)       // Indigo for scene descriptions
private val SummaryColor = Color(0xFF26A69A)     // Teal for round summaries
private val WorldBuildColor = Color(0xFF78909C)  // Blue Grey for world building
private val DiceColor = Color(0xFFFFA726)        // Orange for dice rolls
private val WhisperColor = Color(0xFF8D6E63)     // Brown for whispers
private val SystemColor = Color(0xFF90A4AE)      // Light grey for system
private val FailColor = Color(0xFFEF5350)        // Red for failure
private val ThinkingColor = Color(0xFF546E7A)    // Dark grey for thinking
private val LevelUpColor = Color(0xFFFFD54F)     // Gold for level-ups

// ── Dark Theme ──────────────────────────────────────────────────────────────

private val DndDarkColors = babelFitDarkColors(
    primary = DmColor,
    primaryVariant = SceneColor,
    secondary = SummaryColor
)

// ── Main App Entry ──────────────────────────────────────────────────────────

@Composable
fun BabelFitDndAppV2() {
    val scope = rememberCoroutineScope()
    val controller = remember { ComposeGameControllerV2(scope) }

    MaterialTheme(colors = DndDarkColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            when (controller.screen) {
                AppScreen.SETUP -> SetupScreenV2(controller)
                AppScreen.PLAYING -> GameTimelineScreen(controller)
            }
        }
    }
}

// ── Setup Screen (cleaned up V1) ────────────────────────────────────────────

@Composable
private fun SetupScreenV2(controller: ComposeGameControllerV2) {
    val setup = controller.setupState
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("BabelFit D&D", style = MaterialTheme.typography.h4, color = DmColor)
        Text(
            "Build your party, set scope, and embark.",
            style = MaterialTheme.typography.body2, color = DimText
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { controller.randomizePrimer() },
                colors = ButtonDefaults.buttonColors(backgroundColor = DmColor)
            ) { Text("🎲 Randomize") }
            Button(
                onClick = { controller.randomizeMixed() },
                colors = ButtonDefaults.buttonColors(backgroundColor = DmColor)
            ) { Text("🎲 Mix Genres") }
        }

        // Campaign card
        DarkCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Campaign Setting", style = MaterialTheme.typography.h6, color = BrightText)
                    IconButton(onClick = { controller.randomizeSetting() }) {
                        Text("🎲", style = MaterialTheme.typography.body1)
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = setup.genre,
                    onValueChange = { controller.setupState = controller.setupState.copy(genre = it) },
                    label = { Text("Genre") },
                    colors = darkTextFieldColors()
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = setup.premise,
                    onValueChange = { controller.setupState = controller.setupState.copy(premise = it) },
                    label = { Text("Campaign Premise") },
                    minLines = 2,
                    colors = darkTextFieldColors()
                )
            }
        }

        // AI Configuration card
        DarkCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI Configuration", style = MaterialTheme.typography.h6, color = BrightText)

                Text("Text Generation", style = MaterialTheme.typography.subtitle2, color = DimText)
                VendorSelector(
                    vendors = Vendor.entries,
                    selected = setup.textVendor,
                    onSelect = { vendor ->
                        controller.setupState = controller.setupState.copy(
                            textVendor = vendor,
                            textModel = vendor.defaultModel,
                            imageVendor = if (!vendor.supportsImages && setup.imageVendor == vendor)
                                Vendor.entries.first { it.supportsImages && it.isAvailable() }
                            else setup.imageVendor
                        )
                    }
                )
                ModelDropdown(
                    models = setup.textVendor.models,
                    selected = setup.textModel,
                    onSelect = { controller.setupState = controller.setupState.copy(textModel = it) }
                )

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = setup.enableImages,
                        onCheckedChange = { controller.setupState = controller.setupState.copy(enableImages = it) },
                        colors = CheckboxDefaults.colors(checkedColor = DmColor)
                    )
                    Text("Enable Image Generation", color = DimText)
                }
                if (setup.enableImages) {
                    Text("Image Generation", style = MaterialTheme.typography.subtitle2, color = DimText)
                    VendorSelector(
                        vendors = Vendor.entries.filter { it.supportsImages },
                        selected = setup.imageVendor,
                        onSelect = { controller.setupState = controller.setupState.copy(imageVendor = it) }
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = setup.artStyle,
                        onValueChange = { controller.setupState = controller.setupState.copy(artStyle = it) },
                        label = { Text("Art Style") },
                        colors = darkTextFieldColors()
                    )
                }
            }
        }

        // Party config card
        DarkCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Party & Rounds", style = MaterialTheme.typography.h6, color = BrightText)
                Text("Party Size: ${setup.partySize}", color = DimText)
                Slider(
                    value = setup.partySize.toFloat(),
                    onValueChange = { controller.updatePartySize(it.toInt().coerceIn(1, 4)) },
                    valueRange = 1f..4f, steps = 2,
                    colors = SliderDefaults.colors(thumbColor = DmColor, activeTrackColor = DmColor)
                )
                Text("Rounds: ${setup.maxRounds}", color = DimText)
                Slider(
                    value = setup.maxRounds.toFloat(),
                    onValueChange = { controller.updateMaxRounds(it.toInt().coerceIn(3, 100)) },
                    valueRange = 3f..100f, steps = 96,
                    colors = SliderDefaults.colors(thumbColor = DmColor, activeTrackColor = DmColor)
                )

            }
        }

        // Character drafts
        setup.drafts.forEachIndexed { index, draft ->
            val avatarColor = AvatarColors.colorFor(draft.name.ifBlank { "Player ${index + 1}" })
            DarkCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AvatarCircle(draft.name.ifBlank { "P${index + 1}" }, avatarColor)
                        Text("Character ${index + 1}", style = MaterialTheme.typography.h6, color = BrightText)
                        IconButton(onClick = { controller.randomizeCharacter(index) }) {
                            Text("🎲", style = MaterialTheme.typography.body1)
                        }
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = draft.name,
                        onValueChange = { controller.updateDraft(index) { old -> old.copy(name = it) } },
                        label = { Text("Name (blank = AI)") },
                        colors = darkTextFieldColors()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = draft.race,
                            onValueChange = { controller.updateDraft(index) { old -> old.copy(race = it) } },
                            label = { Text("Race") },
                            colors = darkTextFieldColors()
                        )
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = draft.characterClass,
                            onValueChange = { controller.updateDraft(index) { old -> old.copy(characterClass = it) } },
                            label = { Text("Class") },
                            colors = darkTextFieldColors()
                        )
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = draft.backstory,
                        onValueChange = { controller.updateDraft(index) { old -> old.copy(backstory = it, manualBackstory = it.isNotBlank()) } },
                        label = { Text("Backstory (blank = AI)") },
                        minLines = 2,
                        colors = darkTextFieldColors()
                    )
                }
            }
        }

        controller.errorMessage?.let {
            Text(it, color = ErrorColor)
        }

        Button(
            onClick = { controller.startGame() },
            enabled = !controller.isBusy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = DmColor)
        ) {
            if (controller.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White, strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Starting...", color = Color.White)
            } else {
                Text("⚔ Start Adventure", color = Color.White)
            }
        }
    }
}

// ── Game Timeline Screen ────────────────────────────────────────────────────

@Composable
private fun GameTimelineScreen(controller: ComposeGameControllerV2) {
    val timelineState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(controller.timelineEntries.size, controller.gameFinished) {
        if (controller.timelineEntries.isNotEmpty()) {
            timelineState.animateScrollToItem(controller.timelineEntries.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StickyHeaderBar(controller)

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = timelineState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(controller.timelineEntries, key = { it.timestamp }) { entry ->
                    TimelineEntryCard(entry, controller)
                }

                if (controller.gameFinished) {
                    item { FinBanner(onBackToSetup = { controller.backToSetup() }) }
                }
            }

            if (controller.showUsagePane) {
                UsagePane(
                    tracker = controller.usageTracker,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StickyHeaderBar(controller: ComposeGameControllerV2) {
    Surface(
        color = DarkCardAlt,
        elevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Round ${controller.currentRound}",
                    style = MaterialTheme.typography.h6, color = DmColor
                )
                Text("•", color = DimText)
                Text(controller.world.location.name, style = MaterialTheme.typography.subtitle1, color = BrightText)

                Spacer(Modifier.weight(1f))

                // Export trace
                TextButton(
                    onClick = { controller.exportTrace() },
                    colors = ButtonDefaults.textButtonColors(contentColor = DimText)
                ) {
                    Text("Export Trace", fontSize = 14.sp)
                }

                // Usage pane toggle
                TextButton(
                    onClick = { controller.showUsagePane = !controller.showUsagePane },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (controller.showUsagePane) DmColor else DimText
                    )
                ) {
                    Text("💰", fontSize = 14.sp)
                }

                if (controller.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DmColor)
                    Spacer(Modifier.width(6.dp))
                    controller.activeTurnCharacterName?.let {
                        Text("$it thinking...", style = MaterialTheme.typography.caption, color = DimText)
                    }
                }
            }

            // Compact party HP row
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                controller.world.party.forEach { c ->
                    val color = AvatarColors.colorFor(c.name)
                    val hpRatio = if (c.maxHp == 0) 0f else c.hp.toFloat() / c.maxHp
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape).background(color)
                        )
                        Text(
                            "${c.name} Lv${c.level} ${c.hp}/${c.maxHp}",
                            style = MaterialTheme.typography.caption,
                            color = when {
                                hpRatio > 0.5f -> SuccessColor
                                hpRatio > 0.2f -> DiceColor
                                else -> FailColor
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinBanner(onBackToSetup: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Divider(color = DmColor.copy(alpha = 0.4f))
        Text(
            "— Fin —",
            style = MaterialTheme.typography.h5,
            color = DmColor,
            fontStyle = FontStyle.Italic
        )
        Divider(color = DmColor.copy(alpha = 0.4f))
        Button(
            onClick = onBackToSetup,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = DmColor)
        ) { Text("← Back to Setup", color = Color.White) }
    }
}

// ── Timeline Entry Cards ────────────────────────────────────────────────────

@Composable
private fun TimelineEntryCard(entry: TimelineEntry, controller: ComposeGameControllerV2) {
    when (entry) {
        is TimelineEntry.RoundHeader -> RoundHeaderCard(entry)
        is TimelineEntry.DmNarration -> DmNarrationCard(entry)
        is TimelineEntry.SceneImage -> SceneImageCard(entry)
        is TimelineEntry.CharacterAction -> CharacterActionCard(entry)
        is TimelineEntry.CharacterThinking -> CharacterThinkingCard(entry)
        is TimelineEntry.DiceRoll -> DiceRollCard(entry)
        is TimelineEntry.Whisper -> WhisperCard(entry)
        is TimelineEntry.SystemMessage -> SystemMessageCard(entry)
        is TimelineEntry.ImagePromptPreview -> ImagePromptPreviewCard(entry, controller)
        is TimelineEntry.LevelUp -> LevelUpCard(entry)
    }
}

@Composable
private fun RoundHeaderCard(entry: TimelineEntry.RoundHeader) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Divider(modifier = Modifier.weight(1f), color = DmColor.copy(alpha = 0.4f))
        Surface(
            color = DmColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = "⚔ Round ${entry.round} — ${entry.locationName}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.subtitle2,
                color = DmColor
            )
        }
        Divider(modifier = Modifier.weight(1f), color = DmColor.copy(alpha = 0.4f))
    }
}

@Composable
private fun DmNarrationCard(entry: TimelineEntry.DmNarration) {
    val accentColor = when (entry.category) {
        TimelineEntry.DmCategory.SCENE -> SceneColor
        TimelineEntry.DmCategory.SUMMARY -> SummaryColor
        TimelineEntry.DmCategory.WORLD_BUILD -> WorldBuildColor
        TimelineEntry.DmCategory.WORLD_EVENT -> DmColor
    }
    val emoji = when (entry.category) {
        TimelineEntry.DmCategory.SCENE -> "🗺️"
        TimelineEntry.DmCategory.SUMMARY -> "📜"
        TimelineEntry.DmCategory.WORLD_BUILD -> "⚙️"
        TimelineEntry.DmCategory.WORLD_EVENT -> "🌍"
    }

    ColorBorderCard(accentColor) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "$emoji ${entry.title}",
                style = MaterialTheme.typography.subtitle2,
                color = accentColor
            )
            Text(
                entry.narrative,
                style = MaterialTheme.typography.body2,
                color = BrightText
            )
        }
    }
}

@Composable
private fun SceneImageCard(entry: TimelineEntry.SceneImage) {
    val bitmap = remember(entry.base64) { decodeBase64(entry.base64) }
    if (bitmap != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp,
            backgroundColor = DarkCardColor
        ) {
            Column {
                Image(
                    bitmap = bitmap,
                    contentDescription = entry.caption.ifBlank { "Scene illustration" },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
                if (entry.caption.isNotBlank()) {
                    Text(
                        entry.caption,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.caption,
                        color = DimText
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterActionCard(entry: TimelineEntry.CharacterAction) {
    val color = AvatarColors.colorFor(entry.characterName)
    val outcomeColor = when (entry.success) {
        true -> SuccessColor
        false -> FailColor
        null -> DimText
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Avatar
        AvatarCircle(entry.characterName, color, size = 36)

        // Chat bubble
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.characterName,
                style = MaterialTheme.typography.subtitle2,
                color = color
            )
            Surface(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        entry.action,
                        style = MaterialTheme.typography.body2,
                        color = BrightText
                    )
                    if (entry.outcome.isNotBlank()) {
                        Divider(color = color.copy(alpha = 0.2f))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
                            val icon = when (entry.success) {
                                true -> "✓"
                                false -> "✗"
                                null -> "→"
                            }
                            Text(icon, color = outcomeColor, fontSize = 12.sp)
                            Text(
                                entry.outcome,
                                style = MaterialTheme.typography.body2,
                                color = outcomeColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterThinkingCard(entry: TimelineEntry.CharacterThinking) {
    val color = AvatarColors.colorFor(entry.characterName)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // small dot instead of full avatar
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = 0.5f))
                .align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {}

        Text(
            text = "${entry.characterName} → ${entry.methodName}: ${entry.details}",
            style = MaterialTheme.typography.caption,
            color = ThinkingColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DiceRollCard(entry: TimelineEntry.DiceRoll) {
    val hasResult = entry.rollValue != null
    val resultColor = when (entry.success) {
        true -> SuccessColor
        false -> FailColor
        null -> DiceColor
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(10.dp))
        Surface(
            color = DiceColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎲", fontSize = 16.sp)
                if (hasResult) {
                    val nat20 = entry.rollValue == 20
                    val nat1 = entry.rollValue == 1
                    val flair = when {
                        nat20 -> " ✨NAT 20!✨"
                        nat1 -> " 💥NAT 1!"
                        else -> ""
                    }
                    val successLabel = when (entry.success) {
                        true -> "SUCCESS"
                        false -> "FAIL"
                        null -> "?"
                    }
                    Text(
                        "${entry.characterName} — ${entry.rollType}: " +
                            "d20(${entry.rollValue}) +${entry.modifier} = ${entry.total} " +
                            "vs DC ${entry.difficulty} → $successLabel$flair",
                        style = MaterialTheme.typography.caption,
                        color = resultColor,
                        fontWeight = if (nat20 || nat1) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        "${entry.characterName} — ${entry.rollType} vs DC ${entry.difficulty}",
                        style = MaterialTheme.typography.caption,
                        color = DiceColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WhisperCard(entry: TimelineEntry.Whisper) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.width(10.dp))
        Surface(
            color = WhisperColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🤫", fontSize = 14.sp)
                Text(
                    "${entry.from} whispers to ${entry.to}: \"${entry.message}\"",
                    style = MaterialTheme.typography.caption,
                    fontStyle = FontStyle.Italic,
                    color = WhisperColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SystemMessageCard(entry: TimelineEntry.SystemMessage) {
    val color = if (entry.isError) ErrorColor else SystemColor

    ColorBorderCard(color, alpha = 0.06f) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                entry.details,
                style = MaterialTheme.typography.caption,
                color = if (entry.isError) ErrorColor.copy(alpha = 0.8f) else DimText
            )
        }
    }
}

@Composable
private fun ImagePromptPreviewCard(entry: TimelineEntry.ImagePromptPreview, controller: ComposeGameControllerV2) {
    ColorBorderCard(DmColor, alpha = 0.08f) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "\uD83C\uDFA8 Image Prompt (${entry.imageType})",
                style = MaterialTheme.typography.subtitle2,
                color = DmColor
            )
            Text(
                entry.prompt,
                style = MaterialTheme.typography.caption,
                fontStyle = FontStyle.Italic,
                color = DimText
            )
            when {
                entry.imageBase64 != null -> {
                    val bitmap = remember(entry.imageBase64) { decodeBase64(entry.imageBase64) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = entry.prompt,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                entry.error != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "\u26A0\uFE0F Image generation failed",
                            style = MaterialTheme.typography.subtitle2,
                            color = FailColor
                        )
                        Text(
                            entry.error,
                            style = MaterialTheme.typography.caption,
                            color = FailColor.copy(alpha = 0.8f)
                        )
                        Button(
                            onClick = { controller.generateImage(entry.id) },
                            colors = ButtonDefaults.buttonColors(backgroundColor = FailColor)
                        ) {
                            Text("\uD83D\uDD04 Retry", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
                entry.generating -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp,
                                color = DmColor
                            )
                            Text("Generating...", style = MaterialTheme.typography.caption, color = DimText)
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { controller.generateImage(entry.id) },
                            colors = ButtonDefaults.buttonColors(backgroundColor = DmColor)
                        ) {
                            Text("\uD83C\uDFA8 Generate Image", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelUpCard(entry: TimelineEntry.LevelUp) {
    ColorBorderCard(LevelUpColor, alpha = 0.12f) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("\u2B06\uFE0F", fontSize = 20.sp)
            Column {
                Text(
                    "${entry.characterName} leveled up!",
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Bold,
                    color = LevelUpColor
                )
                Text(
                    "Now Level ${entry.newLevel}",
                    style = MaterialTheme.typography.caption,
                    color = LevelUpColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ── Utilities ────────────────────────────────────────────────────────────────

private fun decodeBase64(base64Data: String): ImageBitmap? {
    return try {
        val bytes = Base64.getDecoder().decode(base64Data)
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}
