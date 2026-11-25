package com.emotionalchess.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Kind
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Move
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side

@Composable
fun App(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().background(BackdropBrush)) {
        Crossfade(targetState = vm.game == null, animationSpec = tween(400), label = "screen") { setup ->
            val game = vm.game
            if (setup || game == null) NewGameScreen(onStart = vm::newGame) else GameScreen(vm, game)
        }
    }
}

// ======================================================================= new game

@Composable
fun NewGameScreen(onStart: (Mode, String, String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(Mode.EMOTIONAL) }
    var white by rememberSaveable { mutableStateOf("") }
    var black by rememberSaveable { mutableStateOf("") }
    var showRules by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            HeroPieces()
            Text("Emotional Chess", style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
            Text(
                "Remorse chess for a real board and a real friend.\nEvery capture leaves a mark.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            SectionLabel("Variant")
            VariantCard(
                emoji = "🎲", title = Mode.EMOTIONAL.title, selected = mode == Mode.EMOTIONAL,
                text = "A piece's first capture rolls a die: devastated, guilt-ridden, unaffected… or psychopath. Bishops absolve the afflicted.",
            ) { mode = Mode.EMOTIONAL }
            VariantCard(
                emoji = "✝️", title = Mode.CLASSIC.title, selected = mode == Mode.CLASSIC,
                text = "Every capture makes the piece remorseful. It can't capture or give check until a friendly bishop comes to forgive it.",
            ) { mode = Mode.CLASSIC }
            SectionLabel("Players")
            PlayerField(white, { white = it }, Side.WHITE, ImeAction.Next)
            PlayerField(black, { black = it }, Side.BLACK, ImeAction.Done)
            Spacer(Modifier.height(6.dp))
            GlowButton("Begin the game") { onStart(mode, white.trim(), black.trim()) }
            TextButton(onClick = { showRules = true }) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null)
                Spacer(Modifier.width(8.dp))
                Text("How to play")
            }
        }
    }
    if (showRules) RulesSheet(mode) { showRules = false }
}

/** A little board fragment with three pieces wearing their feelings. */
@Composable
private fun HeroPieces() {
    Row(Modifier.clip(RoundedCornerShape(16.dp))) {
        HeroTile(Kind.KNIGHT, Side.BLACK, Mood.PSYCHOPATH, light = true)
        HeroTile(Kind.QUEEN, Side.WHITE, Mood.DEVASTATED, light = false)
        HeroTile(Kind.BISHOP, Side.BLACK, Mood.GUILTY, light = true)
    }
}

@Composable
private fun HeroTile(kind: Kind, side: Side, mood: Mood, light: Boolean) {
    Box(Modifier.size(76.dp).background(if (light) LightSquare else DarkSquare), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(64.dp)
                .background(
                    Brush.radialGradient(listOf(moodColor(mood)!!.copy(alpha = 0.85f), Color.Transparent)),
                    CircleShape,
                )
        )
        PieceImage(kind, side, Modifier.size(62.dp))
        Text(
            moodEmoji(mood, Mode.EMOTIONAL), fontSize = 20.sp, lineHeight = 22.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun VariantCard(emoji: String, title: String, text: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) scheme.primary else scheme.outlineVariant, label = "border")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun PlayerField(value: String, onChange: (String) -> Unit, side: Side, ime: ImeAction) {
    OutlinedTextField(
        value, onChange,
        label = { Text("${side.label} player") },
        placeholder = { Text(side.label) },
        leadingIcon = { SideDot(side, 18.dp) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ime),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GlowButton(text: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(scheme.primary, Color(0xFFB57BFF))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = scheme.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SideDot(side: Side, size: Dp) {
    Box(
        Modifier
            .size(size)
            .background(if (side == Side.WHITE) Color(0xFFF7F1E8) else Color(0xFF0B0810), CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

// ======================================================================= game

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel, game: GameState) {
    var menu by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }

    val view = LocalView.current
    LaunchedEffect(vm.feedback) {
        val f = vm.feedback ?: return@LaunchedEffect
        view.performHapticFeedback(
            when (f.kind) {
                FeedbackKind.MOVE -> HapticFeedbackConstants.CLOCK_TICK
                FeedbackKind.CAPTURE -> HapticFeedbackConstants.VIRTUAL_KEY
                FeedbackKind.CHECK -> HapticFeedbackConstants.LONG_PRESS
                FeedbackKind.GAME_OVER ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Text(
                        if (game.mode == Mode.EMOTIONAL) "🎲 Emotional" else "✝️ Classic",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                    IconButton(onClick = vm::flip) { Icon(Icons.Default.SwapVert, "Flip board") }
                    IconButton(onClick = { showRules = true }) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Rules") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Resign / draw…") },
                                leadingIcon = { Icon(Icons.Default.Flag, null) },
                                enabled = game.result == null,
                                onClick = { menu = false; confirmResign = true },
                            )
                            DropdownMenuItem(
                                text = { Text("New game") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = { menu = false; confirmNew = true },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
            val top = if (vm.flipped) Side.WHITE else Side.BLACK
            val board = @Composable { m: Modifier ->
                ChessBoard(game, vm.selected, vm.targets, vm.flipped, vm::onSquareTapped, m)
            }
            // Wide or near-square windows (unfolded Pixel Fold, tablets, landscape): side by side.
            if (maxWidth >= 600.dp && maxWidth >= maxHeight * 0.9f) {
                val boardSize = minOf(maxHeight - 32.dp, maxWidth * 0.6f)
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    board(Modifier.size(boardSize))
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerBar(game, top)
                        StatusBanner(game)
                        PieceCard(vm, game)
                        MoveLog(game, Modifier.weight(1f))
                        PlayerBar(game, top.other)
                    }
                }
            } else {
                val boardMax = maxHeight * 0.58f
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp).padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerBar(game, top, Modifier.widthIn(max = boardMax))
                    board(Modifier.widthIn(max = boardMax).fillMaxWidth())
                    PlayerBar(game, top.other, Modifier.widthIn(max = boardMax))
                    StatusBanner(game)
                    PieceCard(vm, game)
                    MoveLog(game, Modifier.weight(1f))
                }
            }
        }
    }

    // ---------------- dialogs
    if (game.pendingRoll != null || vm.rollReveal != null) DiceDialog(game, vm.rollReveal, vm::roll, vm::dismissRoll)
    vm.promotionChoices?.let { PromotionDialog(it, game.toMove, vm::choosePromotion) }
    if (game.result != null && vm.showResult && vm.rollReveal == null) {
        Dialog(onDismissRequest = vm::dismissResult) {
            ReportCard(game, onNewGame = vm::closeGame, onUndo = vm::undo, onViewBoard = vm::dismissResult)
        }
    }
    if (confirmNew) AlertDialog(
        onDismissRequest = { confirmNew = false },
        title = { Text("Start a new game?") },
        text = { Text("The current game will be discarded.") },
        confirmButton = { Button(onClick = { confirmNew = false; vm.closeGame() }) { Text("New game") } },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancel") } },
    )
    if (confirmResign) AlertDialog(
        onDismissRequest = { confirmResign = false },
        title = { Text("End the game") },
        text = { Text("Record a resignation or an agreed draw.") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { confirmResign = false; vm.resign(Side.WHITE) }) { Text("${game.whiteName} (White) resigns") }
                TextButton(onClick = { confirmResign = false; vm.resign(Side.BLACK) }) { Text("${game.blackName} (Black) resigns") }
                TextButton(onClick = { confirmResign = false; vm.agreeDraw() }) { Text("Agree a draw") }
                TextButton(onClick = { confirmResign = false }) { Text("Cancel") }
            }
        },
    )
    if (showRules) RulesSheet(game.mode) { showRules = false }
}

private fun value(kind: Kind) = when (kind) {
    Kind.PAWN -> 1
    Kind.KNIGHT, Kind.BISHOP -> 3
    Kind.ROOK -> 5
    Kind.QUEEN -> 9
    Kind.KING -> 0
}

@Composable
private fun PlayerBar(game: GameState, side: Side, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val active = game.result == null && game.toMove == side
    val bg by animateColorAsState(if (active) scheme.primary.copy(alpha = 0.16f) else Color.Transparent, label = "bg")
    val edge by animateColorAsState(if (active) scheme.primary.copy(alpha = 0.75f) else Color.Transparent, label = "edge")
    val material = game.board.filterNotNull().sumOf { if (it.side == side) value(it.kind) else -value(it.kind) }
    val taken = game.fallen.filter { it.side == side.other }.sortedByDescending { value(it.kind) }

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, edge, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideDot(side, 14.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            game.playerName(side) + if (game.result?.winner == side) "  👑" else "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        for (mood in listOf(Mood.PSYCHOPATH, Mood.GUILTY, Mood.DEVASTATED)) {
            val n = game.board.count { it?.side == side && it.mood == mood }
            if (n > 0) {
                Spacer(Modifier.width(6.dp))
                Text("${moodEmoji(mood, game.mode)}$n", style = MaterialTheme.typography.labelLarge, color = moodColor(mood)!!)
            }
        }
        Spacer(Modifier.weight(1f))
        if (taken.isNotEmpty()) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(LightSquare.copy(alpha = 0.92f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy((-8).dp),
            ) {
                for (f in taken) {
                    PieceImage(f.kind, f.side, Modifier.size(22.dp), tint = if (f.despair) Color(0xFF8FC4FF) else null)
                }
            }
        }
        if (material > 0) {
            Spacer(Modifier.width(6.dp))
            Text("+$material", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
    }
}

private data class Status(val emoji: String, val title: String, val detail: String, val tone: Color)

@Composable
private fun StatusBanner(game: GameState) {
    val scheme = MaterialTheme.colorScheme
    val name = game.playerName(game.toMove)
    val result = game.result
    val status = when {
        result != null -> Status(
            if (result.winner != null) "👑" else "🤝",
            result.winner?.let { "${game.playerName(it)} wins" } ?: "Draw",
            result.reason, scheme.tertiary,
        )
        game.pendingRoll != null -> Status(
            "🎲", "Roll the die",
            "${game.board[game.pendingRoll]?.name} made its first capture.", scheme.tertiary,
        )
        game.bonusSquare != null -> Status(
            "😈", "$name strikes again",
            "The psychopath on ${Rules.squareName(game.bonusSquare)} must move again.", moodColor(Mood.PSYCHOPATH)!!,
        )
        Rules.inCheck(game) -> Status("⚔️", "Check!", "$name must protect the king.", scheme.error)
        else -> {
            val absolve = Rules.legalAbsolutions(game)
            Status(
                if (game.toMove == Side.WHITE) "⚪" else "⚫",
                "$name to move",
                if (absolve.isNotEmpty()) {
                    "A bishop can absolve " + absolve.joinToString { "${game.board[it]!!.kind.label} ${Rules.squareName(it)}" } + " – tap it."
                } else if (game.log.isEmpty()) {
                    "Play on the real board, then tap the piece and its square here."
                } else {
                    "Move ${game.fullmove}"
                },
                scheme.secondary,
            )
        }
    }
    AnimatedContent(
        targetState = status,
        contentKey = { it.emoji + it.title },
        transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 4 }) togetherWith fadeOut(tween(150)) },
        label = "status",
    ) { s ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(s.tone.copy(alpha = 0.3f), s.tone.copy(alpha = 0.06f))))
                .border(1.dp, s.tone.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(s.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.detail.isNotEmpty()) {
                    Text(s.detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieceCard(vm: GameViewModel, game: GameState) {
    val scheme = MaterialTheme.colorScheme
    val sq = vm.selected
    val p = sq?.let { game.board[it] }
    if (sq == null || p == null) {
        // Nothing selected: a compact legend of the feelings in play.
        val moods = if (game.mode == Mode.EMOTIONAL) listOf(Mood.UNAFFECTED, Mood.GUILTY, Mood.DEVASTATED, Mood.PSYCHOPATH) else listOf(Mood.GUILTY)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (m in moods) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(moodColor(m)!!.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(moodEmoji(m, game.mode), fontSize = 14.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(Rules.moodLabel(m, game.mode), style = MaterialTheme.typography.labelMedium, color = moodColor(m)!!)
                }
            }
        }
        return
    }
    val accent = moodColor(p.mood) ?: scheme.outline
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainer)
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val light = (Rules.file(sq) + Rules.rank(sq)) % 2 == 1
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (light) LightSquare else DarkSquare),
            contentAlignment = Alignment.Center,
        ) { PieceImage(p.kind, p.side, Modifier.size(46.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${p.name} · ${Rules.squareName(sq)}", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            val extra = if (p.mood == Mood.DEVASTATED) " · leaves in ${p.countdown}" else ""
            Text(
                "${moodEmoji(p.mood, game.mode)} ${Rules.moodLabel(p.mood, game.mode)}$extra",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = moodColor(p.mood) ?: scheme.onSurface,
            )
            Text(Rules.moodDescription(p.mood, game.mode), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (Rules.absolverFor(game, sq) != null) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = vm::absolveSelected,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.tertiary, contentColor = scheme.onTertiary),
            ) { Text("✝ Absolve") }
        }
    }
}

@Composable
private fun MoveLog(game: GameState, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val state = rememberLazyListState()
    LaunchedEffect(game.log.size) { if (game.log.isNotEmpty()) state.animateScrollToItem(game.log.size - 1) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerLow.copy(alpha = 0.8f))
            .padding(start = 14.dp, end = 14.dp, top = 10.dp),
    ) {
        Text("THE STORY SO FAR", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, color = scheme.secondary)
        if (game.log.isEmpty()) {
            Text(
                "Nothing has happened yet. Nobody has hurt anybody.",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 10.dp)) {
            itemsIndexed(game.log) { _, line ->
                if (line.startsWith(" ")) {
                    Text(
                        line.trim(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, bottom = 2.dp),
                    )
                } else {
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

// ======================================================================= dialogs

@Composable
private fun PromotionDialog(moves: List<Move>, side: Side, onChoose: (Move?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoose(null) },
        title = { Text("Promote to") },
        text = {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                for (m in moves) {
                    PieceTile(m.promotion ?: Kind.QUEEN, side, 60.dp, Modifier.clickable { onChoose(m) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(null) }) { Text("Cancel") } },
    )
}

/** A piece on a light square, so black pieces stay visible on the dark theme. */
@Composable
fun PieceTile(kind: Kind, side: Side, size: Dp, modifier: Modifier = Modifier) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
            .background(LightSquare)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) { PieceImage(kind, side, Modifier.size(size * 0.88f)) }
}

/** End of game: the result plus how everybody felt along the way. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportCard(game: GameState, onNewGame: () -> Unit, onUndo: () -> Unit, onViewBoard: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val result = game.result ?: return
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
        Column(
            Modifier
                .background(Brush.radialGradient(listOf(scheme.tertiary.copy(alpha = 0.22f), Color.Transparent), radius = 800f))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (result.winner != null) "👑" else "🤝", fontSize = 48.sp, lineHeight = 56.sp)
            Text(
                result.winner?.let { "${game.playerName(it)} wins" } ?: "Draw",
                style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center,
            )
            Text(result.reason, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Text("THE EMOTIONAL REPORT", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, color = scheme.secondary)
            Spacer(Modifier.height(8.dp))
            for (side in listOf(Side.WHITE, Side.BLACK)) {
                val captures = game.fallen.count { it.side == side.other && !it.despair }
                val despair = game.fallen.count { it.side == side && it.despair }
                val absolved = game.absolutions.count { it.side == side }
                val rolls = game.rolls.filter { it.side == side }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surfaceContainer)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SideDot(side, 14.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(game.playerName(side), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    val parts = buildList {
                        add("⚔️ $captures capture" + if (captures == 1) "" else "s")
                        if (absolved > 0) add("✝️ $absolved " + if (game.mode == Mode.EMOTIONAL) "absolved" else "forgiven")
                        if (despair > 0) add("💧 $despair lost to despair")
                    }
                    Text(parts.joinToString("   "), style = MaterialTheme.typography.bodyMedium)
                    if (rolls.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (r in rolls) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PieceTile(r.kind, r.side, 26.dp)
                                    Spacer(Modifier.width(4.dp))
                                    DieFace(r.roll, 22.dp)
                                    Spacer(Modifier.width(2.dp))
                                    Text(moodEmoji(Rules.moodForRoll(r.roll), game.mode), fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("New game", fontSize = 17.sp) }
            Row {
                TextButton(onClick = onUndo) { Text("Undo last move") }
                TextButton(onClick = onViewBoard) { Text("View board") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesSheet(mode: Mode, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("How to play", style = MaterialTheme.typography.headlineMedium)
            Text("Normal chess rules apply, with the changes below. Make each move on the real board, then tap the piece and its destination here.")
            val emotional = @Composable {
                Text("🎲 Emotional (dice)", style = MaterialTheme.typography.headlineSmall)
                Text("The first time a piece captures, roll one die. Each piece rolls only once, ever.")
                DieRule(listOf(1), Mood.DEVASTATED, "Frozen and gives no check. Unless absolved, it leaves the board at the end of its owner's third following turn (the badge counts down). A despairing king loses the game, and so does a departure that exposes your own king.")
                DieRule(listOf(2, 3), Mood.GUILTY, "Moves normally but cannot capture or give check until absolved.")
                DieRule(listOf(4, 5), Mood.UNAFFECTED, "Nothing happens.")
                DieRule(listOf(6), Mood.PSYCHOPATH, "Permanent. Every later capture earns one mandatory extra move with the same piece. A capture made on that extra move earns nothing more. If the extra move can take the enemy king, it wins.")
                Bullet("✝️ Absolution takes your whole turn: a friendly bishop (not devastated) on one of the 8 squares around a guilt-ridden or devastated piece absolves it. Tap the piece, then Absolve. Not allowed while in check, during a bonus move, or on itself.")
                Bullet("👑 Win by checkmate, by a psychopath taking the king, or by a despairing king. Draws: stalemate, threefold repetition, 50-move rule, or only kings left.")
            }
            val classic = @Composable {
                Text("✝️ Classic Remorse", style = MaterialTheme.typography.headlineSmall)
                Bullet("😔 A piece that captures becomes remorseful.")
                Bullet("A remorseful piece can move and block, but cannot capture – so it gives no check. A king may walk onto squares it only 'attacks'.")
                Bullet("A friendly bishop that moves onto one of the 8 squares around it forgives it. The piece walking up to a bishop does not count, and a bishop never forgives itself.")
                Bullet("Promotion keeps the remorse.")
            }
            HorizontalDivider()
            if (mode == Mode.EMOTIONAL) emotional() else classic()
            HorizontalDivider()
            if (mode == Mode.EMOTIONAL) classic() else emotional()
            HorizontalDivider()
            Text(
                "Rules follow the published Remorse Chess descriptions (nabous.dev; remorsechess.com dice rules). Sources differ on small points; this app uses the remorsechess.com version for the dice game.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DieRule(faces: List<Int>, mood: Mood, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Row(Modifier.width(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (f in faces) DieFace(f, 28.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "${moodEmoji(mood, Mode.EMOTIONAL)} ${Rules.moodLabel(mood, Mode.EMOTIONAL)}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = moodColor(mood)!!,
            )
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•  ")
        Text(text)
    }
}
