package com.emotionalchess.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Rules engine for every mode. Normal chess is the base; each mode adds its own twist:
 *
 * Chess960 – shuffled back rank; castling puts king and rook on the usual g/f or c/d squares.
 * King of the Hill – a king that reaches d4, e4, d5 or e5 wins.
 * Three-check – the third check wins.
 * Atomic – a capture explodes: the capturer, the captured piece and every non-pawn next to the
 *   capture square leave the board. Kings cannot capture. Blowing up the enemy king wins; blowing
 *   up your own is illegal. While the kings touch, neither is in check.
 * Antichess – captures are compulsory, there is no check, the king is an ordinary piece (pawns may
 *   promote to one). You win by losing every piece or by being stalemated.
 * Racing Kings – no pawns, no castling; giving check is illegal. First king on the 8th rank wins;
 *   if Black can answer White's arrival with its own, it is a draw.
 *
 * Remorse, dice (EMOTIONAL) – the first time a piece captures it rolls one die:
 *   1    Devastated   – frozen, gives no check; leaves the board at the end of its owner's third
 *                       following turn unless absolved. A king that leaves loses.
 *   2–3  Guilt-ridden – moves normally but cannot capture or give check until absolved.
 *   4–5  Unaffected   – nothing happens.
 *   6    Psychopath   – every later capture earns one mandatory extra move with the same piece;
 *                       that extra move may capture the enemy king, which wins.
 *   Absolution is a whole turn: a friendly, non-devastated bishop next to the piece absolves it,
 *   and an afflicted bishop may absolve itself. Not allowed while in check.
 * Remorse, classic – a piece that captures becomes remorseful (cannot capture or give check) until
 *   a friendly bishop moves next to it. A remorseful bishop may spend a turn to forgive itself.
 * Plague – a piece that captures is infected. When an infected piece moves, every healthy piece
 *   next to its new square (either side) catches it. The infected die after their owner's fifth
 *   turn unless a bishop next to them spends a turn curing them; the cured are immune for good.
 *   Bishops are doctors and never fall ill.
 * Roguelike – a piece (not a king) that captures picks one of three perks. Movement perks add a
 *   second way of moving; a shield absorbs the next capture (the attacker stays where it was).
 */
object Rules {
    const val DEVASTATION_TURNS = 3
    const val INCUBATION_TURNS = 5

    fun file(sq: Int) = sq % 8
    fun rank(sq: Int) = sq / 8
    fun sq(file: Int, rank: Int) = rank * 8 + file
    fun onBoard(file: Int, rank: Int) = file in 0..7 && rank in 0..7
    fun squareName(sq: Int) = "${'a' + file(sq)}${rank(sq) + 1}"
    fun distance(a: Int, b: Int) = max(abs(file(a) - file(b)), abs(rank(a) - rank(b)))

    /** King of the Hill's centre: d4, e4, d5, e5. */
    val HILL: Set<Int> = setOf(sq(3, 3), sq(4, 3), sq(3, 4), sq(4, 4))

    private val NEIGHBOURS: List<List<Int>> = List(64) { s ->
        buildList {
            for (df in -1..1) for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val f = file(s) + df
                val r = rank(s) + dr
                if (onBoard(f, r)) add(sq(f, r))
            }
        }
    }

    fun neighbours(sq: Int): List<Int> = NEIGHBOURS[sq]

    // ---------------------------------------------------------------- setup

    private val STANDARD_BACK = listOf(Kind.ROOK, Kind.KNIGHT, Kind.BISHOP, Kind.QUEEN, Kind.KING, Kind.BISHOP, Kind.KNIGHT, Kind.ROOK)

    fun newGame(
        mode: Mode,
        whiteName: String = "White",
        blackName: String = "Black",
        seed: Long = Random.nextLong(),
        hints: Int = 3,
    ): GameState {
        val board = MutableList<Piece?>(64) { null }
        var id = 0
        fun put(s: Int, side: Side, kind: Kind) {
            board[s] = Piece(side, kind, id = id++)
        }
        if (mode == Mode.RACING_KINGS) {
            // FEN 8/8/8/8/8/8/krbnNBRK/qrbnNBRQ
            val w = Side.WHITE
            val b = Side.BLACK
            val rank2 = listOf(b to Kind.KING, b to Kind.ROOK, b to Kind.BISHOP, b to Kind.KNIGHT, w to Kind.KNIGHT, w to Kind.BISHOP, w to Kind.ROOK, w to Kind.KING)
            val rank1 = listOf(b to Kind.QUEEN, b to Kind.ROOK, b to Kind.BISHOP, b to Kind.KNIGHT, w to Kind.KNIGHT, w to Kind.BISHOP, w to Kind.ROOK, w to Kind.QUEEN)
            for (f in 0..7) {
                put(sq(f, 0), rank1[f].first, rank1[f].second)
                put(sq(f, 1), rank2[f].first, rank2[f].second)
            }
        } else {
            val back = if (mode == Mode.CHESS960) backRank960(Random(seed)) else STANDARD_BACK
            for (f in 0..7) put(sq(f, 0), Side.WHITE, back[f])
            for (f in 0..7) put(sq(f, 1), Side.WHITE, Kind.PAWN)
            for (f in 0..7) put(sq(f, 6), Side.BLACK, Kind.PAWN)
            for (f in 0..7) put(sq(f, 7), Side.BLACK, back[f])
        }
        val state = GameState(
            mode = mode,
            whiteName = whiteName.ifBlank { "White" },
            blackName = blackName.ifBlank { "Black" },
            board = board,
            seed = seed,
            hintsAllowed = hints,
        )
        return state.copy(positions = listOf(positionKey(state)))
    }

    /** A Chess960 back rank: bishops on opposite colours, king somewhere between the rooks. */
    fun backRank960(random: Random): List<Kind> {
        val r = arrayOfNulls<Kind>(8)
        r[random.nextInt(4) * 2] = Kind.BISHOP
        r[random.nextInt(4) * 2 + 1] = Kind.BISHOP
        fun empty() = (0..7).filter { r[it] == null }
        r[empty().random(random)] = Kind.QUEEN
        r[empty().random(random)] = Kind.KNIGHT
        r[empty().random(random)] = Kind.KNIGHT
        val rest = empty()
        r[rest[0]] = Kind.ROOK
        r[rest[1]] = Kind.KING
        r[rest[2]] = Kind.ROOK
        return r.map { it!! }
    }

    // ---------------------------------------------------------------- attacks

    /** Pure geometry for one way of moving: does a [kind] on [from] attack [target]? */
    private fun patternAttack(board: List<Piece?>, kind: Kind, side: Side, from: Int, target: Int): Boolean {
        val df = file(target) - file(from)
        val dr = rank(target) - rank(from)
        if (df == 0 && dr == 0) return false
        return when (kind) {
            Kind.PAWN -> dr == (if (side == Side.WHITE) 1 else -1) && abs(df) == 1
            Kind.KNIGHT -> (abs(df) == 1 && abs(dr) == 2) || (abs(df) == 2 && abs(dr) == 1)
            Kind.KING -> max(abs(df), abs(dr)) == 1
            Kind.ROOK -> (df == 0 || dr == 0) && pathClear(board, from, target)
            Kind.BISHOP -> abs(df) == abs(dr) && pathClear(board, from, target)
            Kind.QUEEN -> (df == 0 || dr == 0 || abs(df) == abs(dr)) && pathClear(board, from, target)
        }
    }

    /** Does the piece on [from] attack [target], counting its perks? Ignores mood. */
    private fun attacks(board: List<Piece?>, from: Int, target: Int): Boolean {
        val p = board[from] ?: return false
        if (patternAttack(board, p.kind, p.side, from, target)) return true
        return p.perks.any { perk -> perk.movesLike?.let { patternAttack(board, it, p.side, from, target) } == true }
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
     * nothing. Kings may never stand next to each other, whatever their mood – except in Atomic,
     * where kings cannot capture at all.
     */
    fun isAttacked(board: List<Piece?>, square: Int, by: Side, mode: Mode = Mode.STANDARD): Boolean {
        for (i in 0 until 64) {
            val p = board[i] ?: continue
            if (p.side != by) continue
            if (p.kind == Kind.KING) {
                if (mode != Mode.ATOMIC && distance(i, square) == 1) return true
                continue
            }
            if (!p.canCapture) continue
            if (attacks(board, i, square)) return true
        }
        return false
    }

    fun kingSquare(board: List<Piece?>, side: Side): Int? =
        board.indices.firstOrNull { board[it]?.let { p -> p.side == side && p.kind == Kind.KING } == true }

    /** Is [side]'s king in check on [board]? Antichess has no check; in Atomic, touching kings are safe. */
    fun kingInDanger(board: List<Piece?>, side: Side, mode: Mode): Boolean {
        if (!mode.hasCheck) return false
        val k = kingSquare(board, side) ?: return false
        if (mode == Mode.ATOMIC) {
            val enemy = kingSquare(board, side.other)
            if (enemy != null && distance(k, enemy) == 1) return false
        }
        return isAttacked(board, k, side.other, mode)
    }

    fun inCheck(state: GameState, side: Side = state.toMove): Boolean = kingInDanger(state.board, side, state.mode)

    // ---------------------------------------------------------------- move generation

    private fun promotionKinds(mode: Mode) =
        if (mode == Mode.ANTICHESS) listOf(Kind.QUEEN, Kind.ROOK, Kind.BISHOP, Kind.KNIGHT, Kind.KING)
        else listOf(Kind.QUEEN, Kind.ROOK, Kind.BISHOP, Kind.KNIGHT)

    private fun pseudoMoves(s: GameState, from: Int): List<Move> {
        val p = s.board[from] ?: return emptyList()
        if (p.frozen) return emptyList()
        val bonus = s.bonusSquare != null
        val moves = mutableListOf<Move>()
        val f0 = file(from)
        val r0 = rank(from)
        val promoRank = if (p.side == Side.WHITE) 7 else 0

        fun canTake(t: Piece): Boolean {
            if (t.side == p.side || !p.canCapture) return false
            if (s.mode == Mode.ATOMIC && p.kind == Kind.KING) return false
            if (t.kind == Kind.KING) return s.mode == Mode.ANTICHESS || bonus // psychopath bonus move
            return true
        }

        fun add(to: Int, ep: Boolean = false) {
            if (p.kind == Kind.PAWN && rank(to) == promoRank) {
                for (k in promotionKinds(s.mode)) moves += Move(from, to, promotion = k)
            } else moves += Move(from, to, enPassant = ep)
        }

        fun step(f: Int, r: Int): Boolean { // true if a ray may continue
            if (!onBoard(f, r)) return false
            val to = sq(f, r)
            val t = s.board[to]
            if (t == null) { add(to); return true }
            if (canTake(t)) add(to)
            return false
        }

        fun pattern(kind: Kind) {
            when (kind) {
                Kind.PAWN -> {
                    val dir = if (p.side == Side.WHITE) 1 else -1
                    val startRank = if (p.side == Side.WHITE) 1 else 6
                    if (onBoard(f0, r0 + dir) && s.board[sq(f0, r0 + dir)] == null) {
                        add(sq(f0, r0 + dir))
                        if (r0 == startRank && s.board[sq(f0, r0 + 2 * dir)] == null) moves += Move(from, sq(f0, r0 + 2 * dir))
                    }
                    for (df in listOf(-1, 1)) {
                        if (!onBoard(f0 + df, r0 + dir)) continue
                        val to = sq(f0 + df, r0 + dir)
                        val t = s.board[to]
                        if (t != null && canTake(t)) add(to)
                        else if (t == null && to == s.epSquare && p.canCapture) add(to, ep = true)
                    }
                }
                Kind.KNIGHT -> for ((df, dr) in KNIGHT_STEPS) step(f0 + df, r0 + dr)
                Kind.KING -> for ((df, dr) in KING_STEPS) step(f0 + df, r0 + dr)
                else -> {
                    val dirs = when (kind) {
                        Kind.ROOK -> ORTHOGONAL
                        Kind.BISHOP -> DIAGONAL
                        else -> KING_STEPS
                    }
                    for ((df, dr) in dirs) {
                        var f = f0 + df
                        var r = r0 + dr
                        while (step(f, r)) { f += df; r += dr }
                    }
                }
            }
        }

        pattern(p.kind)
        for (perk in p.perks) perk.movesLike?.let { pattern(it) }
        if (p.kind == Kind.KING && !p.moved && !bonus && s.mode.canCastle && !inCheck(s, p.side)) addCastles(s, from, p, moves)
        return if (p.perks.isEmpty()) moves else moves.distinct()
    }

    /**
     * Castling for standard chess and Chess960 alike: the king lands on g/c and the rook on f/d.
     * Every square between the king, the rook and their destinations must be empty (apart from
     * those two), and the king may not pass through or land on an attacked square.
     */
    private fun addCastles(s: GameState, kingSq: Int, king: Piece, moves: MutableList<Move>) {
        val r = rank(kingSq)
        for (rf in 0..7) {
            val rookSq = sq(rf, r)
            val rook = s.board[rookSq] ?: continue
            if (rook.side != king.side || rook.kind != Kind.ROOK || rook.moved || rook.frozen) continue
            val kingside = rf > file(kingSq)
            val kingDest = sq(if (kingside) 6 else 2, r)
            val rookDest = sq(if (kingside) 5 else 3, r)
            val files = listOf(file(kingSq), file(kingDest), rf, file(rookDest))
            val span = files.min()..files.max()
            if (span.any { f -> sq(f, r) != kingSq && sq(f, r) != rookSq && s.board[sq(f, r)] != null }) continue
            val without = s.board.toMutableList().also { it[kingSq] = null; it[rookSq] = null }
            val pathFiles = if (file(kingDest) >= file(kingSq)) file(kingSq)..file(kingDest) else file(kingDest)..file(kingSq)
            if (pathFiles.any { f -> isAttacked(without, sq(f, r), king.side.other, s.mode) }) continue
            moves += Move(kingSq, kingDest, castle = true, rookFrom = rookSq)
        }
    }

    private class Applied(
        val board: List<Piece?>,
        /** The piece taken by the move itself (not counting explosions). */
        val captured: Piece?,
        /** Everything that left the board, including atomic explosion victims and the capturer. */
        val casualties: List<Piece>,
        /** Roguelike: the move hit a shield, which broke; the attacker stayed home. */
        val struck: Boolean,
        val explosion: Int?,
    )

    private fun applyMove(s: GameState, m: Move): Applied {
        val b = s.board.toMutableList()
        val p = b[m.from]!!
        if (m.castle) {
            val r = rank(m.from)
            val kingside = file(m.to) == 6
            val rookSq = m.rookFrom ?: sq(if (kingside) 7 else 0, r)
            val rook = b[rookSq]!!
            b[m.from] = null
            b[rookSq] = null
            b[m.to] = p.copy(moved = true)
            b[sq(if (kingside) 5 else 3, r)] = rook.copy(moved = true)
            return Applied(b, null, emptyList(), false, null)
        }
        val capSq = if (m.enPassant) sq(file(m.to), rank(m.from)) else m.to
        val target = b[capSq]?.takeIf { it.side != p.side }
        if (target != null && Perk.SHIELD in target.perks) {
            b[capSq] = target.copy(perks = target.perks - Perk.SHIELD)
            return Applied(b, null, emptyList(), true, null)
        }
        b[capSq] = null
        b[m.from] = null
        b[m.to] = p.copy(kind = m.promotion ?: p.kind, moved = true)
        if (target == null) return Applied(b, null, emptyList(), false, null)
        if (s.mode != Mode.ATOMIC) return Applied(b, target, listOf(target), false, null)
        val casualties = mutableListOf(target, b[m.to]!!)
        b[m.to] = null
        for (n in neighbours(m.to)) {
            val q = b[n] ?: continue
            if (q.kind != Kind.PAWN) {
                casualties += q
                b[n] = null
            }
        }
        return Applied(b, target, casualties, false, m.to)
    }

    private fun isLegal(s: GameState, side: Side, m: Move): Boolean {
        if (!s.mode.hasCheck) return true
        // A psychopath's bonus move may take the king outright.
        if (s.board[m.to]?.let { it.kind == Kind.KING && it.side != side } == true) return true
        val after = applyMove(s, m).board
        if (s.mode == Mode.ATOMIC) {
            if (kingSquare(after, side) == null) return false // blew up its own king
            if (kingSquare(after, side.other) == null) return true // blew up the enemy king: wins
        }
        if (kingInDanger(after, side, s.mode)) return false
        if (s.mode == Mode.RACING_KINGS && kingInDanger(after, side.other, s.mode)) return false
        return true
    }

    private fun movesFrom(s: GameState, from: Int): List<Move> {
        if (s.result != null || s.pendingRoll != null || s.pendingPerk != null) return emptyList()
        val p = s.board[from] ?: return emptyList()
        if (p.side != s.toMove) return emptyList()
        if (s.bonusSquare != null && s.bonusSquare != from) return emptyList()
        return pseudoMoves(s, from).filter { isLegal(s, p.side, it) }
    }

    fun isCapture(s: GameState, m: Move): Boolean =
        m.enPassant || s.board[m.to]?.let { it.side != s.board[m.from]?.side } == true

    fun allLegalMoves(s: GameState): List<Move> {
        val all = (0 until 64).flatMap { movesFrom(s, it) }
        if (s.mode == Mode.ANTICHESS) {
            val captures = all.filter { isCapture(s, it) }
            if (captures.isNotEmpty()) return captures
        }
        return all
    }

    fun legalMovesFrom(s: GameState, from: Int): List<Move> =
        if (s.mode == Mode.ANTICHESS) allLegalMoves(s).filter { it.from == from } else movesFrom(s, from)

    // ---------------------------------------------------------------- absolution / cure

    /**
     * The square of the bishop that can heal the piece on [target] this turn (the target itself
     * when a bishop heals itself), or null.
     */
    fun absolverFor(s: GameState, target: Int): Int? {
        if (s.result != null || s.pendingRoll != null || s.pendingPerk != null || s.bonusSquare != null) return null
        val p = s.board[target] ?: return null
        if (p.side != s.toMove) return null
        val afflicted = when (s.mode) {
            Mode.EMOTIONAL -> p.mood == Mood.GUILTY || p.mood == Mood.DEVASTATED
            Mode.CLASSIC -> p.mood == Mood.GUILTY && p.kind == Kind.BISHOP // others are forgiven by a bishop's move
            Mode.PLAGUE -> p.mood == Mood.INFECTED
            else -> false
        }
        if (!afflicted || inCheck(s)) return null
        if (p.kind == Kind.BISHOP) return target // a bishop heals itself
        return neighbours(target).firstOrNull { n ->
            val b = s.board[n]
            b != null && b.side == p.side && b.kind == Kind.BISHOP && b.mood != Mood.DEVASTATED
        }
    }

    fun legalAbsolutions(s: GameState): List<Int> = (0 until 64).filter { absolverFor(s, it) != null }

    // ---------------------------------------------------------------- actions

    fun play(s: GameState, m: Move, fast: Boolean = false): GameState {
        if (!fast) require(m in legalMovesFrom(s, m.from)) { "Illegal move" }
        val p = s.board[m.from]!!
        val applied = applyMove(s, m)
        val board = applied.board.toMutableList()
        val captured = applied.captured
        val wasBonus = s.bonusSquare != null
        val log = s.log.toMutableList()
        log += describe(s, m, p, captured, wasBonus, applied.struck)
        val absolutions = s.absolutions.toMutableList()

        val isDoublePush = p.kind == Kind.PAWN && abs(rank(m.to) - rank(m.from)) == 2 && file(m.to) == file(m.from)
        var next = s.copy(
            lastMove = m,
            bonusSquare = null,
            explosion = applied.explosion,
            epSquare = if (isDoublePush) (m.from + m.to) / 2 else null,
            halfmoveClock = if (p.kind == Kind.PAWN || captured != null || applied.struck) 0 else s.halfmoveClock + 1,
            fallen = s.fallen + applied.casualties.map { Fallen(it.side, it.kind) },
        )
        if (applied.struck) log += "   🛡️ The shield shattered – the attacker stays put"

        // Classic remorse: a bishop that moves forgives remorseful friends around it.
        if (s.mode == Mode.CLASSIC && p.kind == Kind.BISHOP) {
            for (n in neighbours(m.to)) {
                val f = board[n] ?: continue
                if (f.side == p.side && f.mood == Mood.GUILTY) {
                    board[n] = f.copy(mood = Mood.CALM)
                    log += "   ✝️ ${f.name} on ${squareName(n)} is forgiven"
                    absolutions += Absolution(f.side, f.kind)
                }
            }
        }

        // Plague: a sick piece that moves infects every healthy piece next to where it lands.
        if (s.mode == Mode.PLAGUE && p.mood == Mood.INFECTED && board[m.to] != null) {
            for (n in neighbours(m.to)) {
                val q = board[n] ?: continue
                if (q.mood != Mood.CALM || q.kind == Kind.BISHOP) continue
                // The countdown of a piece infected on its owner's own turn starts next turn.
                board[n] = q.copy(mood = Mood.INFECTED, countdown = INCUBATION_TURNS, fresh = q.side == p.side)
                log += "   🦠 ${q.name} on ${squareName(n)} caught the plague"
            }
        }

        if (s.mode == Mode.ATOMIC && captured != null) {
            log += "   💥 " + applied.casualties.joinToString { it.name } + " exploded"
            if (kingSquare(board, p.side.other) == null) {
                val reason = "${p.side.other.label}'s king exploded"
                return next.copy(board = board, log = log + reason, absolutions = absolutions, result = GameResult(p.side, reason))
            }
        }

        if (captured != null && s.mode != Mode.ATOMIC) {
            if (captured.kind == Kind.KING && s.mode != Mode.ANTICHESS) {
                log += "   ☠️ The psychopath captured the king!"
                return next.copy(
                    board = board, log = log, absolutions = absolutions,
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
                        return next.copy(board = board, log = log, absolutions = absolutions, pendingRoll = m.to)
                    }
                    mover.mood == Mood.PSYCHOPATH && !wasBonus -> {
                        val withBonus = next.copy(board = board, log = log, absolutions = absolutions, bonusSquare = m.to)
                        if (legalMovesFrom(withBonus, m.to).isNotEmpty()) {
                            return withBonus.copy(log = log + "   ${mover.name} hungers for more – it must move again")
                        }
                    }
                }
                Mode.PLAGUE -> if (mover.mood == Mood.CALM && mover.kind != Kind.BISHOP) {
                    board[m.to] = mover.copy(mood = Mood.INFECTED, countdown = INCUBATION_TURNS, fresh = true)
                    log += "   🦠 ${mover.name} caught the plague from its victim"
                }
                Mode.ROGUELIKE -> if (mover.kind != Kind.KING) {
                    val offer = perkOptions(mover).shuffled(Random(s.seed xor (s.log.size * 7919L) xor m.to.toLong())).take(3)
                    if (offer.isNotEmpty()) {
                        log += "   ⭐ ${mover.name} levels up!"
                        return next.copy(board = board, log = log, absolutions = absolutions, pendingPerk = m.to, perkOffer = offer)
                    }
                }
                else -> Unit
            }
        }
        next = next.copy(board = board, log = log, absolutions = absolutions)
        return endTurn(next, fast)
    }

    fun moodForRoll(roll: Int): Mood = when (roll) {
        1 -> Mood.DEVASTATED
        2, 3 -> Mood.GUILTY
        4, 5 -> Mood.UNAFFECTED
        6 -> Mood.PSYCHOPATH
        else -> throw IllegalArgumentException("Die roll must be 1–6")
    }

    fun applyRoll(s: GameState, roll: Int, fast: Boolean = false): GameState {
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
        val rolls = s.rolls + RollRecord(p.side, p.kind, roll)
        return endTurn(s.copy(board = board, pendingRoll = null, log = log, rolls = rolls), fast)
    }

    /** Roguelike perks this piece could still learn. */
    fun perkOptions(p: Piece): List<Perk> = Perk.entries.filter { perk ->
        perk !in p.perks && when (perk) {
            Perk.LEAP -> p.kind != Kind.KNIGHT
            Perk.CHARGE -> p.kind != Kind.ROOK && p.kind != Kind.QUEEN
            Perk.SLANT -> p.kind != Kind.BISHOP && p.kind != Kind.QUEEN
            Perk.STEP -> p.kind != Kind.QUEEN
            Perk.SHIELD -> true
        }
    }

    fun choosePerk(s: GameState, perk: Perk, fast: Boolean = false): GameState {
        val at = requireNotNull(s.pendingPerk) { "No perk pending" }
        require(perk in s.perkOffer) { "Perk not on offer" }
        val p = s.board[at]!!
        val board = s.board.toMutableList()
        board[at] = p.copy(perks = p.perks + perk)
        val log = s.log + "   ${perk.emoji} ${p.name} learns ${perk.label}"
        return endTurn(s.copy(board = board, pendingPerk = null, perkOffer = emptyList(), log = log), fast)
    }

    fun absolve(s: GameState, target: Int, fast: Boolean = false): GameState {
        val healer = requireNotNull(absolverFor(s, target)) { "Cannot absolve" }
        val p = s.board[target]!!
        val board = s.board.toMutableList()
        val healed = when (s.mode) {
            Mode.PLAGUE -> Mood.IMMUNE
            Mode.CLASSIC -> Mood.CALM
            else -> Mood.UNAFFECTED
        }
        board[target] = p.copy(mood = healed, countdown = 0, fresh = false)
        val prefix = if (s.toMove == Side.WHITE) "${s.fullmove}." else "${s.fullmove}…"
        val verb = when (s.mode) {
            Mode.PLAGUE -> "💉 Bishop ${squareName(healer)} cures"
            Mode.CLASSIC -> "✝️ Bishop ${squareName(healer)} forgives"
            else -> "✝️ Bishop ${squareName(healer)} absolves"
        }
        val whom = if (healer == target) "itself" else "${p.kind.label} ${squareName(target)}"
        return endTurn(
            s.copy(
                board = board, log = s.log + "$prefix $verb $whom", lastMove = null, epSquare = null,
                halfmoveClock = s.halfmoveClock + 1, absolutions = s.absolutions + Absolution(p.side, p.kind),
            ),
            fast,
        )
    }

    /** Resign / agreed draw from the table. */
    fun resign(s: GameState, side: Side): GameState =
        s.copy(result = GameResult(side.other, "${side.label} resigned"), log = s.log + "${side.label} resigns")

    fun agreeDraw(s: GameState): GameState =
        s.copy(result = GameResult(null, "Draw by agreement"), log = s.log + "Draw agreed")

    // ---------------------------------------------------------------- turn bookkeeping

    private fun endTurn(s: GameState, fast: Boolean): GameState {
        val mover = s.toMove
        val board = s.board.toMutableList()
        val log = s.log.toMutableList()
        val fallen = s.fallen.toMutableList()
        var loss: String? = null
        var removed = false
        for (i in 0 until 64) {
            val p = board[i] ?: continue
            if (p.side != mover || !p.dying) continue
            if (p.fresh) {
                board[i] = p.copy(fresh = false)
                continue
            }
            val left = p.countdown - 1
            if (left > 0) {
                board[i] = p.copy(countdown = left)
                continue
            }
            board[i] = null
            removed = true
            fallen += Fallen(p.side, p.kind, despair = true)
            val plague = p.mood == Mood.INFECTED
            log += if (plague) "   ☠️ ${p.name} on ${squareName(i)} succumbed to the plague"
            else "   💧 ${p.name} on ${squareName(i)} was overcome by despair and left the board"
            if (p.kind == Kind.KING) loss = if (plague) "${mover.label}'s king succumbed to the plague" else "${mover.label}'s king was overcome by despair"
        }
        if (loss == null && removed && kingInDanger(board, mover, s.mode)) loss = "A departing piece exposed ${mover.label}'s king"
        var next = s.copy(
            board = board,
            log = log,
            fallen = fallen,
            toMove = mover.other,
            fullmove = if (mover == Side.BLACK) s.fullmove + 1 else s.fullmove,
        )
        if (loss != null) return next.copy(result = GameResult(mover.other, loss))
        if (s.mode == Mode.THREE_CHECK && inCheck(next)) {
            next = if (mover == Side.WHITE) next.copy(whiteChecks = next.whiteChecks + 1) else next.copy(blackChecks = next.blackChecks + 1)
        }
        next = next.copy(positions = next.positions + positionKey(next))
        return evaluate(next, fast)
    }

    /**
     * Results after a turn. With [fast] (used by the hint search), running out of moves is not
     * checked here; the search sees that by itself.
     */
    private fun evaluate(s: GameState, fast: Boolean): GameState {
        val result = modeResult(s) ?: run {
            if (!fast && allLegalMoves(s).isEmpty() && legalAbsolutions(s).isEmpty()) {
                when {
                    s.mode == Mode.ANTICHESS -> GameResult(s.toMove, "${s.toMove.label} has no moves and wins")
                    inCheck(s) -> GameResult(s.toMove.other, "Checkmate")
                    else -> GameResult(null, "Stalemate")
                }
            } else null
        } ?: when {
            s.halfmoveClock >= 100 -> GameResult(null, "Draw by the 50-move rule")
            s.positions.count { it == s.positions.last() } >= 3 -> GameResult(null, "Draw by threefold repetition")
            s.mode != Mode.ANTICHESS && s.mode != Mode.RACING_KINGS &&
                s.board.filterNotNull().all { it.kind == Kind.KING && !it.dying } -> GameResult(null, "Draw – only the kings remain")
            else -> null
        }
        return if (result != null) s.copy(result = result, log = s.log + result.reason) else s
    }

    private fun modeResult(s: GameState): GameResult? {
        val mover = s.toMove.other
        return when (s.mode) {
            Mode.KING_OF_THE_HILL ->
                if (kingSquare(s.board, mover) in HILL) GameResult(mover, "${mover.label}'s king took the hill") else null
            Mode.THREE_CHECK ->
                if (s.checksBy(mover) >= 3) GameResult(mover, "${mover.label} gave the third check") else null
            Mode.ANTICHESS ->
                if (s.board.none { it?.side == s.toMove }) GameResult(s.toMove, "${s.toMove.label} lost every piece and wins") else null
            Mode.RACING_KINGS -> {
                val white = kingSquare(s.board, Side.WHITE)?.let { rank(it) == 7 } == true
                val black = kingSquare(s.board, Side.BLACK)?.let { rank(it) == 7 } == true
                if (s.toMove == Side.BLACK) {
                    // White just arrived: Black gets one move to arrive too, if it can.
                    val bk = kingSquare(s.board, Side.BLACK)
                    if (white && allLegalMoves(s).none { it.from == bk && rank(it.to) == 7 }) {
                        GameResult(Side.WHITE, "White's king reached the 8th rank")
                    } else null
                } else when {
                    white && black -> GameResult(null, "Both kings reached the 8th rank")
                    white -> GameResult(Side.WHITE, "White's king reached the 8th rank")
                    black -> GameResult(Side.BLACK, "Black's king reached the 8th rank")
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun positionKey(s: GameState): String = buildString {
        for (p in s.board) {
            if (p == null) { append('.'); continue }
            append(p.side.ordinal).append(p.kind.ordinal).append(p.mood.ordinal).append(p.countdown)
            if (p.kind == Kind.KING || p.kind == Kind.ROOK) append(if (p.moved) 'm' else 'u')
            for (perk in p.perks) append(perk.ordinal)
            append(',')
        }
        append(s.toMove.ordinal).append(':').append(s.epSquare ?: -1)
    }

    private fun describe(s: GameState, m: Move, p: Piece, captured: Piece?, bonus: Boolean, struck: Boolean): String {
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
                append(if (captured != null || struck) "x" else "–")
                append(squareName(m.to))
                if (m.promotion != null) append("=").append(m.promotion.letter)
                if (m.enPassant) append(" e.p.")
            }
        }
        return "$prefix $body"
    }

    fun moodLabel(mood: Mood, mode: Mode): String = when (mood) {
        Mood.CALM -> when (mode) {
            Mode.EMOTIONAL -> "Innocent"
            Mode.PLAGUE -> "Healthy"
            else -> "Calm"
        }
        Mood.UNAFFECTED -> if (mode == Mode.EMOTIONAL) "Unaffected" else "Calm"
        Mood.GUILTY -> if (mode == Mode.EMOTIONAL) "Guilt-ridden" else "Remorseful"
        Mood.DEVASTATED -> "Devastated"
        Mood.PSYCHOPATH -> "Psychopath"
        Mood.INFECTED -> "Infected"
        Mood.IMMUNE -> "Immune"
    }

    fun moodDescription(mood: Mood, mode: Mode): String = when (mood) {
        Mood.CALM -> when (mode) {
            Mode.EMOTIONAL -> "Has not captured yet. Its first capture rolls the die."
            Mode.CLASSIC -> "May capture. Capturing makes it remorseful."
            Mode.PLAGUE -> "Healthy. Capturing infects it, and so does a sick piece moving next to it. Bishops never fall ill."
            Mode.ROGUELIKE -> "Capture to level up and pick a perk."
            else -> "An ordinary piece."
        }
        Mood.UNAFFECTED -> if (mode == Mode.EMOTIONAL) "Its roll is used. Plays like a normal piece." else "May capture."
        Mood.GUILTY -> if (mode == Mode.EMOTIONAL) {
            "Moves normally but cannot capture or give check until absolved by a bishop next to it. A bishop can absolve itself."
        } else {
            "Moves and blocks but cannot capture or give check until a friendly bishop moves next to it. A remorseful bishop can forgive itself (uses the turn)."
        }
        Mood.DEVASTATED -> "Frozen and gives no check. Leaves the board after its owner's third turn unless absolved. A bishop can absolve itself."
        Mood.PSYCHOPATH -> "Every capture earns one mandatory extra move with this piece."
        Mood.INFECTED -> "Dies after its owner's ${INCUBATION_TURNS}th turn unless a bishop next to it cures it. Infects healthy pieces next to wherever it moves."
        Mood.IMMUNE -> "Cured. It can never catch the plague again."
    }

    private val KNIGHT_STEPS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
    private val ORTHOGONAL = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val DIAGONAL = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val KING_STEPS = ORTHOGONAL + DIAGONAL
}
