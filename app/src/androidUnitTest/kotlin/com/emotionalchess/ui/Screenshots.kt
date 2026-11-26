package com.emotionalchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.emotionalchess.engine.Absolution
import com.emotionalchess.engine.Fallen
import com.emotionalchess.engine.GameResult
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Kind
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Move
import com.emotionalchess.engine.Perk
import com.emotionalchess.engine.Piece
import com.emotionalchess.engine.RollRecord
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

/**
 * Renders the main screens on the JVM. Doubles as a smoke test that every screen composes.
 * `./gradlew recordPaparazziDebug` writes the PNGs to app/src/test/snapshots/images.
 */
class Screenshots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.5)

    private fun sq(name: String) = Rules.sq(name[0] - 'a', name[1] - '1')

    /** A mid-game position with every feeling on the board. */
    private fun sampleGame(): GameState {
        var id = 0
        val board = MutableList<Piece?>(64) { null }
        fun put(s: String, side: Side, kind: Kind, mood: Mood = Mood.CALM, countdown: Int = 0) {
            board[sq(s)] = Piece(side, kind, mood, countdown = countdown, moved = true, id = id++)
        }
        val w = Side.WHITE
        val b = Side.BLACK
        put("g1", w, Kind.KING); put("d1", w, Kind.QUEEN); put("a1", w, Kind.ROOK); put("f1", w, Kind.ROOK)
        put("c4", w, Kind.BISHOP); put("f3", w, Kind.KNIGHT, Mood.PSYCHOPATH); put("e5", w, Kind.PAWN, Mood.GUILTY)
        for (s in listOf("a2", "b2", "c3", "f2", "g2", "h2")) put(s, w, Kind.PAWN)
        put("d4", w, Kind.PAWN, Mood.UNAFFECTED)
        put("g8", b, Kind.KING); put("d7", b, Kind.QUEEN, Mood.GUILTY); put("a8", b, Kind.ROOK); put("f8", b, Kind.ROOK)
        put("b7", b, Kind.BISHOP); put("e7", b, Kind.KNIGHT, Mood.DEVASTATED, countdown = 2)
        for (s in listOf("a7", "b6", "c7", "f7", "g7", "h6")) put(s, b, Kind.PAWN)
        return GameState(
            mode = Mode.EMOTIONAL,
            whiteName = "Ada",
            blackName = "Grace",
            board = board,
            toMove = Side.WHITE,
            fullmove = 14,
            lastMove = Move(sq("d8"), sq("d7")),
            log = listOf(
                "11. Nd2–f3",
                "11… Ng6xe7",
                "   🎲 Rolled 1 – Black Knight is devastated",
                "12. Pd4xe5",
                "   🎲 Rolled 6 – White Pawn is psychopath",
                "   ↳ bonus: Pe5–e6",
                "12… Qd8xe6",
                "   🎲 Rolled 3 – Black Queen is guilt-ridden",
                "13. Nf3xe5",
                "13… Qd8–d7",
            ),
            fallen = listOf(
                Fallen(b, Kind.PAWN), Fallen(w, Kind.PAWN), Fallen(b, Kind.KNIGHT), Fallen(w, Kind.BISHOP),
                Fallen(b, Kind.PAWN), Fallen(w, Kind.KNIGHT, despair = true), Fallen(b, Kind.BISHOP),
            ),
            rolls = listOf(RollRecord(w, Kind.KNIGHT, 6), RollRecord(b, Kind.QUEEN, 3), RollRecord(b, Kind.KNIGHT, 1)),
        )
    }

    private fun vmFor(game: GameState, select: String? = null): GameViewModel {
        val vm = GameViewModel(object : GameStore {
            override fun load() = SaveData(game = game)
            override fun save(game: GameState?, undo: List<GameState>, flipped: Boolean, link: LinkSave?) = Unit
        }, uiContext = Dispatchers.Unconfined)
        if (select != null) vm.onSquareTapped(sq(select))
        return vm
    }

    @androidx.compose.runtime.Composable
    private fun Backdrop(content: @androidx.compose.runtime.Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(BackdropBrush), contentAlignment = Alignment.Center) { content() }
    }

    @Test fun newGame() = paparazzi.snapshot {
        EmotionalChessTheme { Backdrop { NewGameScreen(onStart = { _, _, _, _ -> }) } }
    }

    @Test fun midGame() = paparazzi.snapshot {
        EmotionalChessTheme { App(vmFor(sampleGame(), select = "f3")) }
    }

    @Test fun absolution() = paparazzi.snapshot {
        val g = sampleGame().let { it.copy(toMove = Side.BLACK, lastMove = Move(sq("a2"), sq("a3"))) }
        // Put a black bishop next to the guilty queen so it can be absolved.
        val board = g.board.toMutableList()
        board[sq("c6")] = board[sq("b7")]
        board[sq("b7")] = null
        EmotionalChessTheme { App(vmFor(g.copy(board = board), select = "d7")) }
    }

    @Test fun diceChoose() = paparazzi.snapshot {
        val g = sampleGame().copy(pendingRoll = sq("f3"))
        EmotionalChessTheme { Backdrop { Box(Modifier.padding(24.dp)) { DiceCard(g, null, {}, {}) } } }
    }

    @Test fun diceReveal() = paparazzi.snapshot {
        val g = sampleGame()
        val reveal = RollReveal(6, Mood.PSYCHOPATH, "White Knight", Kind.KNIGHT, Side.WHITE, animated = false)
        EmotionalChessTheme { Backdrop { Box(Modifier.padding(24.dp)) { DiceCard(g, reveal, {}, {}) } } }
    }

    @Test fun report() = paparazzi.snapshot {
        val g = sampleGame().copy(
            result = GameResult(Side.WHITE, "Checkmate"),
            absolutions = listOf(Absolution(Side.BLACK, Kind.QUEEN)),
        )
        EmotionalChessTheme { Backdrop { Box(Modifier.padding(24.dp)) { ReportCard(g, {}, {}, {}) } } }
    }

    @Test fun unfolded() {
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_6.copy(screenWidth = 2208, screenHeight = 1840, orientation = ScreenOrientation.LANDSCAPE)
        )
        paparazzi.snapshot { EmotionalChessTheme { App(vmFor(sampleGame(), select = "e7")) } }
    }

    /** Same position as [sampleGame], dressed up for another mode. */
    private fun restyled(mode: Mode, dress: (Piece, String) -> Piece): GameState {
        val g = sampleGame()
        val board = g.board.mapIndexed { i, p -> p?.let { dress(it.copy(mood = Mood.CALM, countdown = 0), Rules.squareName(i)) } }
        return g.copy(mode = mode, board = board, log = listOf("11. Nd2–f3", "11… Ng6xe7", "12. Pd4xe5", "12… Qd8xe6", "13. Nf3xe5", "13… Qd8–d7"), rolls = emptyList())
    }

    @Test fun plague() = paparazzi.snapshot {
        val g = restyled(Mode.PLAGUE) { p, at ->
            when (at) {
                "e5", "d7" -> p.copy(mood = Mood.INFECTED, countdown = 3)
                "e7" -> p.copy(mood = Mood.INFECTED, countdown = 1)
                "d4", "f3" -> p.copy(mood = Mood.IMMUNE)
                else -> p
            }
        }
        EmotionalChessTheme { App(vmFor(g, select = "e5")) }
    }

    @Test fun roguelike() = paparazzi.snapshot {
        val g = restyled(Mode.ROGUELIKE) { p, at ->
            when (at) {
                "f3" -> p.copy(perks = listOf(Perk.CHARGE, Perk.SHIELD))
                "d7" -> p.copy(perks = listOf(Perk.LEAP))
                "e5" -> p.copy(perks = listOf(Perk.STEP))
                else -> p
            }
        }
        EmotionalChessTheme { App(vmFor(g, select = "f3")) }
    }

    @Test fun perkChoice() = paparazzi.snapshot {
        val g = restyled(Mode.ROGUELIKE) { p, _ -> p }.copy(pendingPerk = sq("f3"), perkOffer = listOf(Perk.LEAP, Perk.SHIELD, Perk.CHARGE))
        EmotionalChessTheme { Backdrop { Box(Modifier.padding(24.dp)) { PerkCard(g, suggested = Perk.SHIELD, onPick = {}, onHint = {}, thinking = false) } } }
    }

    @Test fun racingKings() = paparazzi.snapshot {
        EmotionalChessTheme { App(vmFor(Rules.newGame(Mode.RACING_KINGS, "Ada", "Grace"))) }
    }

    @Test fun hint() {
        // Set up outside the composable: inside it, snapshot isolation would hide the finished hint.
        var g = Rules.newGame(Mode.KING_OF_THE_HILL, "Ada", "Grace")
        for ((f, t) in listOf("e2" to "e4", "e7" to "e5", "g1" to "f3", "b8" to "c6", "f1" to "c4", "d7" to "d6")) {
            g = Rules.play(g, Rules.legalMovesFrom(g, sq(f)).first { it.to == sq(t) })
        }
        val vm = vmFor(g)
        vm.requestHint()
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.thinking && System.currentTimeMillis() < deadline) Thread.sleep(20)
        paparazzi.snapshot { EmotionalChessTheme { App(vm) } }
    }

    // ------------------------------------------------------------------ two phones

    @Test fun twoPhoneSetup() = paparazzi.snapshot {
        EmotionalChessTheme { Backdrop { NewGameScreen(onStart = { _, _, _, _ -> }, startWithTwoPhones = true) } }
    }

    private fun linkedPair(): Pair<GameViewModel, GameViewModel> {
        val net = com.emotionalchess.net.FakeNetwork()
        val a = net.newLink()
        val b = net.newLink()
        val noSave = object : GameStore {
            override fun load(): SaveData? = null
            override fun save(game: GameState?, undo: List<GameState>, flipped: Boolean, link: LinkSave?) = Unit
        }
        val host = GameViewModel(noSave, linkFactory = { a }, uiContext = Dispatchers.Unconfined)
        val guest = GameViewModel(noSave, linkFactory = { b }, uiContext = Dispatchers.Unconfined)
        host.hostGame(Mode.EMOTIONAL, "Ada", 3, Side.WHITE)
        guest.joinGame("Grace")
        net.flush()
        return host to guest
    }

    @Test fun lobby() {
        val (_, guest) = linkedPair()
        paparazzi.snapshot { EmotionalChessTheme { App(guest) } }
    }

    @Test fun waitingForFriend() {
        val net = com.emotionalchess.net.FakeNetwork()
        val a = net.newLink()
        val b = net.newLink()
        val noSave = object : GameStore {
            override fun load(): SaveData? = null
            override fun save(game: GameState?, undo: List<GameState>, flipped: Boolean, link: LinkSave?) = Unit
        }
        val host = GameViewModel(noSave, linkFactory = { a }, uiContext = Dispatchers.Unconfined)
        val guest = GameViewModel(noSave, linkFactory = { b }, uiContext = Dispatchers.Unconfined)
        host.hostGame(Mode.EMOTIONAL, "Ada", 3, Side.WHITE)
        guest.joinGame("Grace")
        net.flush()
        guest.connectTo(guest.online!!.found.single())
        net.flush()
        host.answerJoinRequest(true)
        net.flush()
        for ((f, t) in listOf("e2" to "e4", "d7" to "d5")) {
            val mover = if (host.myTurn()) host else guest
            mover.onSquareTapped(sq(f)); mover.onSquareTapped(sq(t)); net.flush()
        }
        // Grace's phone while Ada thinks.
        paparazzi.snapshot { EmotionalChessTheme { App(guest) } }
    }
}
