package com.emotionalchess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private fun sq(name: String) = Rules.sq(name[0] - 'a', name[1] - '1')

    private fun position(mode: Mode, toMove: Side, vararg pieces: Pair<String, Piece>): GameState {
        val board = MutableList<Piece?>(64) { null }
        for ((s, p) in pieces) board[sq(s)] = p
        return GameState(mode = mode, board = board, toMove = toMove)
    }

    private fun w(k: Kind) = Piece(Side.WHITE, k, moved = true)
    private fun b(k: Kind) = Piece(Side.BLACK, k, moved = true)

    private fun move(hint: Hint?) = (hint?.action as? Action.Play)?.move

    @Test
    fun findsBackRankMate() {
        val g = position(
            Mode.STANDARD, Side.WHITE,
            "g1" to w(Kind.KING), "a1" to w(Kind.ROOK),
            "g8" to b(Kind.KING), "f7" to b(Kind.PAWN), "g7" to b(Kind.PAWN), "h7" to b(Kind.PAWN),
        )
        val m = move(Engine.bestAction(g, budgetMs = 3000))
        assertEquals(sq("a1"), m?.from)
        assertEquals(sq("a8"), m?.to)
    }

    @Test
    fun takesAFreeQueen() {
        val g = position(
            Mode.STANDARD, Side.WHITE,
            "a1" to w(Kind.KING), "c3" to w(Kind.KNIGHT), "h8" to b(Kind.KING), "d5" to b(Kind.QUEEN),
        )
        val m = move(Engine.bestAction(g, budgetMs = 3000))
        assertEquals(sq("d5"), m?.to)
    }

    @Test
    fun doesNotHangTheQueen() {
        val g = position(
            Mode.STANDARD, Side.WHITE,
            "a1" to w(Kind.KING), "d1" to w(Kind.QUEEN), "h8" to b(Kind.KING),
            "d5" to b(Kind.PAWN), "e6" to b(Kind.PAWN),
        )
        val m = move(Engine.bestAction(g, budgetMs = 3000))
        assertNotNull(m)
        assertNotEquals("Qxd5 loses the queen to exd5", sq("d5"), m!!.to)
    }

    @Test
    fun walksOntoTheHill() {
        val g = position(Mode.KING_OF_THE_HILL, Side.WHITE, "e3" to w(Kind.KING), "a8" to b(Kind.KING), "h1" to w(Kind.ROOK))
        val m = move(Engine.bestAction(g, budgetMs = 3000))
        assertTrue(m!!.to in Rules.HILL)
    }

    @Test
    fun picksAPerkWhenOneIsPending() {
        var g = Rules.newGame(Mode.ROGUELIKE, seed = 3)
        for ((f, t) in listOf("e2" to "e4", "d7" to "d5", "e4" to "d5")) {
            g = Rules.play(g, Rules.legalMovesFrom(g, sq(f)).first { it.to == sq(t) })
        }
        val hint = Engine.bestAction(g, budgetMs = 1000)
        assertTrue(hint?.action is Action.Pick)
    }

    @Test
    fun givesALegalHintInEveryMode() {
        for (mode in Mode.entries) {
            val g = Rules.newGame(mode, seed = 11)
            val started = System.nanoTime()
            val hint = Engine.bestAction(g, budgetMs = 400)
            val ms = (System.nanoTime() - started) / 1_000_000
            assertNotNull(mode.name, hint)
            assertTrue("$mode took $ms ms", ms < 2000)
            val m = move(hint)!!
            assertTrue("$mode hint ${hint!!.text} is legal", m in Rules.legalMovesFrom(g, m.from))
            println("$mode: ${hint.text} (depth ${hint.depth}, $ms ms)")
        }
    }
}
