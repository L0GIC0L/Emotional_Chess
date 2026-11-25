package com.emotionalchess.engine

import kotlin.math.abs
import kotlin.math.max

/**
 * Rules engine for Remorse Chess.
 *
 * Classic mode (original variant): a piece that captures becomes remorseful. It may move and
 * block but cannot capture (so it gives no check) until a friendly bishop *moves* onto one of
 * the eight squares around it. A bishop cannot forgive itself.
 *
 * Emotional mode (dice variant): the first time a piece captures, it rolls one die:
 *   1    Devastated   – frozen, gives no check; leaves the board at the end of its owner's
 *                       third following turn unless absolved. A king that leaves loses.
 *   2–3  Guilt-ridden – moves normally but cannot capture or give check until absolved.
 *   4–5  Unaffected   – nothing happens.
 *   6    Psychopath   – permanent. Every later capture earns one mandatory extra move with the
 *                       same piece (an extra-move capture earns no further move). An extra move
 *                       may capture the enemy king, which wins.
 * Each piece rolls exactly once. Absolution is its own turn: a friendly, non-devastated bishop on
 * one of the eight surrounding squares absolves a guilty or devastated piece. No bishop absolves
 * itself, and absolution is not allowed while your king is in check.
 */
object Rules {
    const val DEVASTATION_TURNS = 3

    fun file(sq: Int) = sq % 8
    fun rank(sq: Int) = sq / 8
    fun sq(file: Int, rank: Int) = rank * 8 + file
    fun onBoard(file: Int, rank: Int) = file in 0..7 && rank in 0..7
    fun squareName(sq: Int) = "${'a' + file(sq)}${rank(sq) + 1}"
    private fun distance(a: Int, b: Int) = max(abs(file(a) - file(b)), abs(rank(a) - rank(b)))

    fun neighbours(sq: Int): List<Int> = buildList {
        for (df in -1..1) for (dr in -1..1) {
            if (df == 0 && dr == 0) continue
            val f = file(sq) + df
            val r = rank(sq) + dr
            if (onBoard(f, r)) add(sq(f, r))
        }
    }

    // ---------------------------------------------------------------- setup

    fun newGame(mode: Mode, whiteName: String = "White", blackName: String = "Black"): GameState {
        val board = MutableList<Piece?>(64) { null }
        val back = listOf(Kind.ROOK, Kind.KNIGHT, Kind.BISHOP, Kind.QUEEN, Kind.KING, Kind.BISHOP, Kind.KNIGHT, Kind.ROOK)
        for (f in 0..7) {
            board[sq(f, 0)] = Piece(Side.WHITE, back[f])
            board[sq(f, 1)] = Piece(Side.WHITE, Kind.PAWN)
            board[sq(f, 6)] = Piece(Side.BLACK, Kind.PAWN)
            board[sq(f, 7)] = Piece(Side.BLACK, back[f])
        }
        val state = GameState(
            mode = mode,
            whiteName = whiteName.ifBlank { "White" },
            blackName = blackName.ifBlank { "Black" },
            board = board,
        )
        return state.copy(positions = listOf(positionKey(state)))
    }

    // ---------------------------------------------------------------- attacks

    /** Pure geometry: does the piece on [from] attack [target]? Ignores mood. */
    private fun geometricAttack(board: List<Piece?>, from: Int, target: Int): Boolean {
        val p = board[from] ?: return false
        val df = file(target) - file(from)
        val dr = rank(target) - rank(from)
        if (df == 0 && dr == 0) return false
        return when (p.kind) {
            Kind.PAWN -> dr == (if (p.side == Side.WHITE) 1 else -1) && abs(df) == 1
            Kind.KNIGHT -> (abs(df) == 1 && abs(dr) == 2) || (abs(df) == 2 && abs(dr) == 1)
            Kind.KING -> max(abs(df), abs(dr)) == 1
            Kind.ROOK -> (df == 0 || dr == 0) && pathClear(board, from, target)
            Kind.BISHOP -> abs(df) == abs(dr) && pathClear(board, from, target)
            Kind.QUEEN -> (df == 0 || dr == 0 || abs(df) == abs(dr)) && pathClear(board, from, target)
        }
    }

    private fun pathClear(board: List<Piece?>, from: Int, to: Int): Boolean {
        val sf = Integer.signum(file(to) - file(from))
        val sr = Integer.signum(rank(to) - rank(from))
        var f = file(from) + sf
        var r = rank(from) + sr
        while (sq(f, r) != to) {
            if (board[sq(f, r)] != null) return false
            f += sf
            r += sr
        }
        return true
    }

    /**
     * Is [square] attacked by [by]? Guilty and devastated pieces cannot capture, so they attack
     * nothing. Kings may never stand next to each other, whatever their mood.
     */
    fun isAttacked(board: List<Piece?>, square: Int, by: Side): Boolean {
        for (i in 0 until 64) {
            val p = board[i] ?: continue
            if (p.side != by) continue
            if (p.kind == Kind.KING && distance(i, square) == 1) return true
            if (!p.canCapture) continue
            if (geometricAttack(board, i, square)) return true
        }
        return false
    }

    fun kingSquare(board: List<Piece?>, side: Side): Int? =
        board.indices.firstOrNull { board[it]?.let { p -> p.side == side && p.kind == Kind.KING } == true }

    fun inCheck(state: GameState, side: Side = state.toMove): Boolean {
        val k = kingSquare(state.board, side) ?: return false
        return isAttacked(state.board, k, side.other)
    }

    // ---------------------------------------------------------------- move generation

    private fun pseudoMoves(s: GameState, from: Int): List<Move> {
        val p = s.board[from] ?: return emptyList()
        if (p.frozen) return emptyList()
        val bonus = s.bonusSquare != null
        val moves = mutableListOf<Move>()
        val f0 = file(from)
        val r0 = rank(from)

        fun canTake(t: Piece) = t.side != p.side && p.canCapture && (t.kind != Kind.KING || bonus)

        fun addStep(f: Int, r: Int): Boolean { // returns true if the ray may continue
            if (!onBoard(f, r)) return false
            val to = sq(f, r)
            val t = s.board[to]
            if (t == null) { moves += Move(from, to); return true }
            if (canTake(t)) moves += Move(from, to)
            return false
        }

        when (p.kind) {
            Kind.PAWN -> {
                val dir = if (p.side == Side.WHITE) 1 else -1
                val startRank = if (p.side == Side.WHITE) 1 else 6
                val promoRank = if (p.side == Side.WHITE) 7 else 0
                fun addPawn(to: Int, ep: Boolean = false) {
                    if (rank(to) == promoRank) {
                        for (k in listOf(Kind.QUEEN, Kind.ROOK, Kind.BISHOP, Kind.KNIGHT)) moves += Move(from, to, promotion = k)
                    } else moves += Move(from, to, enPassant = ep)
                }
                if (onBoard(f0, r0 + dir) && s.board[sq(f0, r0 + dir)] == null) {
                    addPawn(sq(f0, r0 + dir))
                    if (r0 == startRank && s.board[sq(f0, r0 + 2 * dir)] == null) moves += Move(from, sq(f0, r0 + 2 * dir))
                }
                for (df in listOf(-1, 1)) {
                    if (!onBoard(f0 + df, r0 + dir)) continue
                    val to = sq(f0 + df, r0 + dir)
                    val t = s.board[to]
                    if (t != null && canTake(t)) addPawn(to)
                    else if (t == null && to == s.epSquare && p.canCapture) addPawn(to, ep = true)
                }
            }
            Kind.KNIGHT -> for ((df, dr) in KNIGHT_STEPS) addStep(f0 + df, r0 + dr)
            Kind.KING -> {
                for (df in -1..1) for (dr in -1..1) if (df != 0 || dr != 0) addStep(f0 + df, r0 + dr)
                if (!p.moved && !bonus && !inCheck(s, p.side)) addCastles(s, from, p, moves)
            }
            else -> {
                val dirs = when (p.kind) {
                    Kind.ROOK -> ORTHOGONAL
                    Kind.BISHOP -> DIAGONAL
                    else -> ORTHOGONAL + DIAGONAL
                }
                for ((df, dr) in dirs) {
                    var f = f0 + df
                    var r = r0 + dr
                    while (addStep(f, r)) { f += df; r += dr }
                }
            }
        }
        return moves
    }

    private fun addCastles(s: GameState, from: Int, king: Piece, moves: MutableList<Move>) {
        val r = rank(from)
        for ((rookFile, step) in listOf(7 to 1, 0 to -1)) {
            val rook = s.board[sq(rookFile, r)] ?: continue
            if (rook.side != king.side || rook.kind != Kind.ROOK || rook.moved || rook.frozen) continue
            val between = if (rookFile == 7) (file(from) + 1 until 7) else (1 until file(from))
            if (between.any { s.board[sq(it, r)] != null }) continue
            val pass1 = from + step
            val pass2 = from + 2 * step
            if (isAttacked(s.board, pass1, king.side.other) || isAttacked(s.board, pass2, king.side.other)) continue
            moves += Move(from, pass2, castle = true)
        }
    }

    /** Applies the piece movement only (no mood/turn bookkeeping). Returns new board and captured piece. */
    private fun movePieces(board: List<Piece?>, m: Move): Pair<List<Piece?>, Piece?> {
        val b = board.toMutableList()
        val p = b[m.from]!!
        var captured = b[m.to]
        if (m.enPassant) {
            val capSq = sq(file(m.to), rank(m.from))
            captured = b[capSq]
            b[capSq] = null
        }
        b[m.to] = p.copy(kind = m.promotion ?: p.kind, moved = true)
        b[m.from] = null
        if (m.castle) {
            val kingside = file(m.to) == 6
            val rookFrom = if (kingside) m.to + 1 else m.to - 2
            val rookTo = if (kingside) m.to - 1 else m.to + 1
            b[rookTo] = b[rookFrom]!!.copy(moved = true)
            b[rookFrom] = null
        }
        return b to captured
    }

    fun legalMovesFrom(s: GameState, from: Int): List<Move> {
        if (s.result != null || s.pendingRoll != null) return emptyList()
        val p = s.board[from] ?: return emptyList()
        if (p.side != s.toMove) return emptyList()
        if (s.bonusSquare != null && s.bonusSquare != from) return emptyList()
        return pseudoMoves(s, from).filter { m ->
            if (s.board[m.to]?.kind == Kind.KING) return@filter true // psychopath bonus move takes the king
            val (b, _) = movePieces(s.board, m)
            val k = kingSquare(b, p.side) ?: return@filter true
            !isAttacked(b, k, p.side.other)
        }
    }

    fun allLegalMoves(s: GameState): List<Move> =
        (0 until 64).flatMap { legalMovesFrom(s, it) }

    // ---------------------------------------------------------------- absolution

    /** The bishop square that can absolve the piece on [target] this turn, or null. */
    fun absolverFor(s: GameState, target: Int): Int? {
        if (s.mode != Mode.EMOTIONAL || s.result != null || s.pendingRoll != null || s.bonusSquare != null) return null
        val p = s.board[target] ?: return null
        if (p.side != s.toMove || (p.mood != Mood.GUILTY && p.mood != Mood.DEVASTATED)) return null
        if (inCheck(s)) return null // absolution never gets you out of check
        return neighbours(target).firstOrNull { n ->
            val b = s.board[n]
            b != null && b.side == p.side && b.kind == Kind.BISHOP && b.mood != Mood.DEVASTATED
        }
    }

    fun legalAbsolutions(s: GameState): List<Int> = (0 until 64).filter { absolverFor(s, it) != null }

    // ---------------------------------------------------------------- actions

    fun play(s: GameState, m: Move): GameState {
        require(m in legalMovesFrom(s, m.from)) { "Illegal move" }
        val p = s.board[m.from]!!
        val (moved, captured) = movePieces(s.board, m)
        val board = moved.toMutableList()
        val wasBonus = s.bonusSquare != null
        val log = s.log.toMutableList()
        log += describe(s, m, p, captured, wasBonus)

        val isDoublePush = p.kind == Kind.PAWN && abs(rank(m.to) - rank(m.from)) == 2
        var next = s.copy(
            lastMove = m,
            bonusSquare = null,
            epSquare = if (isDoublePush) (m.from + m.to) / 2 else null,
            halfmoveClock = if (p.kind == Kind.PAWN || captured != null) 0 else s.halfmoveClock + 1,
        )

        // Classic: a bishop that moves forgives remorseful friends on the 8 surrounding squares.
        if (s.mode == Mode.CLASSIC && p.kind == Kind.BISHOP) {
            for (n in neighbours(m.to)) {
                val f = board[n] ?: continue
                if (f.side == p.side && f.mood == Mood.GUILTY) {
                    board[n] = f.copy(mood = Mood.CALM)
                    log += "   ✝ ${f.name} on ${squareName(n)} is forgiven"
                }
            }
        }

        if (captured != null) {
            if (captured.kind == Kind.KING) {
                log += "   ☠ The psychopath captured the king!"
                return next.copy(
                    board = board, log = log,
                    result = GameResult(p.side, "${p.side.label}'s psychopath captured the king"),
                )
            }
            val mover = board[m.to]!!
            when (s.mode) {
                Mode.CLASSIC -> {
                    board[m.to] = mover.copy(mood = Mood.GUILTY)
                    log += "   ${mover.name} is now remorseful"
                }
                Mode.EMOTIONAL -> when {
                    mover.mood == Mood.CALM -> {
                        log += "   🎲 ${mover.name} must roll the die"
                        return next.copy(board = board, log = log, pendingRoll = m.to)
                    }
                    mover.mood == Mood.PSYCHOPATH && !wasBonus -> {
                        val withBonus = next.copy(board = board, log = log, bonusSquare = m.to)
                        if (legalMovesFrom(withBonus, m.to).isNotEmpty()) {
                            return withBonus.copy(log = log + "   ${mover.name} hungers for more – it must move again")
                        }
                    }
                }
            }
        }
        next = next.copy(board = board, log = log)
        return endTurn(next)
    }

    fun moodForRoll(roll: Int): Mood = when (roll) {
        1 -> Mood.DEVASTATED
        2, 3 -> Mood.GUILTY
        4, 5 -> Mood.UNAFFECTED
        6 -> Mood.PSYCHOPATH
        else -> throw IllegalArgumentException("Die roll must be 1–6")
    }

    fun applyRoll(s: GameState, roll: Int): GameState {
        val at = requireNotNull(s.pendingRoll) { "No roll pending" }
        val mood = moodForRoll(roll)
        val p = s.board[at]!!
        val board = s.board.toMutableList()
        board[at] = p.copy(
            mood = mood,
            countdown = if (mood == Mood.DEVASTATED) DEVASTATION_TURNS else 0,
            fresh = mood == Mood.DEVASTATED,
        )
        val log = s.log + "   🎲 Rolled $roll – ${p.name} is ${moodLabel(mood, s.mode).lowercase()}"
        return endTurn(s.copy(board = board, pendingRoll = null, log = log))
    }

    fun absolve(s: GameState, target: Int): GameState {
        val bishop = requireNotNull(absolverFor(s, target)) { "Cannot absolve" }
        val p = s.board[target]!!
        val board = s.board.toMutableList()
        board[target] = p.copy(mood = Mood.UNAFFECTED, countdown = 0, fresh = false)
        val prefix = if (s.toMove == Side.WHITE) "${s.fullmove}." else "${s.fullmove}…"
        val log = s.log + "$prefix ✝ Bishop ${squareName(bishop)} absolves ${p.kind.label} ${squareName(target)}"
        return endTurn(s.copy(board = board, log = log, lastMove = null, epSquare = null, halfmoveClock = s.halfmoveClock + 1))
    }

    /** Resign / agreed draw from the table. */
    fun resign(s: GameState, side: Side): GameState =
        s.copy(result = GameResult(side.other, "${side.label} resigned"), log = s.log + "${side.label} resigns")

    fun agreeDraw(s: GameState): GameState =
        s.copy(result = GameResult(null, "Draw by agreement"), log = s.log + "Draw agreed")

    // ---------------------------------------------------------------- turn bookkeeping

    private fun endTurn(s: GameState): GameState {
        val mover = s.toMove
        val board = s.board.toMutableList()
        val log = s.log.toMutableList()
        var loss: String? = null
        var removed = false
        for (i in 0 until 64) {
            val p = board[i] ?: continue
            if (p.side != mover || p.mood != Mood.DEVASTATED) continue
            if (p.fresh) {
                board[i] = p.copy(fresh = false)
                continue
            }
            val left = p.countdown - 1
            if (left <= 0) {
                board[i] = null
                removed = true
                log += "   💧 ${p.name} on ${squareName(i)} was overcome by despair and left the board"
                if (p.kind == Kind.KING) loss = "${mover.label}'s king was overcome by despair"
            } else board[i] = p.copy(countdown = left)
        }
        if (loss == null && removed) {
            val k = kingSquare(board, mover)
            if (k != null && isAttacked(board, k, mover.other)) loss = "A departing piece exposed ${mover.label}'s king"
        }
        var next = s.copy(
            board = board,
            log = log,
            toMove = mover.other,
            fullmove = if (mover == Side.BLACK) s.fullmove + 1 else s.fullmove,
        )
        if (loss != null) return next.copy(result = GameResult(mover.other, loss))
        next = next.copy(positions = next.positions + positionKey(next))
        return evaluate(next)
    }

    private fun evaluate(s: GameState): GameState {
        val canAct = allLegalMoves(s).isNotEmpty() || legalAbsolutions(s).isNotEmpty()
        val result = when {
            !canAct && inCheck(s) -> GameResult(s.toMove.other, "Checkmate")
            !canAct -> GameResult(null, "Stalemate")
            s.halfmoveClock >= 100 -> GameResult(null, "Draw by the 50-move rule")
            s.positions.count { it == s.positions.last() } >= 3 -> GameResult(null, "Draw by threefold repetition")
            s.board.filterNotNull().all { it.kind == Kind.KING && it.mood != Mood.DEVASTATED } ->
                GameResult(null, "Draw – only the kings remain")
            else -> null
        }
        return if (result != null) s.copy(result = result, log = s.log + result.reason) else s
    }

    private fun positionKey(s: GameState): String = buildString {
        for (p in s.board) {
            if (p == null) { append('.'); continue }
            append(p.side.ordinal).append(p.kind.ordinal).append(p.mood.ordinal).append(p.countdown)
            if (p.kind == Kind.KING || p.kind == Kind.ROOK) append(if (p.moved) 'm' else 'u')
            append(',')
        }
        append(s.toMove.ordinal).append(':').append(s.epSquare ?: -1)
    }

    private fun describe(s: GameState, m: Move, p: Piece, captured: Piece?, bonus: Boolean): String {
        val prefix = when {
            bonus -> "   ↳ bonus:"
            s.toMove == Side.WHITE -> "${s.fullmove}."
            else -> "${s.fullmove}…"
        }
        val body = when {
            m.castle && file(m.to) == 6 -> "O-O"
            m.castle -> "O-O-O"
            else -> buildString {
                append(p.kind.letter)
                append(squareName(m.from))
                append(if (captured != null) "x" else "–")
                append(squareName(m.to))
                if (m.promotion != null) append("=").append(m.promotion.letter)
                if (m.enPassant) append(" e.p.")
            }
        }
        return "$prefix $body"
    }

    fun moodLabel(mood: Mood, mode: Mode): String = when (mood) {
        Mood.CALM -> if (mode == Mode.EMOTIONAL) "Innocent" else "Calm"
        Mood.UNAFFECTED -> if (mode == Mode.EMOTIONAL) "Unaffected" else "Calm"
        Mood.GUILTY -> if (mode == Mode.EMOTIONAL) "Guilt-ridden" else "Remorseful"
        Mood.DEVASTATED -> "Devastated"
        Mood.PSYCHOPATH -> "Psychopath"
    }

    fun moodDescription(mood: Mood, mode: Mode): String = when (mood) {
        Mood.CALM -> if (mode == Mode.EMOTIONAL) "Has not captured yet. Its first capture rolls the die." else "May capture. Capturing makes it remorseful."
        Mood.UNAFFECTED -> if (mode == Mode.EMOTIONAL) "Its roll is used. Plays like a normal piece." else "May capture."
        Mood.GUILTY -> if (mode == Mode.EMOTIONAL) "Moves normally but cannot capture or give check until a bishop next to it absolves it."
        else "Moves and blocks but cannot capture or give check until a friendly bishop moves next to it."
        Mood.DEVASTATED -> "Frozen and gives no check. Leaves the board after its owner's third turn unless absolved."
        Mood.PSYCHOPATH -> "Every capture earns one mandatory extra move with this piece."
    }

    private val KNIGHT_STEPS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
    private val ORTHOGONAL = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val DIAGONAL = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
}
