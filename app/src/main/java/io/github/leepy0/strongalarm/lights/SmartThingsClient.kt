package io.github.leepy0.strongalarm.lights

import android.content.Context
import android.util.Log
import io.github.leepy0.strongalarm.data.DeviceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object SmartThingsClient {
    private const val TAG = "SmartThingsClient"
    private const val BASE = "https://api.smartthings.com/v1"

    data class Device(val id: String, val label: String, val hasSwitch: Boolean, val hasLevel: Boolean)

    /** switch 기능이 있는 기기 목록 (main 컴포넌트 기준) */
    suspend fun listDevices(ctx: Context): List<Device>? = withContext(Dispatchers.IO) {
        val token = SmartThingsAuth.accessToken(ctx) ?: return@withContext null
        val out = mutableListOf<Device>()
        var url: String? = "$BASE/devices?capability=switch"
        while (url != null) {
            val res = Http.request("GET", url, auth(token))
            val root = if (res.ok) runCatching { JSONObject(res.body) }.getOrNull() else null
            if (root == null) {
                Log.e(TAG, "기기 목록 실패 ${res.code}: ${res.body}")
                return@withContext null
            }
            val items = root.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val d = items.getJSONObject(i)
                val caps = mainCapabilities(d)
                out += Device(
                    id = d.getString("deviceId"),
                    label = d.optString("label").ifEmpty { d.optString("name") },
                    hasSwitch = "switch" in caps,
                    hasLevel = "switchLevel" in caps,
                )
            }
            url = root.optJSONObject("_links")?.optJSONObject("next")?.optString("href")?.ifEmpty { null }
        }
        out.sortedBy { it.label }
    }

    /** 현재 on/off, 밝기 */
    suspend fun status(ctx: Context, deviceId: String): DeviceSnapshot? = withContext(Dispatchers.IO) {
        val token = SmartThingsAuth.accessToken(ctx) ?: return@withContext null
        val res = Http.request("GET", "$BASE/devices/$deviceId/components/main/status", auth(token))
        if (!res.ok) return@withContext null
        runCatching {
            val o = JSONObject(res.body)
            val sw = o.optJSONObject("switch")?.optJSONObject("switch")?.optString("value")
            val level = o.optJSONObject("switchLevel")?.optJSONObject("level")
                ?.takeIf { it.has("value") && !it.isNull("value") }?.optInt("value")
            DeviceSnapshot(on = sw?.let { it == "on" }, level = level)
        }.getOrNull()
    }

    suspend fun on(ctx: Context, id: String) = command(ctx, id, cmd("switch", "on"))

    suspend fun off(ctx: Context, id: String) = command(ctx, id, cmd("switch", "off"))

    /** 밝기 설정. turnOn이면 setLevel 뒤 on도 함께 전송 (setLevel만으로 켜지지 않는 드라이버 대비) */
    suspend fun setLevel(ctx: Context, id: String, level: Int, turnOn: Boolean): Boolean {
        val cmds = mutableListOf(cmd("switchLevel", "setLevel", level.coerceIn(1, 100)))
        if (turnOn) cmds += cmd("switch", "on")
        return command(ctx, id, *cmds.toTypedArray())
    }

    private suspend fun command(ctx: Context, id: String, vararg commands: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            val token = SmartThingsAuth.accessToken(ctx) ?: return@withContext false
            val body = JSONObject().put("commands", JSONArray().apply { commands.forEach { put(it) } })
            val res = Http.request("POST", "$BASE/devices/$id/commands", auth(token), body.toString())
            if (!res.ok) Log.e(TAG, "명령 실패 $id ${res.code}: ${res.body}")
            res.ok
        }

    private fun cmd(capability: String, command: String, vararg args: Any): JSONObject =
        JSONObject()
            .put("component", "main")
            .put("capability", capability)
            .put("command", command)
            .put("arguments", JSONArray().apply { args.forEach { put(it) } })

    private fun auth(token: String) = mapOf("Authorization" to "Bearer $token")

    private fun mainCapabilities(d: JSONObject): Set<String> {
        val comps = d.optJSONArray("components") ?: return emptySet()
        for (i in 0 until comps.length()) {
            val c = comps.getJSONObject(i)
            if (c.optString("id") != "main") continue
            val caps = c.optJSONArray("capabilities") ?: return emptySet()
            return (0 until caps.length()).mapNotNull { caps.optJSONObject(it)?.optString("id") }.toSet()
        }
        return emptySet()
    }
}
