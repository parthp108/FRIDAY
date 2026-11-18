package com.health.friday.sovereign

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap

enum class MeshNodeType(val serviceType: String) {
    HOME_ASSISTANT("_home-assistant._tcp."),
    MATTER_FABRIC("_matter._tcp."),
    WYOMING_SPEAKER("_wyoming._tcp."),
    LOCAL_LLM_SERVER("_ollama._tcp."),
    DESKTOP_HELPER("_friday-desktop._tcp.")
}

data class DiscoveredMeshNode(
    val nodeType: MeshNodeType,
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val discoveredAtMs: Long = System.currentTimeMillis()
)

class LanMeshDiscoveryManager(context: Context) {

    companion object {
        private const val TAG = "FRIDAY_MDNS_MESH"
    }

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val activeListeners = ConcurrentHashMap<MeshNodeType, NsdManager.DiscoveryListener>()
    private val nodeMap = ConcurrentHashMap<String, DiscoveredMeshNode>()

    private val _discoveredNodes = MutableStateFlow<List<DiscoveredMeshNode>>(emptyList())
    val discoveredNodes: StateFlow<List<DiscoveredMeshNode>> = _discoveredNodes.asStateFlow()

    /**
     * Acquires a hardware Wi-Fi MulticastLock and starts mDNS discovery across the local subnet.
     */
    fun startMeshDiscovery() {
        acquireMulticastLock()

        for (type in MeshNodeType.entries) {
            if (activeListeners.containsKey(type)) continue

            val listener = createDiscoveryListener(type)
            runCatching {
                nsdManager.discoverServices(type.serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                activeListeners[type] = listener
            }.onFailure { e ->
                Log.e(TAG, "Failed to start mDNS discovery for ${type.serviceType}", e)
            }
        }
    }

    fun stopMeshDiscovery() {
        for ((_, listener) in activeListeners) {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        activeListeners.clear()
        releaseMulticastLock()
    }

    /**
     * Allows manual registration of a known static IP on the LAN (e.g., when running over
     * a mobile hotspot or static desktop IP 192.168.x.x).
     */
    fun registerStaticLanNode(
        nodeType: MeshNodeType,
        serviceName: String,
        ipAddress: String,
        port: Int
    ) {
        val key = "${nodeType.name}:$ipAddress:$port"
        nodeMap[key] = DiscoveredMeshNode(nodeType, serviceName, ipAddress, port)
        _discoveredNodes.value = nodeMap.values.toList()
    }

    private fun createDiscoveryListener(type: MeshNodeType) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "mDNS discovery started for $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: code=$errorCode")
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = resolved.host
                    val ip = if (host is Inet4Address) {
                        host.hostAddress
                    } else {
                        host?.hostAddress
                    } ?: return

                    val node = DiscoveredMeshNode(
                        nodeType = type,
                        serviceName = resolved.serviceName,
                        hostAddress = ip,
                        port = resolved.port
                    )
                    val key = "${type.name}:$ip:${resolved.port}"
                    nodeMap[key] = node
                    _discoveredNodes.value = nodeMap.values.toList()
                    Log.i(TAG, "Resolved LAN Mesh Node: $node")
                }
            })
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            nodeMap.entries.removeIf { it.value.serviceName == service.serviceName }
            _discoveredNodes.value = nodeMap.values.toList()
        }

        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("friday_mdns_lock").apply {
                setReferenceCounted(false)
            }
        }
        if (multicastLock?.isHeld == false) {
            multicastLock?.acquire()
        }
    }

    private fun releaseMulticastLock() {
        if (multicastLock?.isHeld == true) {
            multicastLock?.release()
        }
    }
}
