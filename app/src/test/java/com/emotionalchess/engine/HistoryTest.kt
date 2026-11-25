package com.emotionalchess.engine

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    private fun sq(name: String) = Rules.sq(name[0] - 'a', name[1] - '1')

    /** A game of knights shuffling back and forth, with every state kept for undo. */
    private fun playedGame(plies: Int): List<GameState> {
        val shuffle = listOf("g1" to "f3", "g8" to "f6", "f3" to "g1", "f6" to "g8", "b1" to "c3", "b8" to "c6", "c3" to "b1", "c6" to "b8")
        var g = Rules.newGame(Mode.EMOTIONAL)
        val states = mutableListOf(g)
        for (i in 0 until plies) {
            val (from, to) = shuffle[i % shuffle.size]
            val m = Rules.legalMovesFrom(g, sq(from)).first { it.to == sq(to) }
            g = Rules.play(g, m)
            states += g
            if (g.result != null) break
        }
        return states
    }

    @Test
    fun compactThenExpandRestoresEveryState() {
        val states = playedGame(7)
        val live = states.last()
        val undo = states.dropLast(1)
        assertEquals(undo, History.expand(live, History.compact(live, undo)))
    }

    @Test
    fun compactedSaveIsMuchSmaller() {
        val states = playedGame(7)
        val live = states.last()
        val undo = states.dropLast(1)
        val json = Json
        val full = json.encodeToString(ListSerializer(GameState.serializer()), undo).length
        val compact = json.encodeToString(ListSerializer(Snapshot.serializer()), History.compact(live, undo)).length
        assertTrue("compact=$compact full=$full", compact < full)
    }

    @Test
    fun unrelatedStatesAreStoredWhole() {
        val live = Rules.newGame(Mode.EMOTIONAL)
        val other = playedGame(3).last()
        val snaps = History.compact(live, listOf(other))
        assertEquals(-1, snaps.single().logSize)
        assertEquals(listOf(other), History.expand(live, snaps))
    }
}
