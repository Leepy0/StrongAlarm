package io.github.leepy0.strongalarm.lights

import android.content.Context
import io.github.leepy0.strongalarm.data.DeviceSnapshot
import io.github.leepy0.strongalarm.data.HistoryLog
import io.github.leepy0.strongalarm.data.Phase
import io.github.leepy0.strongalarm.data.SessionState
import io.github.leepy0.strongalarm.data.Stores
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 조명 제어는 모두 best-effort: 실패해도 알람 동작에 영향 없음 */
object LightController {

    fun isConfigured(ctx: Context): Boolean {
        val l = Stores.settings.get(ctx).lights
        return (l.dimmerIds.isNotEmpty() || l.switchIds.isNotEmpty()) && SmartThingsAuth.isLoggedIn(ctx)
    }

    suspend fun snapshot(ctx: Context, ids: Collection<String>): Map<String, DeviceSnapshot> = coroutineScope {
        ids.distinct()
            .map { id -> async { id to (SmartThingsClient.status(ctx, id) ?: DeviceSnapshot()) } }
            .awaitAll()
            .toMap()
    }

    suspend fun setLevels(ctx: Context, ids: List<String>, level: Int, turnOn: Boolean) = coroutineScope {
        ids.map { async { SmartThingsClient.setLevel(ctx, it, level, turnOn) } }.awaitAll()
    }

    suspend fun switchesOn(ctx: Context, ids: List<String>) = coroutineScope {
        ids.map { async { SmartThingsClient.on(ctx, it) } }.awaitAll()
    }

    /** 알람 전 상태로 복구: 앱이 켠 조명은 끄고, 원래 켜져 있던 조명은 밝기만 되돌림 */
    suspend fun restore(ctx: Context, s: SessionState, label: String = "조명 복구") = coroutineScope {
        val l = Stores.settings.get(ctx).lights
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<Boolean>>()
        if (s.dimmersTouched) {
            l.dimmerIds.forEach { id ->
                val snap = s.snapshot[id]
                jobs += async {
                    if (snap?.on == true) {
                        // 원래 켜져 있던 조명은 밝기만 되돌림
                        snap.level?.let { SmartThingsClient.setLevel(ctx, id, it, turnOn = true) } ?: true
                    } else {
                        SmartThingsClient.off(ctx, id)
                    }
                }
            }
        }
        if (s.switchesTouched) {
            l.switchIds.filter { it !in l.dimmerIds && s.snapshot[it]?.on != true }
                .forEach { id -> jobs += async { SmartThingsClient.off(ctx, id) } }
        }
        if (jobs.isEmpty()) return@coroutineScope
        val ok = jobs.awaitAll().count { it }
        HistoryLog.add(ctx, "$label ($ok/${jobs.size})")
    }

    /** 해제 N분 뒤: 앱이 켠 조명 끄기 (LIGHTS_OFF 알람에서 호출) */
    suspend fun autoOff(ctx: Context) {
        val s = Stores.state.get(ctx).session ?: return
        if (s.phase != Phase.DONE) return
        restore(ctx, s, "자동 소등")
        Stores.state.update(ctx) { st -> if (st.session?.date == s.date) st.copy(session = null) else st }
    }
}
