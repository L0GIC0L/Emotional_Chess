package com.emotionalchess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private fun sq(name: String) = Rules.sq(name[0] - 'a', name[1] - '1')

    private fun GameState.move(from: String, to: String, promo: Kind? = null): GameState {
        val m = Rules.legalMovesFrom(this, sq(from)).firstOrNull { it.to == sq(to) && it.promotion == promo }
            ?: error("No legal move $from-$to\n${log.joinToString("\n")}")
        return Rules.play(this, m)
    }

    /** Empty board with the given pieces. */
    private fun position(mode: Mode, toMove: Side = Side.WHITE, vararg pieces: Pair<String, Piece>): GameState {
        val board = MutableList<Piece?>(64) { null }
        for ((s, p) in pieces) board[sq(s)] = p
        return GameState(mode = mode, board = board, toMove = toMove)
    }

    private fun w(k: Kind, mood: Mood = Mood.CALM, moved: Boolean = true) = Piece(Side.WHITE, k, mood, moved = moved)
    private fun b(k: Kind, mood: Mood = Mood.CALM, moved: Boolean = true) = Piece(Side.BLACK, k, mood, moved = moved)

    @Test
    fun openingHasTwentyMoves() {
        val g = Rules.newGame(Mode.EMOTIONAL)
        assertEquals(20, Rules.allLegalMoves(g).size)
    }

    @Test
    fun classicCaptureMakesRemorsefulAndBlocksFurtherCaptures() {
        var g = Rules.newGame(Mode.CLASSIC)
        g = g.move("e2", "e4").move("d7", "d5").move("e4", "d5")
        assertEquals(Mood.GUILTY, g.board[sq("d5")]!!.mood)
        g = g.move("c7", "c6") // offers c6 pawn
        val captures = Rules.legalMovesFrom(g, sq("d5")).filter { g.board[it.to] != null }
        assertTrue("remorseful pawn may not capture", captures.isEmpty())
    }

    @Test
    fun classicBishopMovingAdjacentForgives() {
        var g = position(
            Mode.CLASSIC, Side.WHITE,
            "e1" to w(Kind.KING), "h8" to b(Kind.KING),
            "d4" to w(Kind.KNIGHT, Mood.GUILTY), "f1" to w(Kind.BISHOP),
        )
        g = g.move("f1", "e2") // not adjacent to d4
        assertEquals(Mood.GUILTY, g.board[sq("d4")]!!.mood)
        g = g.move("h8", "g8").move("e2", "d3") // adjacent
        assertEquals(Mood.CALM, g.board[sq("d4")]!!.mood)
    }

    @Test
    fun guiltyPieceGivesNoCheck() {
        val g = position(
            Mode.EMOTIONAL, Side.BLACK,
            "e1" to w(Kind.KING), "a8" to b(Kind.KING),
            "a1" to w(Kind.ROOK, Mood.GUILTY),
        )
        assertFalse(Rules.inCheck(g))
        assertNull(g.result)
    }

    @Test
    fun firstCaptureRequiresRoll() {
        var g = Rules.newGame(Mode.EMOTIONAL)
        g = g.move("e2", "e4").move("d7", "d5").move("e4", "d5")
        assertEquals(sq("d5"), g.pendingRoll)
        assertEquals(Side.WHITE, g.toMove)
        assertTrue(Rules.allLegalMoves(g).isEmpty())
        g = Rules.applyRoll(g, 2)
        assertEquals(Mood.GUILTY, g.board[sq("d5")]!!.mood)
        assertEquals(Side.BLACK, g.toMove)
    }

    @Test
    fun devastatedPieceLeavesAfterThreeOwnerTurns() {
        var g = Rules.newGame(Mode.EMOTIONAL)
        g = g.move("e2", "e4").move("d7", "d5").move("e4", "d5")
        g = Rules.applyRoll(g, 1)
        val d5 = sq("d5")
        assertTrue(Rules.legalMovesFrom(g.copy(toMove = Side.WHITE), d5).isEmpty())
        val whiteMoves = listOf("g1" to "f3", "f3" to "g1", "g1" to "f3")
        val blackMoves = listOf("g8" to "f6", "f6" to "g8", "g8" to "f6")
        for (i in 0 until 3) {
            g = g.move(blackMoves[i].first, blackMoves[i].second)
            assertNotNull("still there before white's turn ${i + 1}", g.board[d5])
            g = g.move(whiteMoves[i].first, whiteMoves[i].second)
        }
        assertNull("gone after white's third turn", g.board[d5])
    }

    @Test
    fun bishopAbsolvesAsItsOwnTurn() {
        var g = position(
            Mode.EMOTIONAL, Side.WHITE,
            "e1" to w(Kind.KING), "h8" to b(Kind.KING),
            "d4" to w(Kind.KNIGHT, Mood.DEVASTATED).copy(countdown = 2), "c3" to w(Kind.BISHOP),
        )
        assertEquals(sq("c3"), Rules.absolverFor(g, sq("d4")))
        g = Rules.absolve(g, sq("d4"))
        assertEquals(Mood.UNAFFECTED, g.board[sq("d4")]!!.mood)
        assertEquals(Side.BLACK, g.toMove)
    }

    @Test
    fun bishopCannotAbsolveItself() {
        val g = position(
            Mode.EMOTIONAL, Side.WHITE,
            "e1" to w(Kind.KING), "h8" to b(Kind.KING),
            "c3" to w(Kind.BISHOP, Mood.GUILTY),
        )
        assertNull(Rules.absolverFor(g, sq("c3")))
    }

    @Test
    fun psychopathGetsBonusMoveAndCanTakeKing() {
        var g = position(
            Mode.EMOTIONAL, Side.WHITE,
            "a1" to w(Kind.KING), "e8" to b(Kind.KING),
            "d1" to w(Kind.ROOK, Mood.PSYCHOPATH), "d5" to b(Kind.PAWN),
        )
        g = g.move("d1", "d5") // capture, now must move again
        assertEquals(sq("d5"), g.bonusSquare)
        assertEquals(Side.WHITE, g.toMove)
        g = g.move("d5", "d8") // non-capturing bonus move gives check
        assertEquals(Side.BLACK, g.toMove)
        assertNull(g.bonusSquare)
    }

    @Test
    fun psychopathBonusCaptureOfKingWins() {
        var g = position(
            Mode.EMOTIONAL, Side.WHITE,
            "a1" to w(Kind.KING), "e8" to b(Kind.KING),
            "e1" to w(Kind.ROOK, Mood.PSYCHOPATH), "e5" to b(Kind.PAWN),
        )
        g = g.move("e1", "e5")
        g = g.move("e5", "e8")
        assertEquals(Side.WHITE, g.result?.winner)
    }

    @Test
    fun foolsMateIsCheckmate() {
        var g = Rules.newGame(Mode.EMOTIONAL)
        g = g.move("f2", "f3").move("e7", "e5").move("g2", "g4").move("d8", "h4")
        assertEquals(Side.BLACK, g.result?.winner)
        assertEquals("Checkmate", g.result?.reason)
    }

    @Test
    fun castlingWorks() {
        var g = position(
            Mode.EMOTIONAL, Side.WHITE,
            "e1" to w(Kind.KING, moved = false), "h1" to w(Kind.ROOK, moved = false), "e8" to b(Kind.KING),
        )
        g = g.move("e1", "g1")
        assertEquals(Kind.KING, g.board[sq("g1")]!!.kind)
        assertEquals(Kind.ROOK, g.board[sq("f1")]!!.kind)
    }

    @Test
    fun piecesHaveStableUniqueIds() {
        var g = Rules.newGame(Mode.EMOTIONAL)
        assertEquals((0..31).toSet(), g.board.mapNotNull { it?.id }.toSet())
        val knight = g.board[sq("g1")]!!.id
        g = g.move("g1", "f3")
        assertEquals(knight, g.board[sq("f3")]!!.id)
    }

    @Test
    fun promotionKeepsIdentity() {
        var g = position(
            Mode.EMOTIONAL, Side.WHITE,
            "e1" to w(Kind.KING), "h8" to b(Kind.KING), "a7" to w(Kind.PAWN).copy(id = 7),
        )
        g = g.move("a7", "a8", Kind.QUEEN)
        assertEquals(Kind.QUEEN, g.board[sq("a8")]!!.kind)
        assertEquals(7, g.board[sq("a8")]!!.id)
    }

    @Test
    fun capturesRollsAndDespairAreRecorded() {
        var g = Rules.newGame(Mode.EMOTIONAL)
        g = g.move("e2", "e4").move("d7", "d5").move("e4", "d5")
        assertEquals(listOf(Fallen(Side.BLACK, Kind.PAWN)), g.fallen)
        g = Rules.applyRoll(g, 1)
        assertEquals(listOf(RollRecord(Side.WHITE, Kind.PAWN, 1)), g.rolls)
        for ((b, w) in listOf(("g8" to "f6") to ("g1" to "f3"), ("f6" to "g8") to ("f3" to "g1"), ("g8" to "f6") to ("g1" to "f3"))) {
            g = g.move(b.first, b.second).move(w.first, w.second)
        }
        assertEquals(Fallen(Side.WHITE, Kind.PAWN, despair = true), g.fallen.last())
    }

    @Test
    fun absolutionAndForgivenessAreRecorded() {
        val dice = position(
            Mode.EMOTIONAL, Side.WHITE,
            "e1" to w(Kind.KING), "h8" to b(Kind.KING),
            "d4" to w(Kind.KNIGHT, Mood.GUILTY), "c3" to w(Kind.BISHOP),
        )
        assertEquals(listOf(Absolution(Side.WHITE, Kind.KNIGHT)), Rules.absolve(dice, sq("d4")).absolutions)

        val classic = position(
            Mode.CLASSIC, Side.WHITE,
            "e1" to w(Kind.KING), "h8" to b(Kind.KING),
            "d4" to w(Kind.KNIGHT, Mood.GUILTY), "f1" to w(Kind.BISHOP),
        )
        assertEquals(listOf(Absolution(Side.WHITE, Kind.KNIGHT)), classic.move("f1", "d3").absolutions)
    }

    @Test
    fun onlyLegalMovesAreAccepted() {
        val g = Rules.newGame(Mode.CLASSIC)
        val illegal = Move(sq("e2"), sq("e5"))
        assertTrue(runCatching { Rules.play(g, illegal) }.isFailure)
    }
}
