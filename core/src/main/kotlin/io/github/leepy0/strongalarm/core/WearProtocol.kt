package io.github.leepy0.strongalarm.core

/** 폰 ↔ 워치 Data Layer 메시지 경로 */
object WearProtocol {
    const val PREFIX = "/strongalarm"

    /** 폰 → 워치: 울림 시작. payload = 목표 걸음 수 (UTF-8 정수) */
    const val START = "$PREFIX/start"

    /** 폰 → 워치: 휴무 버튼 누르는 중 진동 일시정지 */
    const val PAUSE = "$PREFIX/pause"
    const val RESUME = "$PREFIX/resume"

    /** 폰 → 워치: 알람 종료 */
    const val STOP = "$PREFIX/stop"

    /** 워치 → 폰: 울림 시작 이후 누적 걸음 수 (UTF-8 정수) */
    const val STEPS = "$PREFIX/steps"

    fun encodeInt(v: Int): ByteArray = v.toString().toByteArray(Charsets.UTF_8)
    fun decodeInt(b: ByteArray?): Int? = b?.toString(Charsets.UTF_8)?.trim()?.toIntOrNull()
}
