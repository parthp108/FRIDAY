package com.health.friday.sovereign

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MeshAddressPolicy {
    private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun isPrivateLanIpv4(host: String): Boolean {
        val m = ipv4.matchEntire(host) ?: return false
        val o = m.groupValues.drop(1).map { it.toIntOrNull() ?: return false }
        if (o.any { it !in 0..255 }) return false
        return o[0] == 10 ||
                o[0] == 127 ||
                (o[0] == 172 && o[1] in 16..31) ||
                (o[0] == 192 && o[1] == 168)
    }
}

data class TrustedMeshNode(
    val nodeType: MeshNodeType,
    val hostAddress: String,
    val port: Int,
    val label: String,
    val token: String? = null
) {
    override fun toString() = "TrustedMeshNode($nodeType, $hostAddress:$port, $label)"
}

interface TrustedNodeSource {
    fun trustedNodes(type: MeshNodeType): List<TrustedMeshNode>
}

class TrustedMeshNodeStore(context: Context) : TrustedNodeSource {

    private val prefs = context.applicationContext
        .getSharedPreferences("friday_trusted_mesh", Context.MODE_PRIVATE)

    @Synchronized
    override fun trustedNodes(type: MeshNodeType): List<TrustedMeshNode> =
        loadAll().filter { it.nodeType == type }

    @Synchronized
    fun all(): List<TrustedMeshNode> = loadAll()

    @Synchronized
    fun trust(node: TrustedMeshNode): Boolean {
        if (!MeshAddressPolicy.isPrivateLanIpv4(node.hostAddress)) return false
        if (node.port !in 1..65535) return false
        val others = loadAll().filterNot {
            it.nodeType == node.nodeType && it.hostAddress == node.hostAddress && it.port == node.port
        }
        save(others + node)
        return true
    }

    @Synchronized
    fun remove(type: MeshNodeType, host: String, port: Int) {
        save(loadAll().filterNot {
            it.nodeType == type && it.hostAddress == host && it.port == port
        })
    }

    private fun loadAll(): List<TrustedMeshNode> {
        val raw = prefs.getString("nodes", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val type = runCatching { MeshNodeType.valueOf(o.getString("type")) }.getOrNull()
                    ?: return@mapNotNull null
                TrustedMeshNode(
                    nodeType = type,
                    hostAddress = o.getString("host"),
                    port = o.getInt("port"),
                    label = o.optString("label", ""),
                    token = if (o.has("token") && !o.isNull("token")) o.getString("token") else null
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<TrustedMeshNode>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("type", n.nodeType.name)
                put("host", n.hostAddress)
                put("port", n.port)
                put("label", n.label)
                if (n.token != null) put("token", n.token)
            })
        }
        prefs.edit().putString("nodes", arr.toString()).apply()
    }
}
