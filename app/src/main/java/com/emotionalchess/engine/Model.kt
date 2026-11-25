package com.emotionalchess.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Side {
    WHITE, BLACK;

    val other: Side get() = if (this == WHITE) BLACK else WHITE
    val label: String get() = if (this == WHITE) "White" else "Black"
}

@Serializable
enum class Kind(val letter: String, val label: String) {
    KING("K", "King"),
    QUEEN("Q", "Queen"),
    ROOK("R", "Rook"),
    BISHOP("B", "Bishop"),
    KNIGHT("N", "Knight"),
    PAWN("", "Pawn"),
}

/**
 * CALM        – has never captured (Emotional mode: has not rolled yet).
 * UNAFFECTED  – rolled 4–5, or was absolved. Behaves normally forever (Emotional mode only).
 * GUILTY      – Classic: remorseful. Emotional: guilt-ridden (rolled 2–3). Cannot capture or give check.
 * DEVASTATED  – rolled 1. Frozen, gives no check, leaves the board after 3 of its owner's turns.
 * PSYCHOPATH  – rolled 6. Every later capture earns one mandatory extra move.
 */
@Serializable
enum class Mood { CALM, UNAFFECTED, GUILTY, DEVASTATED, PSYCHOPATH }

@Serializable
enum class Mode(val title: String) {
    EMOTIONAL("Emotional (dice)"),
    CLASSIC("Classic Remorse"),
}

@Serializable
data class Piece(
    val side: Side,
    val kind: Kind,
    val mood: Mood = Mood.CALM,
    /** Owner turns left before a devastated piece leaves the board. */
    val countdown: Int = 0,
    /** Devastated during the current turn; the countdown starts on the owner's next turn. */
    val fresh: Boolean = false,
    val moved: Boolean = false,
) {
    val frozen: Boolean get() = mood == Mood.DEVASTATED
    /** Guilty and devastated pieces cannot capture, and therefore give no check. */
    val canCapture: Boolean get() = mood != Mood.GUILTY && mood != Mood.DEVASTATED
    val name: String get() = "${side.label} ${kind.label}"
}

@Serializable
data class Move(
    val from: Int,
    val to: Int,
    val promotion: Kind? = null,
    val castle: Boolean = false,
    val enPassant: Boolean = false,
)

@Serializable
data class GameResult(val winner: Side?, val reason: String)

@Serializable
data class GameState(
    val mode: Mode,
    val whiteName: String = "White",
    val blackName: String = "Black",
    /** Index = rank * 8 + file; a1 = 0, h8 = 63. */
    val board: List<Piece?>,
    val toMove: Side = Side.WHITE,
    val epSquare: Int? = null,
    val halfmoveClock: Int = 0,
    val fullmove: Int = 1,
    /** Square of a piece waiting for its die roll; the turn is paused until the roll is entered. */
    val pendingRoll: Int? = null,
    /** Square of a psychopath that must make its bonus move. */
    val bonusSquare: Int? = null,
    val lastMove: Move? = null,
    val result: GameResult? = null,
    val log: List<String> = emptyList(),
    val positions: List<String> = emptyList(),
) {
    fun playerName(side: Side): String = if (side == Side.WHITE) whiteName else blackName
}
