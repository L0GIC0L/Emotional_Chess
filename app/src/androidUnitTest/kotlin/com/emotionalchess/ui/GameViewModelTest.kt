package com.emotionalchess.ui

import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GameViewModelTest {
    private fun sq(name: String) = Rules.sq(name[0] - 'a', name[1] - '1')

    private class MemoryStore(var data: SaveData? = null) : GameStore {
        override fun load() = data
        override fun save(game: GameState?, undo: List<GameState>, flipped: Boolean) {
            data = SaveData(game, undo = undo, flipped = flipped)
        }
    }

    private fun GameViewModel.tap(from: String, to: String) {
        onSquareTapped(sq(from))
        onSquareTapped(sq(to))
    }

    @Test
    fun tapPieceThenSquareMoves() {
        val vm = GameViewModel(MemoryStore(), uiContext = Dispatchers.Unconfined)
        vm.newGame(Mode.EMOTIONAL, "Ada", "Grace")
        vm.tap("e2", "e4")
        val g = vm.game!!
        assertNotNull(g.board[sq("e4")])
        assertEquals(Side.BLACK, g.toMove)
        assertEquals(FeedbackKind.MOVE, vm.feedback?.kind)
        assertTrue(vm.canUndo)
    }

    @Test
    fun captureRollRevealAndUndo() {
        val vm = GameViewModel(MemoryStore(), uiContext = Dispatchers.Unconfined)
        vm.newGame(Mode.EMOTIONAL, "", "")
        vm.tap("e2", "e4"); vm.tap("d7", "d5"); vm.tap("e4", "d5")
        assertEquals(sq("d5"), vm.game!!.pendingRoll)
        assertEquals(FeedbackKind.CAPTURE, vm.feedback?.kind)

        vm.roll(6)
        val reveal = vm.rollReveal!!
        assertEquals(Mood.PSYCHOPATH, reveal.mood)
        assertFalse("a typed-in roll is revealed without the tumble", reveal.animated)
        assertNull(vm.game!!.pendingRoll)
        assertEquals(Mood.PSYCHOPATH, vm.game!!.board[sq("d5")]!!.mood)

        vm.dismissRoll()
        assertNull(vm.rollReveal)

        vm.undo()
        assertEquals("undo goes back to the pending roll", sq("d5"), vm.game!!.pendingRoll)
    }

    @Test
    fun appRollIsAnimated() {
        val vm = GameViewModel(MemoryStore(), uiContext = Dispatchers.Unconfined)
        vm.newGame(Mode.EMOTIONAL, "", "")
        vm.tap("e2", "e4"); vm.tap("d7", "d5"); vm.tap("e4", "d5")
        vm.roll(null)
        assertTrue(vm.rollReveal!!.animated)
        assertTrue(vm.rollReveal!!.roll in 1..6)
    }

    @Test
    fun fileStoreRoundTripKeepsUndoHistory() {
        val dir = Files.createTempDirectory("ec").toFile()
        val file = File(dir, "game.json")
        val vm = GameViewModel(JsonGameStore(FileStorage(file)), uiContext = Dispatchers.Unconfined)
        vm.newGame(Mode.CLASSIC, "Ada", "Grace")
        val initial = vm.game!!
        vm.tap("e2", "e4"); vm.tap("d7", "d5"); vm.tap("e4", "d5")
        vm.flip()

        // Saves happen in the background; wait for the last one to land.
        val expected = vm.game!!
        val deadline = System.currentTimeMillis() + 5000
        var restored: GameViewModel
        do {
            Thread.sleep(50)
            restored = GameViewModel(JsonGameStore(FileStorage(file)), uiContext = Dispatchers.Unconfined)
        } while ((restored.game != expected || !restored.flipped) && System.currentTimeMillis() < deadline)

        assertEquals(expected, restored.game)
        assertTrue(restored.flipped)
        restored.undo(); restored.undo(); restored.undo()
        assertEquals(initial, restored.game)
        assertFalse(restored.canUndo)
        dir.deleteRecursively()
    }

    @Test
    fun oldSaveFormatStillLoads() {
        val dir = Files.createTempDirectory("ec").toFile()
        val file = File(dir, "game.json")
        val start = Rules.newGame(Mode.EMOTIONAL)
        val next = Rules.play(start, Rules.legalMovesFrom(start, sq("e2")).first { it.to == sq("e4") })
        file.writeText(Json.encodeToString(SaveData.serializer(), SaveData(game = next, undo = listOf(start))))
        val vm = GameViewModel(JsonGameStore(FileStorage(file)), uiContext = Dispatchers.Unconfined)
        assertEquals(next, vm.game)
        vm.undo()
        assertEquals(start, vm.game)
        dir.deleteRecursively()
    }

    private fun GameViewModel.awaitHint() {
        val deadline = System.currentTimeMillis() + 10_000
        while (thinking && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    @Test
    fun hintsAreFoundAndCounted() {
        val vm = GameViewModel(MemoryStore(), uiContext = Dispatchers.Unconfined)
        vm.newGame(Mode.STANDARD, "", "", hints = 1)
        vm.requestHint()
        vm.awaitHint()
        assertNotNull(vm.hint)
        assertEquals(1, vm.game!!.whiteHints)
        assertEquals(0, vm.game!!.hintsLeft(Side.WHITE))

        vm.requestHint() // none left
        assertFalse(vm.thinking)

        val m = (vm.hint!!.action as com.emotionalchess.engine.Action.Play).move
        vm.onSquareTapped(m.from); vm.onSquareTapped(m.to)
        assertNull("a move clears the hint", vm.hint)
        assertEquals(Side.BLACK, vm.game!!.toMove)
    }

    @Test
    fun hintsOffMeansNoHints() {
        val vm = GameViewModel(MemoryStore(), uiContext = Dispatchers.Unconfined)
        vm.newGame(Mode.STANDARD, "", "", hints = 0)
        vm.requestHint()
        assertFalse(vm.thinking)
        assertNull(vm.hint)
    }

    @Test
    fun chess960CastlesByTappingTheRook() {
        val board = MutableList<com.emotionalchess.engine.Piece?>(64) { null }
        board[sq("g1")] = com.emotionalchess.engine.Piece(Side.WHITE, com.emotionalchess.engine.Kind.KING, id = 1)
        board[sq("h1")] = com.emotionalchess.engine.Piece(Side.WHITE, com.emotionalchess.engine.Kind.ROOK, id = 2)
        board[sq("g8")] = com.emotionalchess.engine.Piece(Side.BLACK, com.emotionalchess.engine.Kind.KING, id = 3)
        val vm = GameViewModel(MemoryStore(SaveData(game = GameState(Mode.CHESS960, board = board))), uiContext = Dispatchers.Unconfined)
        vm.tap("g1", "h1")
        assertEquals(com.emotionalchess.engine.Kind.ROOK, vm.game!!.board[sq("f1")]?.kind)
        assertEquals(com.emotionalchess.engine.Kind.KING, vm.game!!.board[sq("g1")]?.kind)
    }

    @Test
    fun roguelikePerkPick() {
        val vm = GameViewModel(MemoryStore(), uiContext = Dispatchers.Unconfined)
        vm.newGame(Mode.ROGUELIKE, "", "")
        vm.tap("e2", "e4"); vm.tap("d7", "d5"); vm.tap("e4", "d5")
        val offer = vm.game!!.perkOffer
        assertEquals(3, offer.size)
        vm.choosePerk(offer[1])
        assertEquals(listOf(offer[1]), vm.game!!.board[sq("d5")]!!.perks)
        assertNull(vm.game!!.pendingPerk)
    }
}
