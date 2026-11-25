package com.emotionalchess.net

import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side
import com.emotionalchess.ui.GameStore
import com.emotionalchess.ui.GameViewModel
import com.emotionalchess.ui.LinkPhase
import com.emotionalchess.ui.SaveData
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An in-memory stand-in for Nearby: messages queue up until [flush], like real asynchronous delivery. */
class FakeNetwork {
    private val queue = ArrayDeque<() -> Unit>()
    private val links = mutableListOf<FakeLink>()
    /** False = the phones are out of range and cannot find each other. */
    var reachable = true

    /** Back in range: discovering phones see the hosting ones again. */
    fun restore() {
        reachable = true
        for (d in links.filter { it.discovering }) for (h in links.filter { it.hosting }) {
            queue += { d.listener?.onFound(Endpoint(h.id, h.name)) }
        }
    }

    fun flush() {
        var guard = 0
        while (queue.isNotEmpty()) {
            queue.removeFirst()()
            check(++guard < 10_000) { "message storm" }
        }
    }

    fun newLink(): FakeLink = FakeLink("L${links.size + 1}").also { links += it }

    /** Cuts the connection, as walking out of range would. */
    fun drop() {
        for (l in links) {
            if (l.peer != null) {
                l.peer = null
                queue += { l.listener?.onDisconnected() }
            }
        }
    }

    inner class FakeLink(val id: String) : PeerLink {
        var listener: PeerListener? = null
        var peer: FakeLink? = null
        var hosting = false
        var discovering = false
        var name = ""
        val sent = mutableListOf<String>()

        override fun host(name: String, listener: PeerListener) {
            this.listener = listener; this.name = name; hosting = true
            if (reachable) for (d in links.filter { it.discovering }) queue += { d.listener?.onFound(Endpoint(id, name)) }
        }

        override fun join(name: String, listener: PeerListener) {
            this.listener = listener; this.name = name; discovering = true
            if (reachable) for (h in links.filter { it.hosting }) queue += { listener.onFound(Endpoint(h.id, h.name)) }
        }

        override fun connect(endpointId: String) {
            val host = links.single { it.id == endpointId }
            queue += {
                host.listener?.onRequest(Endpoint(id, name), "4821")
                listener?.onRequest(Endpoint(host.id, host.name), "4821")
            }
        }

        override fun accept(endpointId: String) {
            val guest = links.single { it.id == endpointId }
            peer = guest; guest.peer = this
            hosting = false; guest.discovering = false
            queue += {
                listener?.onConnected(Endpoint(guest.id, guest.name))
                guest.listener?.onConnected(Endpoint(id, name))
            }
        }

        override fun reject(endpointId: String) {}

        override fun send(text: String) {
            sent += text
            val p = peer ?: return
            queue += { p.listener?.onMessage(text) }
        }

        override fun stop() {
            peer?.let { other -> other.peer = null; queue += { other.listener?.onDisconnected() } }
            peer = null; hosting = false; discovering = false; listener = null
        }
    }
}

class TwoPhoneTest {
    private fun sq(name: String) = Rules.sq(name[0] - 'a', name[1] - '1')

    private class MemoryStore : GameStore {
        override fun load(): SaveData? = null
        override fun save(game: GameState?, undo: List<GameState>, flipped: Boolean) = Unit
    }

    private class Pair(val net: FakeNetwork, val host: GameViewModel, val guest: GameViewModel, val hostLink: FakeNetwork.FakeLink)

    private fun GameViewModel.tap(from: String, to: String) {
        onSquareTapped(sq(from)); onSquareTapped(sq(to))
    }

    /** Ada hosts as White, Grace joins. */
    private fun connected(mode: Mode, hints: Int = 3): Pair {
        val net = FakeNetwork()
        val hostLink = net.newLink()
        val guestLink = net.newLink()
        val host = GameViewModel(MemoryStore(), linkFactory = { hostLink }, uiContext = Dispatchers.Unconfined)
        val guest = GameViewModel(MemoryStore(), linkFactory = { guestLink }, uiContext = Dispatchers.Unconfined)
        host.hostGame(mode, "Ada", hints, Side.WHITE)
        guest.joinGame("Grace")
        net.flush()
        val found = guest.online!!.found.single()
        assertTrue(found.name.startsWith("Ada"))
        guest.connectTo(found)
        net.flush()
        assertEquals("4821", host.online!!.code)
        assertEquals("both phones show the same code", "4821", guest.online!!.code)
        host.answerJoinRequest(true)
        net.flush()
        return Pair(net, host, guest, hostLink)
    }

    private fun assertInStep(p: Pair) {
        assertNotNull(p.host.game)
        assertEquals(p.host.game, p.guest.game)
    }

    @Test
    fun hostAndJoinThenPlay() {
        val p = connected(Mode.STANDARD)
        assertEquals(LinkPhase.PLAYING, p.host.online!!.phase)
        assertEquals(LinkPhase.PLAYING, p.guest.online!!.phase)
        assertEquals(Side.BLACK, p.guest.online!!.mySide)
        assertTrue("Black sees the board from its side", p.guest.flipped)
        assertEquals("Ada", p.guest.game!!.whiteName)
        assertEquals("Grace", p.host.game!!.blackName)
        assertInStep(p)

        p.guest.tap("e2", "e4")
        p.net.flush()
        assertEquals("not Grace's turn", Side.WHITE, p.host.game!!.toMove)

        p.host.tap("e2", "e4"); p.net.flush()
        p.guest.tap("e7", "e5"); p.net.flush()
        assertInStep(p)
        assertEquals(Side.WHITE, p.guest.game!!.toMove)
        assertFalse("no undo between phones", p.host.canUndo)
    }

    @Test
    fun diceRollReachesTheOtherPhone() {
        val p = connected(Mode.EMOTIONAL)
        p.host.tap("e2", "e4"); p.net.flush()
        p.guest.tap("d7", "d5"); p.net.flush()
        p.host.tap("e4", "d5"); p.net.flush()
        assertEquals(sq("d5"), p.guest.game!!.pendingRoll)

        p.guest.roll(1)
        assertEquals("Grace cannot roll for Ada", sq("d5"), p.host.game!!.pendingRoll)

        p.host.roll(null)
        p.net.flush()
        assertInStep(p)
        val reveal = p.guest.rollReveal!!
        assertTrue("the friend's phone plays the tumble too", reveal.animated)
        assertEquals(Rules.moodForRoll(reveal.roll), p.guest.game!!.board[sq("d5")]!!.mood)
    }

    @Test
    fun perkChoiceAndHintsStayInStep() {
        val p = connected(Mode.ROGUELIKE)
        p.host.tap("e2", "e4"); p.net.flush()
        p.guest.tap("d7", "d5"); p.net.flush()
        p.host.tap("e4", "d5"); p.net.flush()
        val offer = p.guest.game!!.perkOffer
        assertEquals("both phones offer the same perks", p.host.game!!.perkOffer, offer)
        p.host.choosePerk(offer.first()); p.net.flush()
        assertInStep(p)

        p.guest.requestHint()
        val deadline = System.currentTimeMillis() + 10_000
        while (p.guest.thinking && System.currentTimeMillis() < deadline) Thread.sleep(10)
        p.net.flush()
        assertNotNull(p.guest.hint)
        assertEquals(1, p.host.game!!.blackHints)
        assertInStep(p)
    }

    @Test
    fun drawOfferAccepted() {
        val p = connected(Mode.STANDARD)
        p.guest.offerDraw(); p.net.flush()
        assertTrue(p.host.online!!.drawOffered)
        p.host.answerDraw(true); p.net.flush()
        assertEquals("Draw by agreement", p.guest.game!!.result?.reason)
        assertInStep(p)
    }

    @Test
    fun onlyYourOwnSideCanResign() {
        val p = connected(Mode.STANDARD)
        p.guest.resign(Side.WHITE); p.net.flush()
        assertNull(p.host.game!!.result)
        p.guest.resign(Side.BLACK); p.net.flush()
        assertEquals(Side.WHITE, p.host.game!!.result?.winner)
        assertInStep(p)
    }

    @Test
    fun outOfStepPhonesResync() {
        val p = connected(Mode.STANDARD)
        p.host.tap("e2", "e4"); p.net.flush()
        // A garbled action from the host's link: wrong sequence number.
        p.hostLink.send(Protocol.encode(NetMessage.Act(99, NetAction.Resign(Side.BLACK), 0)))
        p.net.flush()
        assertNull("the bad action was not applied", p.guest.game!!.result)
        assertInStep(p)
    }

    @Test
    fun droppedConnectionBlocksMovesThenReconnects() {
        val p = connected(Mode.PLAGUE)
        p.host.tap("e2", "e4"); p.net.flush()

        p.net.reachable = false
        p.net.drop(); p.net.flush()
        assertEquals(LinkPhase.LOST, p.host.online!!.phase)
        assertEquals(LinkPhase.LOST, p.guest.online!!.phase)
        p.guest.tap("e7", "e5")
        assertEquals("no moves while the link is down", Side.BLACK, p.guest.game!!.toMove)

        p.net.restore(); p.net.flush()
        assertEquals("the same friend reconnects without a new pairing", LinkPhase.PLAYING, p.host.online!!.phase)
        assertEquals(LinkPhase.PLAYING, p.guest.online!!.phase)
        assertInStep(p)
        p.guest.tap("e7", "e5"); p.net.flush()
        assertInStep(p)
        assertEquals(Side.WHITE, p.host.game!!.toMove)
    }

    @Test
    fun leavingKeepsTheGameOnThisPhone() {
        val p = connected(Mode.STANDARD)
        p.host.tap("e2", "e4"); p.net.flush()
        p.guest.leaveOnline()
        assertNull(p.guest.online)
        p.guest.tap("e7", "e5")
        assertEquals("an ordinary one-phone game now", Side.WHITE, p.guest.game!!.toMove)
        assertTrue(p.guest.canUndo)
    }

    @Test
    fun framerSplitsAndJoinsLongMessages() {
        val text = buildString { repeat(5000) { append("move $it 😈🦠 ") } }
        val frames = Framer.split(text, 7)
        assertTrue(frames.size > 1)
        assertTrue(frames.all { it.toByteArray(Charsets.UTF_8).size < 32 * 1024 })
        val joiner = Framer.Joiner()
        var out: String? = null
        for (f in frames.reversed()) out = joiner.receive(f) ?: out
        assertEquals(text, out)
        assertEquals("hi", Framer.Joiner().receive(Framer.split("hi", 1).single()))
    }

    @Test
    fun longGamesStillFitThroughTheLink() {
        // A long game's state is bigger than one Nearby payload; it must survive framing.
        var g = Rules.newGame(Mode.EMOTIONAL)
        val shuffle = listOf("g1" to "f3", "g8" to "f6", "f3" to "g1", "f6" to "g8", "b1" to "c3", "b8" to "c6", "c3" to "b1", "c6" to "b8")
        for (i in 0 until 7) g = Rules.play(g, Rules.legalMovesFrom(g, sq(shuffle[i].first)).first { it.to == sq(shuffle[i].second) })
        val big = g.copy(log = List(3000) { "${it}. Nb1–c3 😈" })
        val text = Protocol.encode(NetMessage.State(big, Side.BLACK, 0))
        val joiner = Framer.Joiner()
        val back = Framer.split(text, 1).mapNotNull { joiner.receive(it) }.single()
        assertEquals(big, (Protocol.decode(back) as NetMessage.State).game)
        assertEquals(Mood.CALM, big.board[sq("e2")]!!.mood)
    }
}
