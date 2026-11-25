package com.emotionalchess.net

import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Move
import com.emotionalchess.engine.Perk
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.zip.CRC32

/**
 * Two phones play one game by sending each other the actions they take. Both phones run the same
 * rules on the same state, so applying the same actions keeps them identical; every action carries
 * a fingerprint of the resulting state, and a mismatch makes the host resend its whole state.
 */
@Serializable
sealed interface NetAction {
    @Serializable @SerialName("play") data class Play(val move: Move) : NetAction
    @Serializable @SerialName("heal") data class Heal(val square: Int) : NetAction
    @Serializable @SerialName("pick") data class Pick(val perk: Perk) : NetAction
    @Serializable @SerialName("roll") data class Roll(val value: Int, val animated: Boolean) : NetAction
    @Serializable @SerialName("resign") data class Resign(val side: Side) : NetAction
    @Serializable @SerialName("draw") data object Draw : NetAction
    @Serializable @SerialName("hint") data class HintUsed(val side: Side) : NetAction
}

@Serializable
sealed interface NetMessage {
    /** First message each way after connecting. */
    @Serializable @SerialName("hello") data class Hello(val version: Int, val name: String) : NetMessage
    /** The host's whole game, with the side the receiver plays. [seq] counts actions since the last state. */
    @Serializable @SerialName("state") data class State(val game: GameState, val yourSide: Side, val seq: Int) : NetMessage
    /** One action; [seq] is the number of actions the sender had applied before it. */
    @Serializable @SerialName("act") data class Act(val seq: Int, val action: NetAction, val check: Long) : NetMessage
    @Serializable @SerialName("need-state") data object NeedState : NetMessage
    @Serializable @SerialName("draw-offer") data object DrawOffer : NetMessage
    @Serializable @SerialName("draw-declined") data object DrawDeclined : NetMessage
}

object Protocol {
    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }

    fun encode(m: NetMessage): String = json.encodeToString(NetMessage.serializer(), m)

    fun decode(text: String): NetMessage? = runCatching { json.decodeFromString(NetMessage.serializer(), text) }.getOrNull()

    /** A checksum of the whole state; equal on both phones when they agree. */
    fun fingerprint(s: GameState): Long =
        CRC32().apply { update(json.encodeToString(GameState.serializer(), s).toByteArray(Charsets.UTF_8)) }.value

    /** Applies an action exactly as the phone that took it did. */
    fun apply(s: GameState, a: NetAction): GameState = when (a) {
        is NetAction.Play -> Rules.play(s, a.move)
        is NetAction.Heal -> Rules.absolve(s, a.square)
        is NetAction.Pick -> Rules.choosePerk(s, a.perk)
        is NetAction.Roll -> Rules.applyRoll(s, a.value)
        is NetAction.Resign -> Rules.resign(s, a.side)
        NetAction.Draw -> Rules.agreeDraw(s)
        is NetAction.HintUsed ->
            if (a.side == Side.WHITE) s.copy(whiteHints = s.whiteHints + 1) else s.copy(blackHints = s.blackHints + 1)
    }
}

/**
 * Splits long messages into frames small enough for one Nearby payload (32 KB) and joins them
 * again on the other side. Frames are "W<text>" for a whole message or "P<id>:<i>:<n>:<chunk>".
 */
object Framer {
    /** Characters per frame; at most 4 UTF-8 bytes each, so a frame stays under 32 KB. */
    const val CHUNK = 7_000

    fun split(text: String, id: Long): List<String> {
        if (text.length <= CHUNK) return listOf("W$text")
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + CHUNK, text.length)
            // Never cut an emoji (a surrogate pair) in half.
            if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
            chunks += text.substring(start, end)
            start = end
        }
        return chunks.mapIndexed { i, c -> "P$id:$i:${chunks.size}:$c" }
    }

    class Joiner {
        private val partial = HashMap<String, Array<String?>>()

        /** Returns a complete message, or null while parts are still missing. */
        fun receive(frame: String): String? {
            if (frame.startsWith("W")) return frame.substring(1)
            if (!frame.startsWith("P")) return null
            val parts = frame.substring(1).split(":", limit = 4)
            if (parts.size < 4) return null
            val (id, i, n) = Triple(parts[0], parts[1].toIntOrNull() ?: return null, parts[2].toIntOrNull() ?: return null)
            val slots = partial.getOrPut(id) { arrayOfNulls(n) }
            if (i !in slots.indices) return null
            slots[i] = parts[3]
            if (slots.any { it == null }) return null
            partial.remove(id)
            return slots.joinToString("") { it!! }
        }
    }
}
