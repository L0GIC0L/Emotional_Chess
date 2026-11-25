package com.emotionalchess.net

/** A nearby phone that is hosting a game. */
data class Endpoint(val id: String, val name: String)

/** What a [PeerLink] reports. Called on the main thread. */
interface PeerListener {
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
    /** Makes this phone visible as [name] and waits for someone to join. */
    fun host(name: String, listener: PeerListener)
    /** Looks for phones that are hosting. */
    fun join(name: String, listener: PeerListener)
    fun connect(endpointId: String)
    fun accept(endpointId: String)
    fun reject(endpointId: String)
    fun send(text: String)
    fun stop()
}
