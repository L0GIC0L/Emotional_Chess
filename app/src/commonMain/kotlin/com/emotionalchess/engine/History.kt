package com.emotionalchess.engine

import kotlinx.serialization.Serializable

/**
 * An undo snapshot as written to disk.
 *
 * Undo snapshots are ancestors of the live game, and a game's log and position history only ever
 * grow, so each snapshot's lists are a prefix of the live game's. Storing their lengths instead of
 * the lists keeps the save file linear in game length instead of quadratic.
 * A size of -1 means the lists were stored in [state] as they are.
 */
@Serializable
data class Snapshot(val state: GameState, val logSize: Int = -1, val positionsSize: Int = -1)

object History {
    fun compact(live: GameState, undo: List<GameState>): List<Snapshot> = undo.map { s ->
        val sharesLog = s.log.size <= live.log.size && live.log.subList(0, s.log.size) == s.log
        val sharesPositions = s.positions.size <= live.positions.size &&
            live.positions.subList(0, s.positions.size) == s.positions
        if (sharesLog && sharesPositions) {
            Snapshot(s.copy(log = emptyList(), positions = emptyList()), s.log.size, s.positions.size)
        } else {
            Snapshot(s)
        }
    }

    fun expand(live: GameState, snapshots: List<Snapshot>): List<GameState> = snapshots.map { snap ->
        if (snap.logSize < 0 || snap.logSize > live.log.size || snap.positionsSize > live.positions.size) {
            snap.state
        } else {
            snap.state.copy(
                log = live.log.subList(0, snap.logSize).toList(),
                positions = live.positions.subList(0, snap.positionsSize).toList(),
            )
        }
    }
}
