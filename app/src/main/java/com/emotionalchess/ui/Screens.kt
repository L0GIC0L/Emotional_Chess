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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.window.DialogProperties
import com.emotionalchess.engine.Action
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Kind
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.ModeGroup
import com.emotionalchess.engine.Perk
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Move
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side
import com.emotionalchess.net.NearbyPeerLink
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun App(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().background(BackdropBrush)) {
        val screen = when {
            vm.game != null -> 2
            vm.online != null -> 1
            else -> 0
        }
        Crossfade(targetState = screen, animationSpec = tween(400), label = "screen") { which ->
            val game = vm.game
            when {
                which == 2 && game != null -> GameScreen(vm, game)
                which == 1 && vm.online != null -> LobbyScreen(vm)
                else -> NewGameScreen(onStart = vm::newGame, onHost = vm::hostGame, onJoin = vm::joinGame)
            }
        }
    }
}

// ======================================================================= new game

@Composable
fun NewGameScreen(
    onStart: (Mode, String, String, Int) -> Unit,
    onHost: (Mode, String, Int, Side?) -> Unit = { _, _, _, _ -> },
    onJoin: (String) -> Unit = {},
    startWithTwoPhones: Boolean = false,
) {
    var mode by rememberSaveable { mutableStateOf(Mode.STANDARD) }
    var white by rememberSaveable { mutableStateOf("") }
    var black by rememberSaveable { mutableStateOf("") }
    var hints by rememberSaveable { mutableIntStateOf(3) }
    var twoPhones by rememberSaveable { mutableStateOf(startWithTwoPhones) }
    var myName by rememberSaveable { mutableStateOf("") }
    var mySide by rememberSaveable { mutableStateOf<Side?>(Side.WHITE) }
    var showRules by remember { mutableStateOf(false) }
    val nearby = rememberNearbyPermission()

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            HeroPieces()
            Text("Emotional Chess", style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
            Text(
                "Chess for a real board and a real friend.\nPlain, strange, or full of feelings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            SectionLabel("Play on")
            Choice(listOf(false to "📱 One phone", true to "📱📱 Two phones"), twoPhones) { twoPhones = it }
            if (twoPhones) {
                Text(
                    "Each player uses their own phone, linked over Bluetooth / Wi-Fi in the same room. No account needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            for (group in ModeGroup.entries) {
                SectionLabel(group.title)
                for (m in Mode.entries.filter { it.group == group }) ModeRow(m, mode == m) { mode = m }
            }
            if (twoPhones) {
                SectionLabel("You")
                OutlinedTextField(
                    myName, { myName = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Choice(listOf(Side.WHITE to "⚪ White", Side.BLACK to "⚫ Black", null to "🎲 Random"), mySide) { mySide = it }
            } else {
                SectionLabel("Players")
                PlayerField(white, { white = it }, Side.WHITE, ImeAction.Next)
                PlayerField(black, { black = it }, Side.BLACK, ImeAction.Done)
            }
            SectionLabel("Hints")
            HintChoice(hints) { hints = it }
            nearby.denied?.let { Text("⚠️ $it", color = MaterialTheme.colorScheme.error) }
        }
        Column(
            Modifier.widthIn(max = 560.dp).padding(horizontal = 22.dp).padding(top = 8.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (twoPhones) {
                GlowButton("Host · ${mode.emoji} ${mode.title}") { nearby.run { onHost(mode, myName.trim(), hints, mySide) } }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { nearby.run { onJoin(myName.trim()) } }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Join a friend's game", fontSize = 16.sp)
                }
            } else {
                GlowButton("Begin · ${mode.emoji} ${mode.title}") { onStart(mode, white.trim(), black.trim(), hints) }
            }
            TextButton(onClick = { showRules = true }) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null)
                Spacer(Modifier.width(8.dp))
                Text("How to play ${mode.title}")
            }
        }
    }
    if (showRules) RulesSheet(mode) { showRules = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(
                selected = value == v,
                onClick = { onChange(v) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

/** Asks for the Nearby permissions before running a two-phone action. */
private class NearbyPermission(private val request: ((Boolean) -> Unit) -> Unit) {
    var denied by mutableStateOf<String?>(null)
    fun run(action: () -> Unit) = request { granted ->
        if (granted) { denied = null; action() } else denied = "Two-phone play needs the Nearby devices permission."
    }
}

@Composable
private fun rememberNearbyPermission(): NearbyPermission {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    // Previews and screenshot tests have no activity to ask through; there we just go ahead.
    val launcher = if (LocalActivityResultRegistryOwner.current != null) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pending?.invoke(result.values.all { it })
            pending = null
        }
    } else null
    return remember(launcher) {
        NearbyPermission { done ->
            if (launcher == null || NearbyPeerLink.hasPermissions(context)) done(true)
            else { pending = done; launcher.launch(NearbyPeerLink.permissions()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HintChoice(value: Int, onChange: (Int) -> Unit) {
    val options = listOf(0 to "Off", 3 to "3 each", -1 to "Unlimited")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(
                selected = value == v,
                onClick = { onChange(v) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
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
private fun ModeRow(mode: Mode, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) scheme.primary else scheme.outlineVariant, label = "border")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mode.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mode.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(mode.tagline, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
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
                        "${game.mode.emoji} ${game.mode.title}",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (vm.online == null) IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                    IconButton(onClick = vm::flip) { Icon(Icons.Default.SwapVert, "Flip board") }
                    IconButton(onClick = { showRules = true }) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Rules") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            val online = vm.online
                            if (online != null) {
                                DropdownMenuItem(
                                    text = { Text("Offer a draw") },
                                    leadingIcon = { Text("🤝") },
                                    enabled = game.result == null && online.phase == LinkPhase.PLAYING,
                                    onClick = { menu = false; vm.offerDraw() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Resign") },
                                    leadingIcon = { Icon(Icons.Default.Flag, null) },
                                    enabled = game.result == null && online.phase == LinkPhase.PLAYING,
                                    onClick = { menu = false; confirmResign = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Unlink – keep playing on this phone") },
                                    leadingIcon = { Text("📴") },
                                    onClick = { menu = false; vm.leaveOnline() },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Resign / draw…") },
                                    leadingIcon = { Icon(Icons.Default.Flag, null) },
                                    enabled = game.result == null,
                                    onClick = { menu = false; confirmResign = true },
                                )
                            }
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
                ChessBoard(game, vm.selected, vm.targets, vm.flipped, vm::onSquareTapped, m, hint = vm.hint)
            }
            // Wide or near-square windows (unfolded Pixel Fold, tablets, landscape): side by side.
            if (maxWidth >= 600.dp && maxWidth >= maxHeight * 0.9f) {
                val boardSize = minOf(maxHeight - 32.dp, maxWidth * 0.6f)
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    board(Modifier.size(boardSize))
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerBar(game, top)
                        StatusBanner(vm, game)
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
                    StatusBanner(vm, game)
                    PieceCard(vm, game)
                    MoveLog(game, Modifier.weight(1f))
                }
            }
        }
    }

    // ---------------- dialogs
    val mine = vm.myTurn(game)
    if ((game.pendingRoll != null && mine) || vm.rollReveal != null) DiceDialog(game, vm.rollReveal, vm::roll, vm::dismissRoll)
    vm.promotionChoices?.let { PromotionDialog(it, game.toMove, vm::choosePromotion) }
    if (game.pendingPerk != null && mine) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            PerkCard(game, suggested = (vm.hint?.action as? Action.Pick)?.perk, onPick = vm::choosePerk, onHint = vm::requestHint, thinking = vm.thinking)
        }
    }
    if (game.result != null && vm.showResult && vm.rollReveal == null) {
        Dialog(onDismissRequest = vm::dismissResult) {
            ReportCard(game, onNewGame = vm::closeGame, onUndo = if (vm.online == null) vm::undo else null, onViewBoard = vm::dismissResult)
        }
    }
    if (confirmNew) AlertDialog(
        onDismissRequest = { confirmNew = false },
        title = { Text("Start a new game?") },
        text = { Text("The current game will be discarded.") },
        confirmButton = { Button(onClick = { confirmNew = false; vm.closeGame() }) { Text("New game") } },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancel") } },
    )
    val online = vm.online
    if (confirmResign && online != null) AlertDialog(
        onDismissRequest = { confirmResign = false },
        title = { Text("Resign?") },
        text = { Text("${online.peerName ?: "Your friend"} wins the game.") },
        confirmButton = { Button(onClick = { confirmResign = false; online.mySide?.let(vm::resign) }) { Text("Resign") } },
        dismissButton = { TextButton(onClick = { confirmResign = false }) { Text("Cancel") } },
    )
    if (online?.drawOffered == true) AlertDialog(
        onDismissRequest = {},
        title = { Text("🤝 ${online.peerName ?: "Your friend"} offers a draw") },
        confirmButton = { Button(onClick = { vm.answerDraw(true) }) { Text("Accept") } },
        dismissButton = { TextButton(onClick = { vm.answerDraw(false) }) { Text("Play on") } },
    )
    val notice = online?.notice ?: online?.error
    if (notice != null) AlertDialog(
        onDismissRequest = vm::dismissNotice,
        text = { Text(notice) },
        confirmButton = { TextButton(onClick = vm::dismissNotice) { Text("OK") } },
    )
    if (confirmResign && online == null) AlertDialog(
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
        if (game.mode == Mode.THREE_CHECK) {
            Spacer(Modifier.width(6.dp))
            Text("⚡${game.checksBy(side)}/3", style = MaterialTheme.typography.labelLarge, color = scheme.tertiary)
        }
        for (mood in listOf(Mood.PSYCHOPATH, Mood.GUILTY, Mood.DEVASTATED, Mood.INFECTED)) {
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
                    val tint = when {
                        !f.despair -> null
                        game.mode == Mode.PLAGUE -> Color(0xFFB8E07A)
                        else -> Color(0xFF8FC4FF)
                    }
                    PieceImage(f.kind, f.side, Modifier.size(22.dp), tint = tint)
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
private fun StatusBanner(vm: GameViewModel, game: GameState) {
    val scheme = MaterialTheme.colorScheme
    val name = game.playerName(game.toMove)
    val result = game.result
    val hint = vm.hint
    val online = vm.online
    val mine = vm.myTurn(game)
    val status = when {
        result != null -> Status(
            if (result.winner != null) "👑" else "🤝",
            result.winner?.let { "${game.playerName(it)} wins" } ?: "Draw",
            result.reason, scheme.tertiary,
        )
        online?.phase == LinkPhase.LOST -> Status(
            "📡", "Connection lost",
            "Looking for ${online.peerName ?: "your friend"}'s phone. Keep both apps open, with Bluetooth and Wi-Fi on.", scheme.error,
        )
        game.pendingRoll != null -> Status(
            "🎲", if (mine) "Roll the die" else "$name is rolling",
            "${game.board[game.pendingRoll]?.name} made its first capture.", scheme.tertiary,
        )
        game.pendingPerk != null -> Status(
            "⭐", if (mine) "Level up!" else "$name is levelling up",
            "${if (mine) "Pick" else "Picking"} a perk for the ${game.board[game.pendingPerk]?.kind?.label?.lowercase()}.", scheme.tertiary,
        )
        hint != null -> Status("💡", hint.text, "Hint for $name – shown on the board.", scheme.tertiary)
        game.bonusSquare != null -> Status(
            "😈", "$name strikes again",
            "The psychopath on ${Rules.squareName(game.bonusSquare)} must move again.", moodColor(Mood.PSYCHOPATH)!!,
        )
        Rules.inCheck(game) -> Status("⚔️", "Check!", "$name must protect the king.", scheme.error)
        online != null && !mine -> Status(
            if (game.toMove == Side.WHITE) "⚪" else "⚫",
            "$name to move",
            "Waiting for their move on their phone…",
            scheme.secondary,
        )
        else -> Status(
            if (game.toMove == Side.WHITE) "⚪" else "⚫",
            if (online != null) "Your move" else "$name to move",
            turnDetail(game),
            scheme.secondary,
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = status,
                contentKey = { it.emoji + it.title },
                transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 4 }) togetherWith fadeOut(tween(150)) },
                label = "status",
                modifier = Modifier.weight(1f),
            ) { st ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(listOf(st.tone.copy(alpha = 0.3f), st.tone.copy(alpha = 0.06f))))
                        .border(1.dp, st.tone.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(st.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(st.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (st.detail.isNotEmpty()) {
                            Text(st.detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (game.hintsAllowed != 0 && result == null) {
                Spacer(Modifier.width(8.dp))
                HintButton(vm, game)
            }
        }

        if (online?.phase == LinkPhase.LOST) {
            TextButton(onClick = vm::leaveOnline) { Text("📴 Keep playing on this phone instead") }
        }
    }
}

/** What to tell the player whose turn it is, per mode. */
private fun turnDetail(game: GameState): String {
    val absolve = Rules.legalAbsolutions(game)
    if (absolve.isNotEmpty()) {
        val verb = when (game.mode) {
            Mode.PLAGUE -> "A bishop can cure"
            Mode.CLASSIC -> "A bishop can forgive"
            else -> "A bishop can absolve"
        }
        return "$verb " + absolve.joinToString {
            if (Rules.absolverFor(game, it) == it) "itself on ${Rules.squareName(it)}" else "${game.board[it]!!.kind.label} ${Rules.squareName(it)}"
        } + " – tap it."
    }
    if (game.mode == Mode.ANTICHESS && Rules.allLegalMoves(game).any { Rules.isCapture(game, it) }) return "A capture is possible, so you must take."
    if (game.log.isEmpty()) return when (game.mode) {
        Mode.CHESS960 -> "Set up the back ranks exactly as shown. To castle, tap the king, then the rook."
        Mode.RACING_KINGS -> "Set up as shown. No checks allowed – race your king to the 8th rank."
        Mode.KING_OF_THE_HILL -> "Get your king to a gold centre square to win."
        else -> "Play on the real board, then tap the piece and its square here."
    }
    return when (game.mode) {
        Mode.THREE_CHECK -> "Move ${game.fullmove} · checks ${game.whiteChecks}–${game.blackChecks}"
        Mode.RACING_KINGS -> "Move ${game.fullmove} · no checks allowed"
        else -> "Move ${game.fullmove}"
    }
}

@Composable
private fun HintButton(vm: GameViewModel, game: GameState) {
    val left = game.hintsLeft(game.toMove)
    val enabled = left > 0 && !vm.thinking && game.pendingRoll == null && vm.myTurn(game)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = vm::requestHint, enabled = enabled, modifier = Modifier.size(52.dp)) {
            if (vm.thinking) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            else Icon(Icons.Default.Lightbulb, "Hint")
        }
        Text(
            if (game.hintsAllowed < 0) "∞" else "$left left",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieceCard(vm: GameViewModel, game: GameState) {
    val scheme = MaterialTheme.colorScheme
    val sq = vm.selected
    val p = sq?.let { game.board[it] }
    if (sq == null || p == null) {
        // Nothing selected: a compact legend of the feelings in play, or the mode's goal.
        val moods = when (game.mode) {
            Mode.EMOTIONAL -> listOf(Mood.UNAFFECTED, Mood.GUILTY, Mood.DEVASTATED, Mood.PSYCHOPATH)
            Mode.CLASSIC -> listOf(Mood.GUILTY)
            Mode.PLAGUE -> listOf(Mood.INFECTED, Mood.IMMUNE)
            else -> emptyList()
        }
        if (moods.isEmpty()) {
            Text(
                "${game.mode.emoji}  ${game.mode.tagline}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
            return
        }
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
            val extra = when (p.mood) {
                Mood.DEVASTATED -> " · leaves in ${p.countdown}"
                Mood.INFECTED -> " · dies in ${p.countdown}"
                else -> ""
            }
            val feelings = game.mode == Mode.EMOTIONAL || game.mode == Mode.CLASSIC || game.mode == Mode.PLAGUE
            if (feelings) {
                Text(
                    "${moodEmoji(p.mood, game.mode)} ${Rules.moodLabel(p.mood, game.mode)}$extra",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = moodColor(p.mood) ?: scheme.onSurface,
                )
                Text(Rules.moodDescription(p.mood, game.mode), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            } else if (p.perks.isEmpty()) {
                Text(
                    if (game.mode == Mode.ROGUELIKE && p.kind != Kind.KING) "Capture with it to level up and pick a perk." else "Tap a highlighted square to move it.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            } else {
                Text("⭐ ${p.perks.size} perk" + if (p.perks.size == 1) "" else "s", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.tertiary)
            }
            for (perk in p.perks) {
                Text("${perk.emoji} ${perk.label}: ${perk.text}", style = MaterialTheme.typography.bodySmall)
            }
        }
        val healer = if (vm.myTurn(game)) Rules.absolverFor(game, sq) else null
        if (healer != null) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = vm::absolveSelected,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.tertiary, contentColor = scheme.onTertiary),
            ) {
                Text(
                    when (game.mode) {
                        Mode.PLAGUE -> "💉 Cure"
                        Mode.CLASSIC -> "✝ Forgive"
                        else -> "✝ Absolve"
                    } + if (healer == sq) " itself" else ""
                )
            }
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
                    PieceTile(m.promotion ?: Kind.QUEEN, side, if (moves.size > 4) 46.dp else 60.dp, Modifier.clickable { onChoose(m) })
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
fun ReportCard(game: GameState, onNewGame: () -> Unit, onUndo: (() -> Unit)?, onViewBoard: () -> Unit) {
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
                        if (game.mode == Mode.THREE_CHECK) add("⚡ ${game.checksBy(side)} checks")
                        if (absolved > 0) add(
                            when (game.mode) {
                                Mode.PLAGUE -> "💉 $absolved cured"
                                Mode.CLASSIC -> "✝️ $absolved forgiven"
                                else -> "✝️ $absolved absolved"
                            }
                        )
                        if (despair > 0) add(if (game.mode == Mode.PLAGUE) "☠️ $despair lost to plague" else "💧 $despair lost to despair")
                        val perks = game.board.filterNotNull().filter { it.side == side }.sumOf { it.perks.size }
                        if (perks > 0) add("⭐ $perks perks")
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
                if (onUndo != null) TextButton(onClick = onUndo) { Text("Undo last move") }
                TextButton(onClick = onViewBoard) { Text("View board") }
            }
        }
    }
}

/** Roguelike level-up: the capturing piece picks one of three perks. */
@Composable
fun PerkCard(game: GameState, suggested: Perk?, onPick: (Perk) -> Unit, onHint: () -> Unit, thinking: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val at = game.pendingPerk ?: return
    val p = game.board[at] ?: return
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.border(1.dp, scheme.tertiary.copy(alpha = 0.7f), RoundedCornerShape(28.dp)),
    ) {
        Column(
            Modifier
                .background(Brush.radialGradient(listOf(scheme.tertiary.copy(alpha = 0.22f), Color.Transparent), radius = 700f))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PieceTile(p.kind, p.side, 72.dp)
            Text("Level up!", style = MaterialTheme.typography.displaySmall, color = scheme.tertiary)
            Text(
                "${p.name} on ${Rules.squareName(at)} made a capture. Choose its new power:",
                textAlign = TextAlign.Center, color = scheme.onSurfaceVariant,
            )
            for (perk in game.perkOffer) {
                val best = perk == suggested
                Surface(
                    onClick = { onPick(perk) },
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.surfaceContainer,
                    contentColor = scheme.onSurface,
                    border = BorderStroke(if (best) 2.dp else 1.dp, if (best) scheme.tertiary else scheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(perk.emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(perk.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(perk.text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        if (best) Text("💡", fontSize = 20.sp)
                    }
                }
            }
            if (game.hintsAllowed != 0 && game.hintsLeft(game.toMove) > 0 && suggested == null) {
                TextButton(onClick = onHint, enabled = !thinking) { Text(if (thinking) "Thinking…" else "💡 Which one should I pick?") }
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
            Text("${mode.emoji} ${mode.title}", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (mode == Mode.STANDARD) "Normal chess. Make each move on the real board, then tap the piece and its destination here."
                else "Normal chess rules apply, with the changes below. Make each move on the real board, then tap the piece and its destination here.",
            )
            HorizontalDivider()
            ModeRules(mode)
            HorizontalDivider()
            Text("Other modes", style = MaterialTheme.typography.titleMedium)
            for (m in Mode.entries.filter { it != mode }) {
                Text("${m.emoji} ${m.title} – ${m.tagline}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (mode == Mode.EMOTIONAL || mode == Mode.CLASSIC) {
                Text(
                    "Remorse rules follow nabous.dev (classic) and remorsechess.com (dice). House rule: a bishop can heal itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeRules(mode: Mode) {
    when (mode) {
        Mode.STANDARD -> {
            Bullet("👑 Checkmate wins. Stalemate, threefold repetition, the 50-move rule and bare kings are draws.")
            Bullet("💡 Stuck? The hint button suggests a move (if hints are on).")
        }
        Mode.CHESS960 -> {
            Bullet("The back rank is shuffled: bishops on opposite colours, the king somewhere between the rooks. Black mirrors White. Set up your board as the app shows.")
            Bullet("Castling ends with the king on g1/c1 and the rook on f1/d1 (g8/c8 and f8/d8 for Black). The squares in between must be empty and the king may not pass through check.")
            Bullet("To castle here, tap the king, then the rook.")
        }
        Mode.KING_OF_THE_HILL -> {
            Bullet("⛰️ Move your king onto d4, e4, d5 or e5 (marked in gold) and you win at once.")
            Bullet("Checkmate still wins too.")
        }
        Mode.THREE_CHECK -> {
            Bullet("⚡ The third check you give wins the game. The player bars count them.")
            Bullet("Checkmate still wins too.")
        }
        Mode.ATOMIC -> {
            Bullet("💥 A capture explodes: the capturing piece, the captured piece and every piece on the 8 squares around them leave the board – except pawns, which only go if they capture or are captured.")
            Bullet("Kings cannot capture.")
            Bullet("Blowing up the enemy king wins. A move that would blow up your own king is illegal.")
            Bullet("While the two kings touch, neither of them can be in check.")
        }
        Mode.ANTICHESS -> {
            Bullet("🙃 If you can capture, you must (you choose which capture).")
            Bullet("There is no check. The king is an ordinary piece and can be captured; pawns may promote to a king. No castling.")
            Bullet("Win by losing all your pieces, or by having no legal move.")
        }
        Mode.RACING_KINGS -> {
            Bullet("🏁 No pawns. All pieces start on the first two ranks as shown; no castling.")
            Bullet("Giving check is not allowed, and neither is moving into check.")
            Bullet("The first king on the 8th rank (marked in gold) wins. If White gets there first and Black can reach it on the very next move, it is a draw.")
        }
        Mode.EMOTIONAL -> {
            Text("The first time a piece captures, roll one die. Each piece rolls only once, ever.")
            DieRule(listOf(1), Mood.DEVASTATED, "Frozen and gives no check. Unless absolved, it leaves the board at the end of its owner's third following turn (the badge counts down). A despairing king loses the game, and so does a departure that exposes your own king.")
            DieRule(listOf(2, 3), Mood.GUILTY, "Moves normally but cannot capture or give check until absolved.")
            DieRule(listOf(4, 5), Mood.UNAFFECTED, "Nothing happens.")
            DieRule(listOf(6), Mood.PSYCHOPATH, "Permanent. Every later capture earns one mandatory extra move with the same piece. A capture made on that extra move earns nothing more. If the extra move can take the enemy king, it wins.")
            Bullet("✝️ Absolution takes your whole turn: a friendly bishop (not devastated) on one of the 8 squares around a guilt-ridden or devastated piece absolves it. A guilt-ridden or devastated bishop can absolve itself. Tap the piece, then Absolve. Not allowed while in check or during a bonus move.")
            Bullet("👑 Win by checkmate, by a psychopath taking the king, or by a despairing king. Draws: stalemate, threefold repetition, 50-move rule, or only kings left.")
        }
        Mode.CLASSIC -> {
            Bullet("😔 A piece that captures becomes remorseful.")
            Bullet("A remorseful piece can move and block, but cannot capture – so it gives no check. A king may walk onto squares it only 'attacks'.")
            Bullet("A friendly bishop that moves onto one of the 8 squares around it forgives it. The piece walking up to a bishop does not count.")
            Bullet("A remorseful bishop can forgive itself; that takes its whole turn. Tap it, then Forgive.")
            Bullet("Promotion keeps the remorse.")
        }
        Mode.PLAGUE -> {
            Bullet("🤢 A piece that captures catches the plague from its victim. The badge counts down its owner's turns.")
            Bullet("🦠 When an infected piece moves, every healthy piece next to its new square catches it too – friend or foe.")
            Bullet("☠️ An infected piece dies at the end of its owner's ${Rules.INCUBATION_TURNS}th turn after catching it. If that is the king, its owner loses.")
            Bullet("💉 A bishop next to an infected friend can spend the turn curing it. Cured pieces are immune (💪) for good. Not allowed while in check.")
            Bullet("Bishops are doctors and never fall ill.")
        }
        Mode.ROGUELIKE -> {
            Bullet("⭐ When a piece other than the king captures, it levels up: pick one of three perks.")
            for (perk in Perk.entries) Bullet("${perk.emoji} ${perk.label}: ${perk.text}")
            Bullet("Perks stack, survive promotion, and count for check: a rook that can leap gives check like a knight.")
            Bullet("The app remembers every perk for you. Tap a piece to see its perks.")
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
