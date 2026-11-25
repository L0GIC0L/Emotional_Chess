package com.emotionalchess.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

/**
 * Two phones in the same room, over Google Nearby Connections: Bluetooth, Wi-Fi Direct or a shared
 * Wi-Fi network, whichever works. No account and no server. Needs Google Play services.
 */
class NearbyPeerLink(context: Context) : PeerLink {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private var listener: PeerListener? = null
    private var myName = ""
    private var connected: String? = null
    private var names = HashMap<String, String>()
    private var nextId = 0L
    private val joiner = Framer.Joiner()

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            joiner.receive(String(bytes, Charsets.UTF_8))?.let { listener?.onMessage(it) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            names[endpointId] = info.endpointName
            listener?.onRequest(Endpoint(endpointId, info.endpointName), info.authenticationDigits)
            // We asked to join, so accept our own side straight away; the host decides.
            if (!info.isIncomingConnection) client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connected = endpointId
                    client.stopAdvertising()
                    client.stopDiscovery()
                    listener?.onConnected(Endpoint(endpointId, names[endpointId] ?: ""))
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> listener?.onError("The other phone declined.")
                else -> listener?.onError("Could not connect (${resolution.status.statusCode}). Try again.")
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (endpointId == connected) connected = null
            listener?.onDisconnected()
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            listener?.onFound(Endpoint(endpointId, info.endpointName))
        }

        override fun onEndpointLost(endpointId: String) {
            listener?.onLost(endpointId)
        }
    }

    override fun host(name: String, listener: PeerListener) {
        this.listener = listener
        myName = name
        client.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { listener.onError(describe(it, "Could not make this phone visible")) }
    }

    override fun join(name: String, listener: PeerListener) {
        this.listener = listener
        myName = name
        client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { listener.onError(describe(it, "Could not look for nearby phones")) }
    }

    override fun connect(endpointId: String) {
        client.requestConnection(myName, endpointId, lifecycle)
            .addOnFailureListener { listener?.onError(describe(it, "Could not reach that phone")) }
    }

    override fun accept(endpointId: String) {
        client.acceptConnection(endpointId, payloads)
    }

    override fun reject(endpointId: String) {
        client.rejectConnection(endpointId)
    }

    override fun send(text: String) {
        val to = connected ?: return
        for (frame in Framer.split(text, nextId++)) client.sendPayload(to, Payload.fromBytes(frame.toByteArray(Charsets.UTF_8)))
    }

    override fun stop() {
        listener = null
        connected = null
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
    }

    private fun describe(e: Exception, what: String): String {
        val code = (e as? ApiException)?.statusCode
        return when (code) {
            ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING, ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING -> "$what: already searching."
            in ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES..ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT ->
                "$what: Emotional Chess needs the Location and Nearby devices permissions. " +
                    "Allow them in Settings → Apps → Emotional Chess → Permissions, turn Location on, and try again. ($code)"
            else -> "$what. Check that Bluetooth, Wi-Fi and Location are on." + (code?.let { " ($it)" } ?: "")
        }
    }

    companion object {
        private const val SERVICE_ID = "com.emotionalchess.table"
        /** One-to-one connection with the best bandwidth. */
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT

        /**
         * Runtime permissions Nearby needs on this Android version. Location is needed on every
         * version: without it discovery fails with MISSING_PERMISSION_ACCESS_COARSE_LOCATION
         * (seen on Android 17). Android 12+ only grants precise location together with approximate.
         */
        fun permissions(): Array<String> = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.toTypedArray()

        fun hasPermissions(context: Context): Boolean = permissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
