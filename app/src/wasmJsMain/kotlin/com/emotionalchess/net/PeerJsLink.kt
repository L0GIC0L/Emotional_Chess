package com.emotionalchess.net

import com.emotionalchess.net.peerjs.DataConnection
import com.emotionalchess.net.peerjs.Peer
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private fun peerOptions(): JsAny = js(
    "({ debug: 1, config: { iceServers: [ { urls: 'stun:stun.l.google.com:19302' }, { urls: 'stun:stun1.l.google.com:19302' } ] } })"
)

private fun connectOptions(name: String): JsAny = js("({ reliable: true, serialization: 'raw', metadata: { name: name } })")

private fun errorType(e: JsAny?): String = js("(e && e.type) ? String(e.type) : 'unknown'")

private fun asText(data: JsAny?): String? = js("(typeof data === 'string') ? data : null")

/**
 * Online play between two browsers. The host gets a short room code; the friend joins with it.
 * The browsers then talk directly over WebRTC; the free PeerJS server only introduces them.
 * Messages use the same protocol (and framing) as two phones over Nearby.
 */
class PeerJsLink : PeerLink {
    private var peer: Peer? = null
    private var listener: PeerListener? = null
    private var myName = ""
    /** Host: our room. */
    private var roomCode: String? = null
    private var connection: DataConnection? = null
    /** Guest: a room to dial as soon as our own peer is ready. */
    private var pendingRoom: String? = null
    /** Guest: the room we were in, to find it again after a dropped connection. */
    private var lastRoom: String? = null
    private var retries = 0
    /** Host after a reload: the room to reopen (it may take the server a moment to free the name). */
    private var fixedRoom: String? = null
    private var reopenTries = 0
    private val joiner = Framer.Joiner()
    private var nextId = 0L
    private val scope = MainScope()

    // ------------------------------------------------------------------ host

    override fun host(name: String, listener: PeerListener, room: String?) {
        this.listener = listener
        myName = name
        val existing = peer
        val code = roomCode
        if (existing != null && !existing.destroyed && code != null) {
            // Back after a dropped connection: the same room, so the friend can come back.
            if (existing.disconnected) existing.reconnect()
            listener.onReady(code)
            return
        }
        if (room != null) fixedRoom = room.uppercase()
        openRoom(fixedRoom ?: newCode())
    }

    private fun openRoom(code: String) {
        val p = Peer(PREFIX + code, peerOptions())
        peer = p
        p.on("open") {
            roomCode = code
            println("Emotional Chess room: $code")
            listener?.onReady(code)
        }
        p.on("connection") { c -> c?.unsafeCast<DataConnection>()?.let { incoming(it) } }
        // Losing the PeerJS server does not end a game already connected; quietly get back on it.
        p.on("disconnected") { if (!p.destroyed) p.reconnect() }
        p.on("error") { e -> onPeerError(p, errorType(e)) }
    }

    private fun incoming(c: DataConnection) {
        // Only someone with the code can get here. A new connection replaces an old one that died.
        c.on("open") {
            connection?.let { old -> connection = null; old.close() }
            attach(c, room = null)
        }
    }

    // ------------------------------------------------------------------ guest

    override fun join(name: String, listener: PeerListener, rejoin: String?) {
        this.listener = listener
        myName = name
        if (rejoin != null) lastRoom = rejoin.uppercase()
        val existing = peer
        if (existing != null && !existing.destroyed) {
            if (existing.disconnected) existing.reconnect()
            // After a dropped connection: offer the same room again in a moment, and the game reconnects.
            lastRoom?.let { room -> scope.launch { delay(1500); listener.onFound(Endpoint(room, "Room $room")) } }
            return
        }
        val p = Peer(peerOptions())
        peer = p
        p.on("open") {
            pendingRoom?.let { pendingRoom = null; dial(it) }
            // After a reload: offer the room this page was in, and the game reconnects to it.
            if (rejoin != null) lastRoom?.let { room -> listener.onFound(Endpoint(room, "Room $room")) }
        }
        p.on("disconnected") { if (!p.destroyed) p.reconnect() }
        p.on("error") { e -> onPeerError(p, errorType(e)) }
    }

    override fun connect(endpointId: String) {
        val room = endpointId.trim().uppercase()
        val p = peer ?: return
        if (p.open) dial(room) else pendingRoom = room
    }

    private fun dial(room: String) {
        val p = peer ?: return
        val c = p.connect(PREFIX + room, connectOptions(myName))
        var settled = false
        c.on("open") {
            settled = true
            lastRoom = room
            retries = 0
            attach(c, room)
        }
        scope.launch {
            delay(20_000)
            if (!settled && connection == null) {
                settled = true
                c.close()
                if (lastRoom != null) retryLater()
                else listener?.onError("Couldn't reach room $room. Check the code, and that your friend's page is still open.")
            }
        }
    }

    private fun retryLater() {
        val room = lastRoom ?: return
        if (++retries > 40) {
            listener?.onError("Couldn't find room $room again. Your friend may have closed the page.")
            return
        }
        scope.launch {
            delay(3000)
            if (connection == null) dial(room)
        }
    }

    // ------------------------------------------------------------------ both

    private fun attach(c: DataConnection, room: String?) {
        connection = c
        var closed = false
        val gone: (JsAny?) -> Unit = {
            if (!closed && connection === c) {
                closed = true
                connection = null
                listener?.onDisconnected()
            }
        }
        c.on("data") { d -> asText(d)?.let { joiner.receive(it) }?.let { listener?.onMessage(it) } }
        c.on("close", gone)
        c.on("iceStateChanged") { s -> if (asText(s) == "failed" || asText(s) == "closed") gone(null) }
        listener?.onConnected(Endpoint(c.peer, if (room != null) "Room $room" else "Friend"))
    }

    private fun onPeerError(p: Peer, type: String) {
        val connected = connection != null
        when (type) {
            "unavailable-id" -> if (roomCode == null) {
                p.destroy()
                val keep = fixedRoom
                when {
                    // Our own room from before the reload: the server frees it shortly.
                    keep != null && ++reopenTries <= 10 -> scope.launch { delay(3000); openRoom(keep) }
                    keep != null -> listener?.onError("Couldn't reopen room $keep. Start a new game.")
                    // Someone else already uses this code: pick another.
                    else -> openRoom(newCode())
                }
            }
            "peer-unavailable" ->
                if (lastRoom != null && !connected) retryLater()
                else if (!connected) listener?.onError("No game with that code is open. Check the code, and that your friend's page is still open.")
            "disconnected" -> Unit
            "network", "server-error", "socket-error", "socket-closed" ->
                if (!connected) listener?.onError("Can't reach the game server. Check the internet connection and try again.")
            "browser-incompatible" -> listener?.onError("This browser can't play online. Try Chrome, Edge, Firefox or Safari.")
            "webrtc" -> if (!connected) listener?.onError("Couldn't connect to your friend's browser. Some networks block this; try another network.")
            else -> if (!connected) listener?.onError("Online play failed ($type). Try again.")
        }
    }

    override fun accept(endpointId: String) = Unit // anyone with the room code may join

    override fun reject(endpointId: String) = Unit

    override fun send(text: String) {
        val c = connection ?: return
        for (frame in Framer.split(text, nextId++)) c.send(frame.toJsString())
    }

    override fun stop() {
        listener = null
        connection?.close()
        connection = null
        peer?.destroy()
        peer = null
        scope.cancel()
    }

    private fun newCode(): String = (1..5).map { CODE_LETTERS[Random.nextInt(CODE_LETTERS.length)] }.joinToString("")

    private companion object {
        const val PREFIX = "emotionalchess-"
        /** No 0/O or 1/I, so codes read aloud or typed don't get confused. */
        const val CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
