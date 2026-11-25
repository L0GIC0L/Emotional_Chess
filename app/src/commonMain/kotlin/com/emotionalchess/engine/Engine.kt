package com.emotionalchess.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Anything a player can do on their turn. */
sealed interface Action {
    data class Play(val move: Move) : Action
    /** Absolve, forgive or cure the piece on [square]. */
    data class Heal(val square: Int) : Action
    data class Pick(val perk: Perk) : Action
}

data class Hint(val action: Action, val text: String, val score: Int, val depth: Int)

/**
 * A small game-tree search for hints. It drives [Rules] directly, so it understands every mode:
 * moods, explosions, perks, plague and all. Iterative deepening alpha-beta with a capture
 * search at the leaves; die rolls are averaged over their odds.
 *
 * Scores are in centipawns from the point of view of the side to move.
 */
object Engine {
    private const val MATE = 1_000_000
    private const val INF = Int.MAX_VALUE / 2
    private const val QUIESCENCE_PLIES = 4

    /** Thrown (always the same instance, so it is cheap) when the time is up. */
    private object Timeout : RuntimeException()

    fun actions(s: GameState): List<Action> = when {
        s.result != null || s.pendingRoll != null -> emptyList()
        s.pendingPerk != null -> s.perkOffer.map { Action.Pick(it) }
        else -> Rules.allLegalMoves(s).map { Action.Play(it) } + Rules.legalAbsolutions(s).map { Action.Heal(it) }
    }

    fun apply(s: GameState, a: Action, fast: Boolean = true): GameState = when (a) {
        is Action.Play -> Rules.play(s, a.move, fast)
        is Action.Heal -> Rules.absolve(s, a.square, fast)
        is Action.Pick -> Rules.choosePerk(s, a.perk, fast)
    }

    /** The best action for the side to move, searching for at most [budgetMs]; null if there is no choice to make. */
    fun bestAction(s: GameState, budgetMs: Long = 1500, maxDepth: Int = 8): Hint? {
        val root = actions(s)
        if (root.isEmpty()) return null
        val deadline = TimeSource.Monotonic.markNow() + budgetMs.milliseconds
        var ordered = order(s, root)
        var best = ordered.first()
        var bestScore = 0
        var reached = 0
        if (root.size > 1) {
            for (depth in 1..maxDepth) {
                try {
                    var alpha = -INF
                    var localBest = ordered.first()
                    val scored = ArrayList<Pair<Action, Int>>(ordered.size)
                    for (a in ordered) {
                        val v = valueOf(s, apply(s, a), depth - 1, alpha, INF, 1, deadline)
                        scored += a to v
                        if (v > alpha) { alpha = v; localBest = a }
                    }
                    best = localBest
                    bestScore = alpha
                    reached = depth
                    // Search the best moves first next time round.
                    ordered = scored.sortedByDescending { it.second }.map { it.first }
                    if (abs(alpha) > MATE - 1000) break
                } catch (_: Timeout) {
                    break
                }
            }
        }
        return Hint(best, describe(s, best), bestScore, reached)
    }

    /** Value of [child] for the side to move in [parent]: the same side may move again (bonus move, perk pick). */
    private fun valueOf(parent: GameState, child: GameState, depth: Int, alpha: Int, beta: Int, ply: Int, deadline: TimeMark): Int =
        if (child.toMove == parent.toMove) search(child, depth, alpha, beta, ply, deadline)
        else -search(child, depth, -beta, -alpha, ply, deadline)

    private fun search(s: GameState, depth: Int, alphaIn: Int, beta: Int, ply: Int, deadline: TimeMark): Int {
        if (deadline.hasPassedNow()) throw Timeout
        s.result?.let { return terminal(s, it, ply) }
        if (s.pendingRoll != null) return chance(s, ply)
        if (depth <= 0) return quiesce(s, alphaIn, beta, ply, QUIESCENCE_PLIES, deadline)
        val acts = actions(s)
        if (acts.isEmpty()) return noMoves(s, ply)
        var alpha = alphaIn
        var best = -INF
        for (a in order(s, acts)) {
            val v = valueOf(s, apply(s, a), depth - 1, alpha, beta, ply + 1, deadline)
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        return best
    }

    /** Only captures (and forced choices), so the search does not stop in the middle of an exchange. */
    private fun quiesce(s: GameState, alphaIn: Int, beta: Int, ply: Int, left: Int, deadline: TimeMark): Int {
        if (deadline.hasPassedNow()) throw Timeout
        s.result?.let { return terminal(s, it, ply) }
        if (s.pendingRoll != null) return chance(s, ply)
        if (s.pendingPerk != null || s.bonusSquare != null) {
            // Not a quiet position: the same player must still act. Take the best option statically.
            val acts = actions(s)
            if (acts.isEmpty()) return evaluate(s)
            return acts.maxOf { a -> val c = apply(s, a); if (c.toMove == s.toMove) leaf(c, ply) else -leaf(c, ply) }
        }
        val standPat = evaluate(s)
        if (left == 0 || standPat >= beta) return standPat
        var alpha = max(alphaIn, standPat)
        val captures = Rules.allLegalMoves(s).filter { Rules.isCapture(s, it) || it.promotion == Kind.QUEEN }
        for (m in orderMoves(s, captures)) {
            val c = Rules.play(s, m, fast = true)
            val v = if (c.toMove == s.toMove) quiesce(c, alpha, beta, ply + 1, left - 1, deadline)
            else -quiesce(c, -beta, -alpha, ply + 1, left - 1, deadline)
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        return alpha
    }

    /** A capture that rolls the die: average the outcomes by their odds (1, 2–3, 4–5, 6). */
    private fun chance(s: GameState, ply: Int): Int {
        var total = 0
        for ((roll, weight) in listOf(1 to 1, 2 to 2, 4 to 2, 6 to 1)) {
            val c = Rules.applyRoll(s, roll, fast = true)
            total += weight * (if (c.toMove == s.toMove) leaf(c, ply) else -leaf(c, ply))
        }
        return total / 6
    }

    private fun leaf(s: GameState, ply: Int): Int = s.result?.let { terminal(s, it, ply) } ?: evaluate(s)

    private fun terminal(s: GameState, r: GameResult, ply: Int): Int = when (r.winner) {
        null -> 0
        s.toMove -> MATE - ply
        else -> -(MATE - ply)
    }

    private fun noMoves(s: GameState, ply: Int): Int = when {
        s.mode == Mode.ANTICHESS -> MATE - ply
        Rules.inCheck(s) -> -(MATE - ply)
        else -> 0
    }

    // ------------------------------------------------------------------ ordering

    private fun order(s: GameState, acts: List<Action>): List<Action> = acts.sortedByDescending { a ->
        when (a) {
            is Action.Play -> moveOrderScore(s, a.move)
            is Action.Heal -> 500
            is Action.Pick -> when (a.perk) {
                Perk.SHIELD -> 30
                Perk.LEAP -> 25
                Perk.CHARGE -> 20
                Perk.SLANT -> 15
                Perk.STEP -> 10
            }
        }
    }

    private fun orderMoves(s: GameState, moves: List<Move>) = moves.sortedByDescending { moveOrderScore(s, it) }

    private fun moveOrderScore(s: GameState, m: Move): Int {
        val mover = s.board[m.from] ?: return 0
        val victim = if (m.enPassant) Kind.PAWN else s.board[m.to]?.takeIf { it.side != mover.side }?.kind
        var score = 0
        if (victim != null) score += 10_000 + 10 * baseValue(victim) - baseValue(mover.kind) / 10
        if (m.promotion == Kind.QUEEN) score += 9_000
        if (m.castle) score += 50
        return score
    }

    // ------------------------------------------------------------------ evaluation

    private fun baseValue(kind: Kind) = when (kind) {
        Kind.PAWN -> 100
        Kind.KNIGHT -> 320
        Kind.BISHOP -> 330
        Kind.ROOK -> 500
        Kind.QUEEN -> 900
        Kind.KING -> 0
    }

    private fun pieceValue(p: Piece, mode: Mode): Int {
        if (mode == Mode.ANTICHESS) return -100 // fewer pieces is better
        var v = baseValue(p.kind).toDouble()
        v *= when (p.mood) {
            Mood.GUILTY -> 0.7
            Mood.DEVASTATED -> 0.15 * p.countdown
            Mood.PSYCHOPATH -> 1.2
            Mood.INFECTED -> 0.15 + 0.12 * p.countdown
            Mood.IMMUNE -> 1.05
            else -> 1.0
        }
        if (p.kind == Kind.KING && p.mood == Mood.INFECTED) v -= 200.0 * (Rules.INCUBATION_TURNS - p.countdown)
        if (p.kind == Kind.KING && p.mood == Mood.DEVASTATED) v -= 300.0 * (Rules.DEVASTATION_TURNS - p.countdown + 1)
        for (perk in p.perks) v += if (perk == Perk.SHIELD) baseValue(p.kind) / 3.0 else 45.0
        return v.toInt()
    }

    private fun centreDistance(sq: Int): Int {
        val f = Rules.file(sq)
        val r = Rules.rank(sq)
        return max(min(abs(f - 3), abs(f - 4)), min(abs(r - 3), abs(r - 4)))
    }

    private fun positional(p: Piece, sq: Int, mode: Mode): Int {
        if (mode == Mode.ANTICHESS) return 0
        val advance = if (p.side == Side.WHITE) Rules.rank(sq) else 7 - Rules.rank(sq)
        return when (p.kind) {
            Kind.PAWN -> advance * 8 + (if (Rules.file(sq) in 2..5) advance * 3 else 0)
            Kind.KNIGHT, Kind.BISHOP -> (3 - centreDistance(sq)) * 10
            Kind.QUEEN, Kind.ROOK -> (3 - centreDistance(sq)) * 3
            Kind.KING -> when (mode) {
                Mode.KING_OF_THE_HILL -> (3 - centreDistance(sq)) * 30
                Mode.RACING_KINGS -> Rules.rank(sq) * Rules.rank(sq) * 25
                else -> 0
            }
        }
    }

    /** Static evaluation for the side to move. */
    fun evaluate(s: GameState): Int {
        var score = 0
        for (i in 0 until 64) {
            val p = s.board[i] ?: continue
            val v = pieceValue(p, s.mode) + positional(p, i, s.mode)
            score += if (p.side == Side.WHITE) v else -v
        }
        if (s.mode == Mode.THREE_CHECK) score += 300 * (s.whiteChecks - s.blackChecks)
        return if (s.toMove == Side.WHITE) score else -score
    }

    // ------------------------------------------------------------------ text

    fun describe(s: GameState, a: Action): String = when (a) {
        is Action.Play -> {
            val m = a.move
            val p = s.board[m.from]
            when {
                m.castle -> if (Rules.file(m.to) == 6) "Castle kingside" else "Castle queenside"
                p == null -> "${Rules.squareName(m.from)} → ${Rules.squareName(m.to)}"
                else -> buildString {
                    append(p.kind.label).append(' ').append(Rules.squareName(m.from))
                    append(if (Rules.isCapture(s, m)) " takes " else " → ")
                    append(Rules.squareName(m.to))
                    if (m.promotion != null) append(" (promote to ${m.promotion.label.lowercase()})")
                }
            }
        }
        is Action.Heal -> {
            val p = s.board[a.square]
            val verb = when (s.mode) {
                Mode.PLAGUE -> "Cure"
                Mode.CLASSIC -> "Forgive"
                else -> "Absolve"
            }
            "$verb the ${p?.kind?.label?.lowercase() ?: "piece"} on ${Rules.squareName(a.square)}"
        }
        is Action.Pick -> "Pick ${a.perk.emoji} ${a.perk.label}"
    }
}
