@file:JsModule("peerjs")

package com.emotionalchess.net.peerjs

/**
 * The parts of PeerJS (https://peerjs.com) the game uses: WebRTC data channels between two
 * browsers, introduced to each other by the free public PeerJS server.
 */
external class Peer : JsAny {
    constructor(id: String, options: JsAny)
    constructor(options: JsAny)

    val open: Boolean
    val disconnected: Boolean
    val destroyed: Boolean

    fun on(event: String, callback: (JsAny?) -> Unit)
    fun connect(peer: String, options: JsAny): DataConnection
    fun reconnect()
    fun destroy()
}

external interface DataConnection : JsAny {
    val peer: String
    val open: Boolean

    fun on(event: String, callback: (JsAny?) -> Unit)
    fun send(data: JsAny?)
    fun close()
}
