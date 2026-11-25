package com.emotionalchess.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.emotionalchess.engine.Engine
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Hint
import com.emotionalchess.engine.History
import com.emotionalchess.engine.Kind
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Move
import com.emotionalchess.engine.Perk
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side
import com.emotionalchess.engine.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

@Serializable
data class SaveData(
    val game: GameState? = null,
    /** Undo states in the first save format (whole states). Read for old saves only. */
    val undo: List<GameState> = emptyList(),
    val history: List<Snapshot> = emptyList(),
    val flipped: Boolean = false,
)

interface GameStore {
    fun load(): SaveData?
    fun save(game: GameState?, undo: List<GameState>, flipped: Boolean)
}

/** Saves in the background, one write at a time, via a temp file so a crash never leaves half a save. */
class FileGameStore(private val file: File) : GameStore {
    private val json = Json { ignoreUnknownKeys = true }
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override fun load(): SaveData? = runCatching { json.decodeFromString<SaveData>(file.readText()) }.getOrNull()

    override fun save(game: GameState?, undo: List<GameState>, flipped: Boolean) {
        scope.launch {
            runCatching {
                val history = if (game != null) History.compact(game, undo) else emptyList()
                val tmp = File(file.path + ".tmp")
                tmp.writeText(json.encodeToString(SaveData.serializer(), SaveData(game, history = history, flipped = flipped)))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }
}

data class RollReveal(
    val roll: Int,
    val mood: Mood,
    val pieceName: String,
    val kind: Kind,
    val side: Side,
    /** True when the app rolled: play the tumble. A typed-in physical roll is revealed at once. */
    val animated: Boolean,
)

enum class FeedbackKind { MOVE, CAPTURE, CHECK, GAME_OVER }

/** One-shot haptic cue; a fresh [seq] makes every event distinct. */
data class Feedback(val seq: Long, val kind: FeedbackKind)

class GameViewModel(private val store: GameStore) : ViewModel() {
    var game by mutableStateOf<GameState?>(null)
        private set
    private var undoStack: List<GameState> = emptyList()
    var canUndo by mutableStateOf(false)
        private set
    var flipped by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Int?>(null)
        private set
    var promotionChoices by mutableStateOf<List<Move>?>(null)
        private set
    var rollReveal by mutableStateOf<RollReveal?>(null)
        private set
    var showResult by mutableStateOf(true)
        private set
    var feedback by mutableStateOf<Feedback?>(null)
        private set
    private var feedbackSeq = 0L

    /** The latest hint, shown until the position changes. */
    var hint by mutableStateOf<Hint?>(null)
        private set
    var thinking by mutableStateOf(false)
        private set
    private val hintScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var hintJob: Job? = null
    private var hintRequest = 0L

    init {
        store.load()?.let { data ->
            game = data.game
            undoStack = data.game?.let { g -> if (data.history.isNotEmpty()) History.expand(g, data.history) else data.undo }.orEmpty()
            canUndo = undoStack.isNotEmpty()
            flipped = data.flipped
        }
    }

    // ----------------------------------------------------------- derived helpers

    val targets: List<Move>
        get() {
            val g = game ?: return emptyList()
            val s = selected ?: return emptyList()
            return Rules.legalMovesFrom(g, s)
        }

    // ----------------------------------------------------------- actions

    fun newGame(mode: Mode, white: String, black: String, hints: Int = 3) {
        clearHint()
        game = Rules.newGame(mode, white, black, hints = hints)
        undoStack = emptyList()
        selected = null
        flipped = false
        showResult = true
        rollReveal = null
        persist()
    }

    fun closeGame() {
        clearHint()
        game = null
        undoStack = emptyList()
        selected = null
        rollReveal = null
        persist()
    }

    fun onSquareTapped(sq: Int) {
        val g = game ?: return
        if (g.result != null || g.pendingRoll != null || g.pendingPerk != null) return
        val sel = selected
        if (sel != null) {
            // Castling can also be entered by tapping the rook (needed in Chess960).
            val moves = Rules.legalMovesFrom(g, sel).filter { it.to == sq || (it.castle && it.rookFrom == sq) }
            when {
                moves.size > 1 && moves.all { it.promotion != null } -> { promotionChoices = moves; return }
                moves.isNotEmpty() -> { commit(Rules.play(g, moves.first())); return }
            }
        }
        val p = g.board[sq]
        selected = if (p != null && sq != sel) sq else g.bonusSquare
    }

    fun choosePromotion(move: Move?) {
        val g = game
        promotionChoices = null
        if (move != null && g != null) commit(Rules.play(g, move))
    }

    fun absolveSelected() {
        val g = game ?: return
        val s = selected ?: return
        if (Rules.absolverFor(g, s) != null) commit(Rules.absolve(g, s))
    }

    /** [roll] null = let the app roll. */
    fun roll(roll: Int?) {
        val g = game ?: return
        val at = g.pendingRoll ?: return
        val value = roll ?: Random.nextInt(1, 7)
        val p = g.board[at] ?: return
        rollReveal = RollReveal(value, Rules.moodForRoll(value), p.name, p.kind, p.side, animated = roll == null)
        commit(Rules.applyRoll(g, value), haptics = false)
    }

    fun dismissRoll() { rollReveal = null }

    fun choosePerk(perk: Perk) {
        val g = game ?: return
        if (g.pendingPerk == null || perk !in g.perkOffer) return
        commit(Rules.choosePerk(g, perk))
    }

    /** Thinks in the background; counts against the player's hints only when one is found. */
    fun requestHint() {
        val g = game ?: return
        if (thinking || g.result != null || g.pendingRoll != null || g.hintsLeft(g.toMove) <= 0) return
        thinking = true
        val request = ++hintRequest
        hintJob = hintScope.launch {
            val found = runCatching { Engine.bestAction(g, budgetMs = HINT_MS) }.getOrNull()
            // Ignore a search that finished after the position changed or the hint was cleared.
            if (request != hintRequest) return@launch
            thinking = false
            if (game !== g || found == null) return@launch
            hint = found
            game = if (g.toMove == Side.WHITE) g.copy(whiteHints = g.whiteHints + 1) else g.copy(blackHints = g.blackHints + 1)
            persist()
        }
    }

    fun clearHint() {
        hintRequest++
        hintJob?.cancel()
        hintJob = null
        hint = null
        thinking = false
    }

    override fun onCleared() {
        hintScope.cancel()
    }

    fun resign(side: Side) { game?.let { commit(Rules.resign(it, side)) } }

    fun agreeDraw() { game?.let { commit(Rules.agreeDraw(it)) } }

    fun dismissResult() { showResult = false }

    fun undo() {
        if (undoStack.isEmpty()) return
        clearHint()
        game = undoStack.last()
        undoStack = undoStack.dropLast(1)
        selected = game?.bonusSquare
        rollReveal = null
        showResult = true
        persist()
    }

    fun flip() {
        flipped = !flipped
        persist()
    }

    private fun commit(next: GameState, haptics: Boolean = true) {
        clearHint()
        val before = game
        before?.let { undoStack = (undoStack + it).takeLast(MAX_UNDO) }
        game = next
        selected = next.bonusSquare
        showResult = true
        if (haptics || next.result != null) {
            val kind = when {
                next.result != null -> FeedbackKind.GAME_OVER
                Rules.inCheck(next) -> FeedbackKind.CHECK
                before != null && next.fallen.size > before.fallen.size -> FeedbackKind.CAPTURE
                else -> FeedbackKind.MOVE
            }
            feedback = Feedback(++feedbackSeq, kind)
        }
        persist()
    }

    private fun persist() {
        canUndo = undoStack.isNotEmpty()
        store.save(game, undoStack, flipped)
    }

    private companion object {
        const val MAX_UNDO = 150
        const val HINT_MS = 1800L
    }
}
