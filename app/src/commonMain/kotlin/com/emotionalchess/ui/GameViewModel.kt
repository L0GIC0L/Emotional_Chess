package com.emotionalchess.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.emotionalchess.engine.Engine
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Hint
import com.emotionalchess.engine.History
import com.emotionalchess.engine.Kind
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Move
import com.emotionalchess.engine.Perk
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side
import com.emotionalchess.engine.Snapshot
import com.emotionalchess.net.Endpoint
import com.emotionalchess.net.NetAction
import com.emotionalchess.net.NetMessage
import com.emotionalchess.net.PeerLink
import com.emotionalchess.net.PeerListener
import com.emotionalchess.net.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** An online game this page was part of, so a reload (common on phones) can pick it up again. */
@Serializable
data class LinkSave(
    val role: Role,
    val room: String,
    val myName: String,
    val mySide: Side? = null,
    val peerName: String? = null,
    /** Host still waiting for the friend: the game that starts when they join. */
    val hosted: GameState? = null,
)

@Serializable
data class SaveData(
    val game: GameState? = null,
    /** Undo states in the first save format (whole states). Read for old saves only. */
    val undo: List<GameState> = emptyList(),
    val history: List<Snapshot> = emptyList(),
    val flipped: Boolean = false,
    val link: LinkSave? = null,
)

interface GameStore {
    fun load(): SaveData?
    fun save(game: GameState?, undo: List<GameState>, flipped: Boolean, link: LinkSave?)
}

/** Where a save lives: a file on Android and desktop, browser storage on the web. */
interface TextStorage {
    fun read(): String?
    fun write(text: String)
}

/** Saves as JSON in the background, one write at a time. */
class JsonGameStore(private val storage: TextStorage) : GameStore {
    private val json = Json { ignoreUnknownKeys = true }
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    override fun load(): SaveData? = runCatching { json.decodeFromString<SaveData>(storage.read()!!) }.getOrNull()

    override fun save(game: GameState?, undo: List<GameState>, flipped: Boolean, link: LinkSave?) {
        scope.launch {
            runCatching {
                val history = if (game != null) History.compact(game, undo) else emptyList()
                storage.write(json.encodeToString(SaveData.serializer(), SaveData(game, history = history, flipped = flipped, link = link)))
            }
        }
    }
}

data class RollReveal(
    val roll: Int,
    val mood: Mood,
    val pieceName: String,
    val kind: Kind,
    val side: Side,
    /** True when the app rolled: play the tumble. A typed-in physical roll is revealed at once. */
    val animated: Boolean,
)

enum class FeedbackKind { MOVE, CAPTURE, CHECK, GAME_OVER }

/** One-shot haptic cue; a fresh [seq] makes every event distinct. */
data class Feedback(val seq: Long, val kind: FeedbackKind)

@Serializable
enum class Role { HOST, GUEST }

enum class LinkPhase {
    /** Guest: looking for phones that host a game. */
    LOOKING,
    /** Host: waiting for someone to join. */
    WAITING,
    /** Guest: asked to join, waiting for the host to accept and send the game. */
    CONNECTING,
    PLAYING,
    /** The connection dropped; both phones try to find each other again. */
    LOST,
}

/** A game between two phones in the same room. */
data class OnlineState(
    val role: Role,
    val phase: LinkPhase,
    val myName: String,
    /** The side this phone plays; a guest learns it from the host. */
    val mySide: Side? = null,
    val peerName: String? = null,
    /** Guest: hosts found nearby. */
    val found: List<Endpoint> = emptyList(),
    /** Host: someone asking to join, and the pairing code both phones show. */
    val request: Endpoint? = null,
    val code: String? = null,
    /** Online host: the room code the friend joins with. */
    val roomCode: String? = null,
    val error: String? = null,
    /** The other player has offered a draw. */
    val drawOffered: Boolean = false,
    val notice: String? = null,
)

class GameViewModel(
    private val store: GameStore,
    /** Creates the link to another phone; null when two-phone play is unavailable (tests, previews). */
    private val linkFactory: (() -> PeerLink)? = null,
    /** Where background results are applied: the main thread in the apps; tests pass their own. */
    private val uiContext: CoroutineContext = mainOrUnconfined(),
    /** Online (web) games are picked up again after a reload. Nearby links end with the app. */
    private val resumeLinks: Boolean = linkStyle == LinkStyle.ONLINE,
) {
    /** Whether this platform can link two devices (Android, over Nearby). */
    val canLink: Boolean get() = linkFactory != null

    var game by mutableStateOf<GameState?>(null)
        private set
    private var undoStack: List<GameState> = emptyList()
    var canUndo by mutableStateOf(false)
        private set
    var flipped by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Int?>(null)
        private set
    var promotionChoices by mutableStateOf<List<Move>?>(null)
        private set
    var rollReveal by mutableStateOf<RollReveal?>(null)
        private set
    var showResult by mutableStateOf(true)
        private set
    var feedback by mutableStateOf<Feedback?>(null)
        private set
    private var feedbackSeq = 0L

    /** The latest hint, shown until the position changes. */
    var hint by mutableStateOf<Hint?>(null)
        private set
    var thinking by mutableStateOf(false)
        private set
    private val hintScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var hintJob: Job? = null
    private var hintRequest = 0L

    /** Two-phone game, or null when both players share this phone. */
    var online by mutableStateOf<OnlineState?>(null)
        private set
    private var link: PeerLink? = null
    /** Actions applied since the last full state from the host; must match on both phones. */
    private var seq = 0
    /** Host: the game waiting for a guest to join. */
    private var hostedGame: GameState? = null
    /** Guest: the host's advertised name, to find it again after a dropped connection. */
    private var hostEndpointName: String? = null
    /** Declared before init, which may resume a link (property initialisers run in order). */
    private val listener: PeerListener by lazy { LinkEvents() }

    init {
        store.load()?.let { data ->
            game = data.game
            undoStack = data.game?.let { g -> if (data.history.isNotEmpty()) History.expand(g, data.history) else data.undo }.orEmpty()
            canUndo = undoStack.isNotEmpty()
            flipped = data.flipped
            data.link?.takeIf { resumeLinks }?.let(::resume)
        }
    }

    /** Back after a reload: reopen the same room (host), or find it again (guest). */
    private fun resume(save: LinkSave) {
        val l = linkFactory?.invoke() ?: return
        link = l
        hostedGame = save.hosted
        if (save.role == Role.HOST) {
            val phase = if (game != null) LinkPhase.LOST else LinkPhase.WAITING
            online = OnlineState(Role.HOST, phase, save.myName, save.mySide, save.peerName, roomCode = save.room)
            l.host(save.myName, listener, room = save.room)
        } else {
            hostEndpointName = "Room ${save.room}"
            online = OnlineState(Role.GUEST, LinkPhase.LOST, save.myName, save.mySide, save.peerName)
            l.join(save.myName, listener, rejoin = save.room)
        }
    }

    /** Opened from an invitation link: join that room, unless this page is already in it. */
    fun acceptInvitation(code: String) {
        val o = online
        val room = if (o?.role == Role.HOST) o.roomCode else joiningRoom
        if (room != code) closeGame()
    }

    // ----------------------------------------------------------- derived helpers

    val targets: List<Move>
        get() {
            val g = game ?: return emptyList()
            val s = selected ?: return emptyList()
            if (!myTurn(g)) return emptyList()
            return Rules.legalMovesFrom(g, s)
        }

    // ----------------------------------------------------------- actions

    fun newGame(mode: Mode, white: String, black: String, hints: Int = 3) {
        clearHint()
        game = Rules.newGame(mode, white, black, hints = hints)
        undoStack = emptyList()
        selected = null
        flipped = false
        showResult = true
        rollReveal = null
        persist()
    }

    fun closeGame() {
        leaveOnline()
        clearHint()
        game = null
        undoStack = emptyList()
        selected = null
        rollReveal = null
        persist()
    }

    /** In a two-phone game, only the player whose turn it is may act, and only while connected. */
    fun myTurn(g: GameState? = game): Boolean {
        if (g == null) return false
        val o = online ?: return true
        return o.phase == LinkPhase.PLAYING && o.mySide == g.toMove
    }

    fun onSquareTapped(sq: Int) {
        val g = game ?: return
        if (g.result != null || g.pendingRoll != null || g.pendingPerk != null) return
        val sel = selected
        if (sel != null && myTurn(g)) {
            // Castling can also be entered by tapping the rook (needed in Chess960).
            val moves = Rules.legalMovesFrom(g, sel).filter { it.to == sq || (it.castle && it.rookFrom == sq) }
            when {
                moves.size > 1 && moves.all { it.promotion != null } -> { promotionChoices = moves; return }
                moves.isNotEmpty() -> { act(NetAction.Play(moves.first())); return }
            }
        }
        val p = g.board[sq]
        selected = if (p != null && sq != sel) sq else g.bonusSquare
    }

    fun choosePromotion(move: Move?) {
        val g = game
        promotionChoices = null
        if (move != null && g != null && myTurn(g)) act(NetAction.Play(move))
    }

    fun absolveSelected() {
        val g = game ?: return
        val s = selected ?: return
        if (myTurn(g) && Rules.absolverFor(g, s) != null) act(NetAction.Heal(s))
    }

    /** [roll] null = let the app roll. */
    fun roll(roll: Int?) {
        val g = game ?: return
        if (g.pendingRoll == null || !myTurn(g)) return
        act(NetAction.Roll(roll ?: Random.nextInt(1, 7), animated = roll == null))
    }

    fun dismissRoll() { rollReveal = null }

    fun choosePerk(perk: Perk) {
        val g = game ?: return
        if (g.pendingPerk == null || perk !in g.perkOffer || !myTurn(g)) return
        act(NetAction.Pick(perk))
    }

    /** Thinks in the background; counts against the player's hints only when one is found. */
    fun requestHint() {
        val g = game ?: return
        if (thinking || g.result != null || g.pendingRoll != null || g.hintsLeft(g.toMove) <= 0 || !myTurn(g)) return
        thinking = true
        val request = ++hintRequest
        hintJob = hintScope.launch {
            val found = runCatching { Engine.bestAction(g, budgetMs = hintBudgetMs) }.getOrNull()
            // Back on the main thread, where every other change to the game happens.
            withContext(uiContext) {
                // Ignore a search that finished after the position changed or the hint was cleared.
                if (request != hintRequest) return@withContext
                if (game === g && found != null) {
                    hint = found
                    act(NetAction.HintUsed(g.toMove))
                }
                thinking = false
            }
        }
    }

    fun clearHint() {
        hintRequest++
        hintJob?.cancel()
        hintJob = null
        hint = null
        thinking = false
    }

    /** Stops background work and the link; call when the screen goes away for good. */
    fun close() {
        hintScope.cancel()
        link?.stop()
    }

    fun resign(side: Side) {
        val g = game ?: return
        val o = online
        if (g.result != null || (o != null && (o.mySide != side || o.phase != LinkPhase.PLAYING))) return
        act(NetAction.Resign(side))
    }

    fun agreeDraw() {
        val g = game ?: return
        if (g.result == null && online == null) act(NetAction.Draw)
    }

    fun dismissResult() { showResult = false }

    fun undo() {
        // Two phones would disagree about the position, so there is no undo between phones.
        if (undoStack.isEmpty() || online != null) return
        clearHint()
        game = undoStack.last()
        undoStack = undoStack.dropLast(1)
        selected = game?.bonusSquare
        rollReveal = null
        showResult = true
        persist()
    }

    fun flip() {
        flipped = !flipped
        persist()
    }

    /** Applies an action taken on this phone; in a two-phone game, also sends it to the other phone. */
    private fun act(action: NetAction) {
        val g = game ?: return
        val next = runCatching { Protocol.apply(g, action) }.getOrNull() ?: return
        val before = seq
        seq = before + 1
        show(g, next, action)
        if (online != null) send(NetMessage.Act(before, action, Protocol.fingerprint(next)))
    }

    /** Puts a new state on screen with the right side effects for the action that produced it. */
    private fun show(before: GameState, next: GameState, action: NetAction) {
        when (action) {
            is NetAction.HintUsed -> { game = next; persist() }
            is NetAction.Roll -> {
                val p = before.pendingRoll?.let { before.board[it] }
                if (p != null) rollReveal = RollReveal(action.value, Rules.moodForRoll(action.value), p.name, p.kind, p.side, action.animated)
                commit(next, haptics = false)
            }
            else -> commit(next)
        }
    }

    private fun commit(next: GameState, haptics: Boolean = true) {
        clearHint()
        val before = game
        before?.let { undoStack = (undoStack + it).takeLast(MAX_UNDO) }
        game = next
        selected = next.bonusSquare
        showResult = true
        if (haptics || next.result != null) {
            val kind = when {
                next.result != null -> FeedbackKind.GAME_OVER
                Rules.inCheck(next) -> FeedbackKind.CHECK
                before != null && next.fallen.size > before.fallen.size -> FeedbackKind.CAPTURE
                else -> FeedbackKind.MOVE
            }
            feedback = Feedback(++feedbackSeq, kind)
        }
        persist()
    }

    // ----------------------------------------------------------- two phones

    /** Hosts [mode] for a friend's phone. [side] null = pick at random. */
    fun hostGame(mode: Mode, myName: String, hints: Int, side: Side?) {
        val l = linkFactory?.invoke() ?: run { online = OnlineState(Role.HOST, LinkPhase.WAITING, myName, error = NO_LINK); return }
        leaveOnline()
        val me = myName.ifBlank { "Host" }
        val mySide = side ?: if (Random.nextBoolean()) Side.WHITE else Side.BLACK
        hostedGame = Rules.newGame(
            mode,
            whiteName = if (mySide == Side.WHITE) me else "Friend",
            blackName = if (mySide == Side.BLACK) me else "Friend",
            hints = hints,
        )
        link = l
        online = OnlineState(Role.HOST, LinkPhase.WAITING, me, mySide)
        l.host("$me · ${mode.emoji} ${mode.title}", listener)
    }

    /** Looks for a friend's phone that is hosting; online, joins the room [code] straight away. */
    fun joinGame(myName: String, code: String? = null) {
        val l = linkFactory?.invoke() ?: run { online = OnlineState(Role.GUEST, LinkPhase.LOOKING, myName, error = NO_LINK); return }
        leaveOnline()
        link = l
        online = OnlineState(Role.GUEST, LinkPhase.LOOKING, myName.ifBlank { "Guest" })
        l.join(online!!.myName, listener)
        val room = code?.trim()?.uppercase()
        if (!room.isNullOrEmpty()) connectTo(Endpoint(room, "Room $room"))
    }

    /** Online guest: the room code being joined. */
    val joiningRoom: String? get() = hostEndpointName?.removePrefix("Room ")

    fun connectTo(endpoint: Endpoint) {
        val o = online ?: return
        hostEndpointName = endpoint.name
        // Reconnecting keeps showing "connection lost" until the game arrives.
        online = o.copy(phase = if (o.phase == LinkPhase.LOST) LinkPhase.LOST else LinkPhase.CONNECTING, error = null)
        link?.connect(endpoint.id)
    }

    fun answerJoinRequest(accept: Boolean) {
        val o = online ?: return
        val r = o.request ?: return
        online = o.copy(request = null, code = null)
        if (accept) link?.accept(r.id) else link?.reject(r.id)
    }

    fun offerDraw() {
        if (online?.phase == LinkPhase.PLAYING && game?.result == null) {
            send(NetMessage.DrawOffer)
            online = online?.copy(notice = "Draw offered")
        }
    }

    fun answerDraw(accept: Boolean) {
        val o = online ?: return
        online = o.copy(drawOffered = false)
        if (accept) act(NetAction.Draw) else send(NetMessage.DrawDeclined)
    }

    fun dismissNotice() { online = online?.copy(notice = null, error = null) }

    /** Ends the link. A game in progress stays on this phone as an ordinary game. */
    fun leaveOnline() {
        link?.stop()
        link = null
        online = null
        hostedGame = null
        hostEndpointName = null
        persist()
    }

    private fun send(m: NetMessage) = link?.send(Protocol.encode(m))

    private fun sendState() {
        val g = game ?: return
        val side = online?.mySide ?: return
        send(NetMessage.State(g, side.other, seq))
    }

    private inner class LinkEvents : PeerListener {
        override fun onReady(code: String) {
            online = online?.copy(roomCode = code, error = null)
            persist()
        }

        override fun onFound(endpoint: Endpoint) {
            val o = online ?: return
            // After a dropped connection or a reload, go straight back to the same host – every time
            // it is offered, since an earlier attempt may have failed.
            if (o.phase == LinkPhase.LOST && endpoint.name == hostEndpointName) {
                connectTo(endpoint)
                return
            }
            if (endpoint.id in o.found.map { it.id }) return
            online = o.copy(found = o.found + endpoint)
        }

        override fun onLost(endpointId: String) {
            online = online?.let { o -> o.copy(found = o.found.filter { it.id != endpointId }) }
        }

        override fun onRequest(endpoint: Endpoint, code: String) {
            val o = online ?: return
            if (o.role == Role.GUEST) { online = o.copy(code = code); return }
            // The same friend coming back after a dropped connection needs no second confirmation.
            if (o.phase == LinkPhase.LOST && endpoint.name == o.peerName) link?.accept(endpoint.id)
            else online = o.copy(request = endpoint, code = code)
        }

        override fun onConnected(endpoint: Endpoint) {
            val o = online ?: return
            online = o.copy(found = emptyList(), request = null, error = null)
            send(NetMessage.Hello(Protocol.VERSION, o.myName))
        }

        override fun onDisconnected() {
            val o = online ?: return
            if (o.phase == LinkPhase.WAITING || o.phase == LinkPhase.LOOKING) return
            online = o.copy(phase = LinkPhase.LOST, found = emptyList(), code = null, drawOffered = false)
            persist()
            val l = link ?: return
            if (o.role == Role.HOST) {
                val g = game
                l.host("${o.myName} · ${g?.mode?.emoji ?: ""} ${g?.mode?.title ?: ""}", this)
            } else l.join(o.myName, this)
        }

        override fun onMessage(text: String) {
            val m = Protocol.decode(text) ?: return
            receive(m)
        }

        override fun onError(message: String) {
            online = online?.copy(error = message)
        }
    }

    private fun receive(m: NetMessage) {
        val o = online ?: return
        when (m) {
            is NetMessage.Hello -> {
                if (m.version != Protocol.VERSION) {
                    online = o.copy(error = "Your friend's app is a different version. Update both phones.")
                    return
                }
                online = o.copy(peerName = m.name)
                if (o.role != Role.HOST) return
                hostedGame?.let { fresh ->
                    // First connection: the guest's name goes on the other side, and the game begins.
                    val other = o.mySide?.other ?: Side.BLACK
                    game = if (other == Side.WHITE) fresh.copy(whiteName = m.name) else fresh.copy(blackName = m.name)
                    hostedGame = null
                    undoStack = emptyList()
                    seq = 0
                    selected = null
                    rollReveal = null
                    showResult = true
                    flipped = o.mySide == Side.BLACK
                }
                online = online?.copy(phase = LinkPhase.PLAYING)
                persist()
                sendState()
            }
            is NetMessage.State -> {
                if (o.role != Role.GUEST) return
                clearHint()
                game = m.game
                seq = m.seq
                undoStack = emptyList()
                selected = m.game.bonusSquare
                showResult = true
                flipped = m.yourSide == Side.BLACK
                online = o.copy(phase = LinkPhase.PLAYING, mySide = m.yourSide, code = null)
                persist()
            }
            is NetMessage.Act -> {
                val g = game
                val next = if (g != null && m.seq == seq) runCatching { Protocol.apply(g, m.action) }.getOrNull() else null
                if (g == null || next == null || Protocol.fingerprint(next) != m.check) {
                    // Out of step: the host's game is the truth.
                    if (o.role == Role.HOST) sendState() else send(NetMessage.NeedState)
                    return
                }
                seq++
                show(g, next, m.action)
            }
            NetMessage.NeedState -> if (o.role == Role.HOST) sendState()
            NetMessage.DrawOffer -> online = o.copy(drawOffered = true)
            NetMessage.DrawDeclined -> online = o.copy(notice = "${o.peerName ?: "Your friend"} declined the draw")
        }
    }

    private fun persist() {
        canUndo = undoStack.isNotEmpty() && online == null
        store.save(game, undoStack, flipped, linkSave())
    }

    private fun linkSave(): LinkSave? {
        if (!resumeLinks) return null
        val o = online ?: return null
        val room = (if (o.role == Role.HOST) o.roomCode else joiningRoom) ?: return null
        return LinkSave(o.role, room, o.myName, o.mySide, o.peerName, hostedGame)
    }

    companion object {
        /** The main thread where there is one; otherwise results are applied where they finish. */
        fun mainOrUnconfined(): CoroutineContext = try {
            Dispatchers.Main.also { it.isDispatchNeeded(EmptyCoroutineContext) }
        } catch (_: Throwable) {
            Dispatchers.Unconfined
        }

        private const val MAX_UNDO = 150
        private const val NO_LINK = "Two-phone play is not available on this device."
    }
}
