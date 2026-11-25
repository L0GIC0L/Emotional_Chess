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
 * CALM        – nothing has happened to it.
 * UNAFFECTED  – rolled 4–5, or was absolved. Behaves normally forever (dice remorse).
 * GUILTY      – Classic: remorseful. Dice: guilt-ridden (rolled 2–3). Cannot capture or give check.
 * DEVASTATED  – rolled 1. Frozen, gives no check, leaves the board after 3 of its owner's turns.
 * PSYCHOPATH  – rolled 6. Every later capture earns one mandatory extra move.
 * INFECTED    – Plague: dies after [Rules.INCUBATION_TURNS] of its owner's turns unless cured.
 * IMMUNE      – Plague: was cured and can never catch the plague again.
 */
@Serializable
enum class Mood { CALM, UNAFFECTED, GUILTY, DEVASTATED, PSYCHOPATH, INFECTED, IMMUNE }

enum class ModeGroup(val title: String) { CHESS("Chess"), FEELINGS("Feelings & mayhem") }

/** Every mode plays on a standard board with one standard set. */
@Serializable
enum class Mode(val title: String, val emoji: String, val group: ModeGroup, val tagline: String) {
    STANDARD("Standard", "♟️", ModeGroup.CHESS, "Plain chess. The app keeps score and spots check and mate."),
    CHESS960("Chess960", "🔀", ModeGroup.CHESS, "Fischer Random: the back rank is shuffled. Set it up as shown."),
    KING_OF_THE_HILL("King of the Hill", "⛰️", ModeGroup.CHESS, "Also win by walking your king onto one of the four centre squares."),
    THREE_CHECK("Three-check", "⚡", ModeGroup.CHESS, "Also win by giving check three times."),
    ATOMIC("Atomic", "💥", ModeGroup.CHESS, "Captures explode, taking out every piece next to them except pawns."),
    ANTICHESS("Antichess", "🙃", ModeGroup.CHESS, "Captures are forced. Lose all your pieces to win."),
    RACING_KINGS("Racing Kings", "🏁", ModeGroup.CHESS, "No checks allowed. First king to the far rank wins."),
    EMOTIONAL("Remorse: dice", "🎲", ModeGroup.FEELINGS, "A first capture rolls a die: devastated, guilty, unaffected… or psychopath."),
    CLASSIC("Remorse: classic", "✝️", ModeGroup.FEELINGS, "Every capture makes a piece remorseful until a bishop forgives it."),
    PLAGUE("Plague", "🦠", ModeGroup.FEELINGS, "Capturing infects. The sick spread it and die unless a bishop cures them."),
    ROGUELIKE("Roguelike", "⭐", ModeGroup.FEELINGS, "Every capture levels a piece up: pick one of three perks."),
    ;

    val hasCheck: Boolean get() = this != ANTICHESS
    val canCastle: Boolean get() = this != ANTICHESS && this != RACING_KINGS
}

/** Roguelike upgrades. A movement perk adds the moves of [movesLike]. Kings never get perks. */
@Serializable
enum class Perk(val emoji: String, val label: String, val text: String, val movesLike: Kind?) {
    LEAP("🐴", "Leap", "Can also jump like a knight.", Kind.KNIGHT),
    CHARGE("🏰", "Charge", "Can also move like a rook.", Kind.ROOK),
    SLANT("⛪", "Slant", "Can also move like a bishop.", Kind.BISHOP),
    STEP("👣", "Step", "Can also step one square in any direction.", Kind.KING),
    SHIELD("🛡️", "Shield", "The next attack on it only breaks the shield, and the attacker stays where it was.", null),
}

@Serializable
data class Piece(
    val side: Side,
    val kind: Kind,
    val mood: Mood = Mood.CALM,
    /** Owner turns left before a devastated or infected piece leaves the board. */
    val countdown: Int = 0,
    /** Afflicted during its owner's current turn; the countdown starts on the owner's next turn. */
    val fresh: Boolean = false,
    val moved: Boolean = false,
    /** Stable identity so the board can animate a piece as it moves. -1 = unknown (old saves). */
    val id: Int = -1,
    val perks: List<Perk> = emptyList(),
) {
    val frozen: Boolean get() = mood == Mood.DEVASTATED
    /** Guilty and devastated pieces cannot capture, and therefore give no check. */
    val canCapture: Boolean get() = mood != Mood.GUILTY && mood != Mood.DEVASTATED
    /** Devastated and infected pieces leave the board when their countdown runs out. */
    val dying: Boolean get() = mood == Mood.DEVASTATED || mood == Mood.INFECTED
    val name: String get() = "${side.label} ${kind.label}"
}

@Serializable
data class Move(
    val from: Int,
    val to: Int,
    val promotion: Kind? = null,
    val castle: Boolean = false,
    val enPassant: Boolean = false,
    /** Castling only: the rook's square (needed for Chess960). Null = the standard corner rook. */
    val rookFrom: Int? = null,
)

@Serializable
data class GameResult(val winner: Side?, val reason: String)

/** A piece that left the board: captured, exploded, or died of despair or plague. [side] is its owner. */
@Serializable
data class Fallen(val side: Side, val kind: Kind, val despair: Boolean = false)

@Serializable
data class RollRecord(val side: Side, val kind: Kind, val roll: Int)

/** A guilty, devastated or infected piece that was absolved, forgiven or cured. */
@Serializable
data class Absolution(val side: Side, val kind: Kind)

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
    /** Roguelike: square of a piece choosing a perk from [perkOffer]; the turn is paused meanwhile. */
    val pendingPerk: Int? = null,
    val perkOffer: List<Perk> = emptyList(),
    val lastMove: Move? = null,
    /** Atomic: square of the last explosion, for the animation. */
    val explosion: Int? = null,
    val result: GameResult? = null,
    val log: List<String> = emptyList(),
    val positions: List<String> = emptyList(),
    val fallen: List<Fallen> = emptyList(),
    val rolls: List<RollRecord> = emptyList(),
    val absolutions: List<Absolution> = emptyList(),
    /** Three-check: checks given by White and by Black. */
    val whiteChecks: Int = 0,
    val blackChecks: Int = 0,
    /** Seeds the Chess960 setup and roguelike perk offers, so a game replays identically. */
    val seed: Long = 0,
    /** Hints each player may use: -1 = unlimited, 0 = off. */
    val hintsAllowed: Int = 3,
    val whiteHints: Int = 0,
    val blackHints: Int = 0,
) {
    fun playerName(side: Side): String = if (side == Side.WHITE) whiteName else blackName
    fun checksBy(side: Side): Int = if (side == Side.WHITE) whiteChecks else blackChecks
    fun hintsUsed(side: Side): Int = if (side == Side.WHITE) whiteHints else blackHints
    fun hintsLeft(side: Side): Int = if (hintsAllowed < 0) Int.MAX_VALUE else (hintsAllowed - hintsUsed(side)).coerceAtLeast(0)
}
