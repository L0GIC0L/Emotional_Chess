package com.emotionalchess.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Move
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

@Serializable
private data class SaveData(
    val game: GameState? = null,
    val undo: List<GameState> = emptyList(),
    val flipped: Boolean = false,
)

data class RollReveal(val roll: Int, val mood: Mood, val pieceName: String)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val json = Json { ignoreUnknownKeys = true }
    private val saveFile = File(app.filesDir, "game.json")
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

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

    init {
        runCatching { json.decodeFromString<SaveData>(saveFile.readText()) }.getOrNull()?.let {
            game = it.game
            undoStack = it.undo
            canUndo = undoStack.isNotEmpty()
            flipped = it.flipped
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

    fun newGame(mode: Mode, white: String, black: String) {
        game = Rules.newGame(mode, white, black)
        undoStack = emptyList()
        selected = null
        flipped = false
        showResult = true
        persist()
    }

    fun closeGame() {
        game = null
        undoStack = emptyList()
        selected = null
        persist()
    }

    fun onSquareTapped(sq: Int) {
        val g = game ?: return
        if (g.result != null || g.pendingRoll != null) return
        val sel = selected
        if (sel != null) {
            val moves = Rules.legalMovesFrom(g, sel).filter { it.to == sq }
            when {
                moves.size > 1 -> { promotionChoices = moves; return }
                moves.size == 1 -> { commit(Rules.play(g, moves.first())); return }
            }
        }
        val p = g.board[sq]
        selected = if (p != null && sq != sel) sq else null
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
        val name = g.board[at]?.name ?: "Piece"
        commit(Rules.applyRoll(g, value))
        rollReveal = RollReveal(value, Rules.moodForRoll(value), name)
    }

    fun dismissRoll() { rollReveal = null }

    fun resign(side: Side) { game?.let { commit(Rules.resign(it, side)) } }

    fun agreeDraw() { game?.let { commit(Rules.agreeDraw(it)) } }

    fun dismissResult() { showResult = false }

    fun undo() {
        if (undoStack.isEmpty()) return
        game = undoStack.last()
        undoStack = undoStack.dropLast(1)
        selected = null
        rollReveal = null
        showResult = true
        persist()
    }

    fun flip() {
        flipped = !flipped
        persist()
    }

    private fun commit(next: GameState) {
        game?.let { undoStack = (undoStack + it).takeLast(MAX_UNDO) }
        game = next
        selected = next.bonusSquare
        showResult = true
        persist()
    }

    private fun persist() {
        canUndo = undoStack.isNotEmpty()
        val data = SaveData(game, undoStack, flipped)
        viewModelScope.launch(io) {
            runCatching { saveFile.writeText(json.encodeToString(SaveData.serializer(), data)) }
        }
    }

    private companion object {
        const val MAX_UNDO = 150
    }
}
