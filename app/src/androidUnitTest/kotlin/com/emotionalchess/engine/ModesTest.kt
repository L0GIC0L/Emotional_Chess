package com.emotionalchess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ModesTest {
    private fun sq(name: String) = Rules.sq(name[0] - 'a', name[1] - '1')

    private fun GameState.move(from: String, to: String, promo: Kind? = null): GameState {
        val m = Rules.legalMovesFrom(this, sq(from)).firstOrNull { it.to == sq(to) && it.promotion == promo }
            ?: error("No legal move $from-$to\n${log.joinToString("\n")}")
        return Rules.play(this, m)
    }

    private fun GameState.has(from: String, to: String) = Rules.legalMovesFrom(this, sq(from)).any { it.to == sq(to) }

    private fun position(mode: Mode, toMove: Side, vararg pieces: Pair<String, Piece>): GameState {
        val board = MutableList<Piece?>(64) { null }
        var id = 0
        for ((s, p) in pieces) board[sq(s)] = p.copy(id = id++)
        return GameState(mode = mode, board = board, toMove = toMove)
    }

    private fun w(k: Kind, mood: Mood = Mood.CALM, moved: Boolean = true) = Piece(Side.WHITE, k, mood, moved = moved)
    private fun b(k: Kind, mood: Mood = Mood.CALM, moved: Boolean = true) = Piece(Side.BLACK, k, mood, moved = moved)

    // ------------------------------------------------------------------ standard

    @Test
    fun standardCapturesLeaveNoFeelings() {
        val g = Rules.newGame(Mode.STANDARD).move("e2", "e4").move("d7", "d5").move("e4", "d5")
        assertEquals(Mood.CALM, g.board[sq("d5")]!!.mood)
        assertNull(g.pendingRoll)
        assertEquals(Side.BLACK, g.toMove)
    }

    // ------------------------------------------------------------------ chess960

    @Test
    fun chess960SetupsAreValid() {
        val seen = mutableSetOf<List<Kind>>()
        repeat(300) {
            val rank = Rules.backRank960(Random(it))
            seen += rank
            val bishops = rank.indices.filter { i -> rank[i] == Kind.BISHOP }
            assertEquals(2, bishops.size)
            assertTrue("bishops on opposite colours", (bishops[0] + bishops[1]) % 2 == 1)
            val rooks = rank.indices.filter { i -> rank[i] == Kind.ROOK }
            val king = rank.indexOf(Kind.KING)
            assertTrue("king between rooks", king > rooks[0] && king < rooks[1])
            assertEquals(1, rank.count { k -> k == Kind.QUEEN })
            assertEquals(2, rank.count { k -> k == Kind.KNIGHT })
        }
        assertTrue("many different setups", seen.size > 100)
        assertEquals(Rules.newGame(Mode.CHESS960, seed = 42).board, Rules.newGame(Mode.CHESS960, seed = 42).board)
    }

    @Test
    fun chess960CastlingWhereTheKingStaysPut() {
        // King already on g1: castling short only moves the rook to f1.
        val g = position(
            Mode.CHESS960, Side.WHITE,
            "g1" to w(Kind.KING, moved = false), "h1" to w(Kind.ROOK, moved = false), "g8" to b(Kind.KING),
        )
        val castle = Rules.legalMovesFrom(g, sq("g1")).single { it.castle }
        assertEquals(sq("g1"), castle.to)
        assertEquals(sq("h1"), castle.rookFrom)
        val after = Rules.play(g, castle)
        assertEquals(Kind.KING, after.board[sq("g1")]!!.kind)
        assertEquals(Kind.ROOK, after.board[sq("f1")]!!.kind)
        assertNull(after.board[sq("h1")])
    }

    @Test
    fun chess960LongCastlingFromB1() {
        val g = position(
            Mode.CHESS960, Side.WHITE,
            "b1" to w(Kind.KING, moved = false), "a1" to w(Kind.ROOK, moved = false), "g8" to b(Kind.KING),
        )
        val after = Rules.play(g, Rules.legalMovesFrom(g, sq("b1")).single { it.castle })
        assertEquals(Kind.KING, after.board[sq("c1")]!!.kind)
        assertEquals(Kind.ROOK, after.board[sq("d1")]!!.kind)
    }

    @Test
    fun noCastlingThroughAttackedSquares() {
        val g = position(
            Mode.STANDARD, Side.WHITE,
            "e1" to w(Kind.KING, moved = false), "h1" to w(Kind.ROOK, moved = false),
            "e8" to b(Kind.KING), "f8" to b(Kind.ROOK),
        )
        assertTrue(Rules.legalMovesFrom(g, sq("e1")).none { it.castle })
    }

    // ------------------------------------------------------------------ king of the hill / three-check

    @Test
    fun kingOfTheHillWins() {
        val g = position(Mode.KING_OF_THE_HILL, Side.WHITE, "e3" to w(Kind.KING), "a8" to b(Kind.KING)).move("e3", "e4")
        assertEquals(Side.WHITE, g.result?.winner)
    }

    @Test
    fun thirdCheckWins() {
        val g = position(Mode.THREE_CHECK, Side.WHITE, "a1" to w(Kind.KING), "h1" to w(Kind.ROOK), "e8" to b(Kind.KING))
            .copy(whiteChecks = 2)
            .move("h1", "h8")
        assertEquals(3, g.whiteChecks)
        assertEquals(Side.WHITE, g.result?.winner)
    }

    @Test
    fun checksAreCounted() {
        val g = position(Mode.THREE_CHECK, Side.WHITE, "a1" to w(Kind.KING), "h1" to w(Kind.ROOK), "e8" to b(Kind.KING))
            .move("h1", "h8")
        assertEquals(1, g.whiteChecks)
        assertNull(g.result)
    }

    // ------------------------------------------------------------------ atomic

    @Test
    fun atomicCaptureExplodesNeighboursButNotPawns() {
        val g = position(
            Mode.ATOMIC, Side.WHITE,
            "a1" to w(Kind.KING), "d1" to w(Kind.QUEEN), "h8" to b(Kind.KING),
            "d5" to b(Kind.KNIGHT), "e6" to b(Kind.BISHOP), "c6" to b(Kind.PAWN),
        ).move("d1", "d5")
        assertNull("capturer explodes", g.board[sq("d5")])
        assertNull("neighbouring bishop explodes", g.board[sq("e6")])
        assertNotNull("pawns survive", g.board[sq("c6")])
        assertEquals(sq("d5"), g.explosion)
        assertEquals(3, g.fallen.size)
    }

    @Test
    fun atomicKingsCannotCaptureAndCannotBlowThemselvesUp() {
        val g = position(
            Mode.ATOMIC, Side.WHITE,
            "e1" to w(Kind.KING), "d8" to w(Kind.ROOK), "d2" to b(Kind.KNIGHT), "h8" to b(Kind.KING),
        )
        assertFalse("king may not capture", g.has("e1", "d2"))
        assertFalse("explosion would take our own king", g.has("d8", "d2"))
    }

    @Test
    fun atomicBlowingUpTheKingWins() {
        val g = position(
            Mode.ATOMIC, Side.WHITE,
            "a1" to w(Kind.KING), "d1" to w(Kind.ROOK), "d8" to b(Kind.KNIGHT), "e8" to b(Kind.KING),
        ).move("d1", "d8")
        assertEquals(Side.WHITE, g.result?.winner)
    }

    // ------------------------------------------------------------------ antichess

    @Test
    fun antichessCapturesAreCompulsory() {
        val g = Rules.newGame(Mode.ANTICHESS).move("e2", "e3").move("b7", "b5")
        val moves = Rules.allLegalMoves(g)
        assertEquals(1, moves.size)
        assertEquals(sq("b5"), moves.single().to)
    }

    @Test
    fun antichessLosingEverythingWins() {
        val g = position(Mode.ANTICHESS, Side.BLACK, "a3" to w(Kind.PAWN), "a8" to b(Kind.ROOK)).move("a8", "a3")
        assertEquals(Side.WHITE, g.result?.winner)
    }

    @Test
    fun antichessKingIsJustAPiece() {
        val g = position(Mode.ANTICHESS, Side.WHITE, "e4" to w(Kind.ROOK), "e8" to b(Kind.KING), "a8" to b(Kind.PAWN))
        assertTrue(g.has("e4", "e8"))
        assertFalse(Rules.inCheck(g, Side.BLACK))
    }

    // ------------------------------------------------------------------ racing kings

    @Test
    fun racingKingsSetupAndNoChecks() {
        val g = Rules.newGame(Mode.RACING_KINGS)
        assertEquals(16, g.board.count { it != null })
        assertTrue(g.board.none { it?.kind == Kind.PAWN })
        // Every legal move leaves both kings out of check.
        for (m in Rules.allLegalMoves(g)) {
            val after = Rules.play(g, m)
            assertFalse(Rules.inCheck(after, Side.BLACK))
        }
    }

    @Test
    fun racingKingsFirstToTheTopWins() {
        val g = position(Mode.RACING_KINGS, Side.WHITE, "g7" to w(Kind.KING), "a2" to b(Kind.KING)).move("g7", "g8")
        assertEquals(Side.WHITE, g.result?.winner)
    }

    @Test
    fun racingKingsBlackCanEqualise() {
        var g = position(Mode.RACING_KINGS, Side.WHITE, "g7" to w(Kind.KING), "b7" to b(Kind.KING)).move("g7", "g8")
        assertNull("Black still gets a move", g.result)
        g = g.move("b7", "b8")
        assertEquals("Both kings reached the 8th rank", g.result?.reason)
        assertNull(g.result?.winner)
    }

    // ------------------------------------------------------------------ plague

    @Test
    fun plagueCaptureInfectsAndSickPiecesSpreadIt() {
        var g = Rules.newGame(Mode.PLAGUE).move("e2", "e4").move("d7", "d5").move("e4", "d5")
        assertEquals(Mood.INFECTED, g.board[sq("d5")]!!.mood)
        g = g.move("g8", "f6")
        // The sick pawn moves to d6, next to c7, e7 (black pawns) and c7/e7/… – they catch it.
        g = g.move("d5", "d6")
        assertEquals(Mood.INFECTED, g.board[sq("c7")]!!.mood)
        assertEquals(Mood.INFECTED, g.board[sq("e7")]!!.mood)
        assertEquals("bishops are doctors", Mood.CALM, g.board[sq("c8")]!!.mood)
    }

    @Test
    fun plagueKillsUnlessABishopCures() {
        val start = position(
            Mode.PLAGUE, Side.WHITE,
            "e1" to w(Kind.KING), "h8" to b(Kind.KING), "a1" to w(Kind.ROOK),
            "d4" to w(Kind.KNIGHT, Mood.INFECTED).copy(countdown = 1),
            "c3" to w(Kind.BISHOP),
        )
        // Shuffle the rook: the knight dies at the end of this turn.
        val died = start.move("a1", "a2")
        assertNull(died.board[sq("d4")])
        assertTrue(died.fallen.last().despair)

        val cured = Rules.absolve(start, sq("d4"))
        assertEquals(Mood.IMMUNE, cured.board[sq("d4")]!!.mood)
    }

    @Test
    fun infectedKingDyingLoses() {
        val g = position(
            Mode.PLAGUE, Side.WHITE,
            "e1" to w(Kind.KING, Mood.INFECTED).copy(countdown = 1), "a1" to w(Kind.ROOK), "h8" to b(Kind.KING),
        ).move("a1", "a2")
        assertEquals(Side.BLACK, g.result?.winner)
    }

    // ------------------------------------------------------------------ roguelike

    @Test
    fun roguelikeCaptureOffersPerks() {
        var g = Rules.newGame(Mode.ROGUELIKE, seed = 7).move("e2", "e4").move("d7", "d5").move("e4", "d5")
        assertEquals(sq("d5"), g.pendingPerk)
        assertEquals(3, g.perkOffer.size)
        assertTrue(Rules.allLegalMoves(g).isEmpty())
        val perk = g.perkOffer.first()
        g = Rules.choosePerk(g, perk)
        assertEquals(listOf(perk), g.board[sq("d5")]!!.perks)
        assertEquals(Side.BLACK, g.toMove)
    }

    @Test
    fun movementPerksAddMovesAndGiveCheck() {
        val g = position(
            Mode.ROGUELIKE, Side.WHITE,
            "a1" to w(Kind.KING), "d4" to w(Kind.ROOK).copy(perks = listOf(Perk.LEAP)), "h8" to b(Kind.KING),
        )
        assertTrue("a rook that leaps", g.has("d4", "e6"))
        assertTrue("and still slides", g.has("d4", "d8"))
        val g2 = position(
            Mode.ROGUELIKE, Side.BLACK,
            "a1" to w(Kind.KING), "f6" to w(Kind.ROOK).copy(perks = listOf(Perk.LEAP)), "h7" to b(Kind.KING),
        )
        assertTrue("the leaping rook checks h7 like a knight", Rules.inCheck(g2))
    }

    @Test
    fun shieldAbsorbsOneCapture() {
        var g = position(
            Mode.ROGUELIKE, Side.WHITE,
            "a1" to w(Kind.KING), "d1" to w(Kind.ROOK), "h8" to b(Kind.KING),
            "d7" to b(Kind.KNIGHT).copy(perks = listOf(Perk.SHIELD)),
        )
        g = g.move("d1", "d7")
        assertEquals("the attacker stays home", Kind.ROOK, g.board[sq("d1")]?.kind)
        assertEquals("the knight survives", emptyList<Perk>(), g.board[sq("d7")]!!.perks)
        assertNull(g.pendingPerk)
    }

    @Test
    fun kingsNeverLevelUpAndQueensOnlyGetUsefulPerks() {
        assertEquals(listOf(Perk.LEAP, Perk.SHIELD), Rules.perkOptions(Piece(Side.WHITE, Kind.QUEEN)))
        val g = position(Mode.ROGUELIKE, Side.WHITE, "e1" to w(Kind.KING), "e2" to b(Kind.PAWN), "h8" to b(Kind.KING))
            .move("e1", "e2")
        assertNull(g.pendingPerk)
    }
}
