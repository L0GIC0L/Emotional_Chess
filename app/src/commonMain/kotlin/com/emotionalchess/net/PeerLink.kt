package com.emotionalchess.net

/** A nearby phone that is hosting a game. */
data class Endpoint(val id: String, val name: String)

/** What a [PeerLink] reports. Called on the main thread. */
interface PeerListener {
    /** Online play: hosting has started under this room code (the friend joins with it). */
    fun onReady(code: String) {}
    fun onFound(endpoint: Endpoint) {}
    fun onLost(endpointId: String) {}
    /** Someone wants to join; both phones show the same [code] so the players can check it. */
    fun onRequest(endpoint: Endpoint, code: String) {}
    fun onConnected(endpoint: Endpoint) {}
    fun onDisconnected() {}
    fun onMessage(text: String) {}
    fun onError(message: String) {}
}

/** Finds, connects to and talks with one other phone. */
interface PeerLink {
    /** Makes this phone visible as [name] and waits for someone to join; online, reopens [room] if given. */
    fun host(name: String, listener: PeerListener, room: String? = null)
    /**
     * Looks for phones that are hosting (online: gets ready to join a room code via [connect]).
     * Online, [rejoin] is a room this page was in before it reloaded: it is offered via onFound.
     */
    fun join(name: String, listener: PeerListener, rejoin: String? = null)
    /** Joins a host found nearby, or (online) the room with this code. */
    fun connect(endpointId: String)
    fun accept(endpointId: String)
    fun reject(endpointId: String)
    fun send(text: String)
    fun stop()
}
