package io.github.leepy0.strongalarm.alarm

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** 폰 → 워치 메시지. 워치 미연결·Play 서비스 오류는 무시 */
object WatchLink {
    suspend fun nodes(ctx: Context): List<Node> =
        runCatching { Wearable.getNodeClient(ctx).connectedNodes.await() }
            .onFailure { Log.w("WatchLink", "노드 조회 실패", it) }
            .getOrDefault(emptyList())

    /** @return 전송 대상 노드 수 */
    suspend fun send(ctx: Context, path: String, payload: ByteArray = ByteArray(0)): Int {
        val ns = nodes(ctx)
        val client = Wearable.getMessageClient(ctx)
        ns.forEach { n ->
            runCatching { client.sendMessage(n.id, path, payload).await() }
                .onFailure { Log.w("WatchLink", "전송 실패 $path → ${n.displayName}", it) }
        }
        return ns.size
    }
}
