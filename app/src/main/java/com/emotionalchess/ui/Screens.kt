package com.emotionalchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Kind
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Move
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side

@Composable
fun App(vm: GameViewModel) {
    val game = vm.game
    if (game == null) NewGameScreen(onStart = vm::newGame) else GameScreen(vm, game)
}

// ======================================================================= new game

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(onStart: (Mode, String, String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(Mode.EMOTIONAL) }
    var white by rememberSaveable { mutableStateOf("") }
    var black by rememberSaveable { mutableStateOf("") }
    var showRules by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Emotional Chess") }) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Play on your real board. Mirror each move here and the app tracks every piece's feelings, rolls, countdowns and absolutions.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Variant", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Mode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                        ) { Text(m.title) }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        if (mode == Mode.EMOTIONAL)
                            "A piece's first capture rolls a die: 1 Devastated, 2–3 Guilt-ridden, 4–5 Unaffected, 6 Psychopath. Bishops absolve the afflicted."
                        else
                            "Every capture makes the piece remorseful: it can't capture or give check until a friendly bishop moves next to it.",
                        Modifier.padding(16.dp),
                    )
                }
                OutlinedTextField(white, { white = it }, label = { Text("White player") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(black, { black = it }, label = { Text("Black player") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onStart(mode, white.trim(), black.trim()) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Start game")
                }
                TextButton(onClick = { showRules = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Read the rules")
                }
            }
        }
    }
    if (showRules) RulesSheet(mode) { showRules = false }
}

// ======================================================================= game

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel, game: GameState) {
    var menu by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(game.mode.title) },
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
                            DropdownMenuItem(text = { Text("New game") }, onClick = { menu = false; confirmNew = true })
                        }
                    }
                },
            )
        },
    ) { pad ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
            // Wide or near-square windows (unfolded Pixel Fold, tablets, landscape): side by side.
            val sideBySide = maxWidth >= 600.dp && maxWidth >= maxHeight * 0.9f
            if (sideBySide) {
                val boardSize = minOf(maxHeight - 32.dp, maxWidth * 0.62f)
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ChessBoard(game, vm.selected, vm.targets, vm.flipped, vm::onSquareTapped, Modifier.size(boardSize))
                    SidePanel(vm, game, Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ChessBoard(
                        game, vm.selected, vm.targets, vm.flipped, vm::onSquareTapped,
                        Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth(),
                    )
                    SidePanel(vm, game, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp))
                }
            }
        }
    }

    // ---------------- dialogs
    if (game.pendingRoll != null) RollDialog(game, vm::roll)
    vm.rollReveal?.let { RevealDialog(it, game.mode, vm::dismissRoll) }
    vm.promotionChoices?.let { PromotionDialog(it, game.toMove, vm::choosePromotion) }
    val result = game.result
    if (result != null && vm.showResult && vm.rollReveal == null) {
        AlertDialog(
            onDismissRequest = vm::dismissResult,
            title = { Text(result.winner?.let { "${game.playerName(it)} wins" } ?: "Draw") },
            text = { Text(result.reason) },
            confirmButton = { Button(onClick = { vm.closeGame() }) { Text("New game") } },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::undo) { Text("Undo") }
                    TextButton(onClick = vm::dismissResult) { Text("View board") }
                }
            },
        )
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

@Composable
private fun SidePanel(vm: GameViewModel, game: GameState, modifier: Modifier) {
    val logState = rememberLazyListState()
    LaunchedEffect(game.log.size) { if (game.log.isNotEmpty()) logState.animateScrollToItem(game.log.size - 1) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusCard(game)
        SelectedCard(vm, game)
        Legend(game.mode)
        Text("Moves", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            state = logState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(game.log) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(game: GameState) {
    val scheme = MaterialTheme.colorScheme
    val check = game.result == null && Rules.inCheck(game)
    val (title, detail) = when {
        game.result != null -> (game.result.winner?.let { "${game.playerName(it)} wins" } ?: "Draw") to game.result.reason
        game.pendingRoll != null -> "Roll the die" to "${game.board[game.pendingRoll]?.name} made its first capture."
        game.bonusSquare != null -> "${game.playerName(game.toMove)}: bonus move" to
            "The psychopath on ${Rules.squareName(game.bonusSquare)} must move again."
        else -> "${game.playerName(game.toMove)} to move" + (if (check) " – CHECK" else "") to
            "${game.toMove.label} · move ${game.fullmove}" + absolveHint(game)
    }
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (check) scheme.errorContainer else scheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(if (game.toMove == Side.WHITE) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black, CircleShape)
                    .padding(1.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun absolveHint(game: GameState): String {
    val list = Rules.legalAbsolutions(game)
    if (list.isEmpty()) return ""
    return "\nCan absolve: " + list.joinToString { "${game.board[it]!!.kind.label} ${Rules.squareName(it)}" } + " (tap it)"
}

@Composable
private fun SelectedCard(vm: GameViewModel, game: GameState) {
    val sq = vm.selected ?: return
    val p = game.board[sq] ?: return
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PieceGlyph(p.kind, p.side, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${p.name} · ${Rules.squareName(sq)}", style = MaterialTheme.typography.titleMedium)
                val mood = Rules.moodLabel(p.mood, game.mode)
                val extra = if (p.mood == Mood.DEVASTATED) " · leaves after ${p.countdown} more of its owner's turns" else ""
                Text("$mood$extra", color = moodColor(p.mood) ?: MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Text(Rules.moodDescription(p.mood, game.mode), style = MaterialTheme.typography.bodySmall)
            }
            if (Rules.absolverFor(game, sq) != null) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = vm::absolveSelected) { Text("Absolve") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(mode: Mode) {
    val moods = if (mode == Mode.EMOTIONAL) listOf(Mood.UNAFFECTED, Mood.GUILTY, Mood.DEVASTATED, Mood.PSYCHOPATH) else listOf(Mood.GUILTY)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (m in moods) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(moodColor(m)!!, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(Rules.moodLabel(m, mode), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ======================================================================= dialogs

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RollDialog(game: GameState, onRoll: (Int?) -> Unit) {
    val p = game.board[game.pendingRoll!!]
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Casino, null) },
        title = { Text("Roll the die") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${p?.name} on ${Rules.squareName(game.pendingRoll)} made its first capture. Roll a real die and tap the result, or let the app roll.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 1..6) FilledTonalButton(onClick = { onRoll(n) }) { Text(n.toString(), fontSize = 18.sp) }
                }
            }
        },
        confirmButton = { Button(onClick = { onRoll(null) }) { Text("Roll for me") } },
    )
}

@Composable
private fun RevealDialog(r: RollReveal, mode: Mode, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎲 ${r.roll}", style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${r.pieceName} is ${Rules.moodLabel(r.mood, mode)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = moodColor(r.mood) ?: MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(Rules.moodDescription(r.mood, mode), textAlign = TextAlign.Center)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun PromotionDialog(moves: List<Move>, side: Side, onChoose: (Move?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoose(null) },
        title = { Text("Promote to") },
        text = {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                for (m in moves) {
                    val kind = m.promotion ?: Kind.QUEEN
                    TextButton(onClick = { onChoose(m) }) { PieceGlyph(kind, side, 48.dp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(null) }) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesSheet(mode: Mode, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("How to play", style = MaterialTheme.typography.headlineSmall)
            Text("Normal chess rules apply, with the changes below. Make each move on the real board, then tap the piece and its destination here.")
            HorizontalDivider()
            Text("Classic Remorse", style = MaterialTheme.typography.titleMedium)
            Bullet("A piece that captures becomes remorseful (orange ring).")
            Bullet("A remorseful piece can move and block, but cannot capture – so it gives no check. A king may walk onto squares it only 'attacks'.")
            Bullet("A friendly bishop that moves onto one of the 8 squares around it forgives it. The piece walking up to a bishop does not count, and a bishop never forgives itself.")
            Bullet("Promotion keeps the remorse.")
            HorizontalDivider()
            Text("Emotional (dice)", style = MaterialTheme.typography.titleMedium)
            Bullet("The first time a piece captures, roll one die. Each piece rolls only once, ever.")
            Bullet("1 – Devastated (blue): frozen, gives no check. Unless absolved, it leaves the board at the end of its owner's third following turn. If it is the king, its owner loses; if its departure exposes its own king to check, its owner loses.")
            Bullet("2–3 – Guilt-ridden (orange): moves normally but cannot capture or give check until absolved.")
            Bullet("4–5 – Unaffected (green dot): nothing happens.")
            Bullet("6 – Psychopath (purple): permanent. Every later capture earns one mandatory extra move with the same piece. A capture made on that extra move earns nothing more. If the extra move can take the enemy king, it wins.")
            Bullet("Absolution takes your whole turn: a friendly bishop (not devastated) standing on one of the 8 squares around a guilt-ridden or devastated piece absolves it. Tap the piece, then Absolve. Not allowed while in check, during a bonus move, or on itself.")
            Bullet("Win by checkmate, by a psychopath taking the king, or by a despairing king. Draws: stalemate, threefold repetition, 50-move rule, or only kings left.")
            HorizontalDivider()
            Text(
                "Rules follow the published Remorse Chess descriptions (nabous.dev; remorsechess.com dice rules). Sources differ on small points; this app uses the remorsechess.com version for the dice game.",
                style = MaterialTheme.typography.bodySmall,
            )
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
