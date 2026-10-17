package io.github.leepy0.strongalarm.core

/**
 * 기상 확인 후 다시 잠들었는지 감시.
 * 알람을 끈 시각부터 [windowMs] 동안, 움직임(걸음)도 폰 사용도 [idleMs] 이상 없으면 다시 울릴 때가 됨.
 * 끈 시각도 '활동'으로 친다.
 */
class WakeWatch(private val startAt: Long, private val idleMs: Long, private val windowMs: Long = WINDOW_MS) {
    enum class Verdict { WAIT, RING, EXPIRED }

    var lastActiveAt = startAt
        private set

    /** 움직임 또는 폰 사용 */
    fun onActive(now: Long) {
        if (now > lastActiveAt) lastActiveAt = now
    }

    fun check(now: Long): Verdict = when {
        now - startAt >= windowMs -> Verdict.EXPIRED
        now - lastActiveAt >= idleMs -> Verdict.RING
        else -> Verdict.WAIT
    }

    companion object {
        /** 끈 뒤 이 시간 동안만 감시 (그 뒤엔 일상 활동으로 보고 종료) */
        const val WINDOW_MS = 60 * 60_000L
    }
}
