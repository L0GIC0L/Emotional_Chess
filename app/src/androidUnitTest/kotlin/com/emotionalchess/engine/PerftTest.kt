package com.emotionalchess.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Counts leaf positions against well-known published totals, which catches move-generation bugs. */
class PerftTest {
    private fun fen(fen: String): GameState {
        val parts = fen.split(" ")
        val board = MutableList<Piece?>(64) { null }
        val rights = parts.getOrElse(2) { "-" }
        parts[0].split("/").forEachIndexed { i, row ->
            val rank = 7 - i
            var file = 0
            for (c in row) {
                if (c.isDigit()) { file += c - '0'; continue }
                val side = if (c.isUpperCase()) Side.WHITE else Side.BLACK
                val kind = when (c.lowercaseChar()) {
                    'k' -> Kind.KING; 'q' -> Kind.QUEEN; 'r' -> Kind.ROOK
                    'b' -> Kind.BISHOP; 'n' -> Kind.KNIGHT; else -> Kind.PAWN
                }
                val s = Rules.sq(file, rank)
                val unmoved = when {
                    kind == Kind.KING -> rights.any { r -> (r.isUpperCase() == (side == Side.WHITE)) && r != '-' }
                    kind == Kind.ROOK && side == Side.WHITE -> (s == 7 && 'K' in rights) || (s == 0 && 'Q' in rights)
                    kind == Kind.ROOK -> (s == 63 && 'k' in rights) || (s == 56 && 'q' in rights)
                    else -> true
                }
                board[s] = Piece(side, kind, moved = !unmoved)
                file++
            }
        }
        val ep = parts.getOrElse(3) { "-" }.let { if (it == "-") null else Rules.sq(it[0] - 'a', it[1] - '1') }
        return GameState(Mode.STANDARD, board = board, toMove = if (parts[1] == "w") Side.WHITE else Side.BLACK, epSquare = ep)
    }

    private fun perft(s: GameState, depth: Int): Long {
        val moves = Rules.allLegalMoves(s)
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(Rules.play(s, it, fast = true), depth - 1) }
    }

    @Test fun startPosition() = assertEquals(8902L, perft(Rules.newGame(Mode.STANDARD), 3))

    @Test fun kiwipete() = assertEquals(
        2039L, perft(fen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -"), 2),
    )

    @Test fun enPassantAndPins() = assertEquals(2812L, perft(fen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - -"), 3))

    @Test fun promotionsAndCastling() = assertEquals(
        9467L, perft(fen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"), 3),
    )

    @Test fun promotionWithCheck() = assertEquals(
        1486L, perft(fen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"), 2),
    )
}
