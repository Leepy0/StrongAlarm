package io.github.leepy0.strongalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.provider.Settings
import android.util.Log
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 알람 소리. USAGE_ALARM + 내장 스피커 우선 출력 (블루투스·이어폰 연결 상태에서도 스피커로).
 * 기본 알람음 재생 실패(잠금 해제 전 부팅 등) 시 합성 비프음으로 대체
 */
/**
 * @param restoreVolume 종료 시 되돌릴 알람 볼륨. null이면 지금 볼륨 (프로세스 재시작 후 복구 시 세션에 저장한 값 사용)
 */
class AlarmSound(private val ctx: Context, restoreVolume: Int? = null) {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var beep: AudioTrack? = null
    /** 종료 시 복구할 원래 알람 볼륨 */
    val originalVolume: Int = restoreVolume ?: am.getStreamVolume(AudioManager.STREAM_ALARM)
    private var restored = false

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val speaker: AudioDeviceInfo?
        get() = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    fun start() {
        if (!startPlayer()) startBeep()
    }

    fun pause() {
        runCatching { player?.pause() }
        runCatching { beep?.pause() }
    }

    fun resume() {
        runCatching { player?.start() }
        runCatching { beep?.play() }
    }

    /** 알람 스트림 볼륨 강제 설정 (0.0~1.0) */
    fun setVolume(fraction: Float) {
        runCatching {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val v = (max * fraction).roundToInt().coerceIn(1, max)
            am.setStreamVolume(AudioManager.STREAM_ALARM, v, 0)
        }.onFailure { Log.w(TAG, "볼륨 설정 실패", it) }
    }

    fun stop() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        runCatching { beep?.stop() }
        beep?.release()
        beep = null
        if (!restored) {
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0) }
            restored = true
        }
    }

    private fun startPlayer(): Boolean = try {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        val mp = MediaPlayer()
        player = mp
        mp.apply {
            setAudioAttributes(attrs)
            setDataSource(ctx, uri)
            isLooping = true
            speaker?.let { setPreferredDevice(it) }
            setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer 오류 $what/$extra → 비프음 대체")
                releasePlayer()
                startBeep()
                true
            }
            prepare()
            start()
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "기본 알람음 재생 실패 → 비프음", e)
        releasePlayer()
        false
    }

    private fun releasePlayer() {
        runCatching { player?.release() }
        player = null
    }

    private fun startBeep() {
        if (beep != null) return
        try {
            val rate = 44_100
            // 삐삐삐삐 — 휴지 패턴 (약 1.8초 반복)
            val pattern = buildList {
                repeat(4) { add(true to 0.15); add(false to 0.10) }
                add(false to 0.8)
            }
            val total = pattern.sumOf { (it.second * rate).toInt() }
            val pcm = ShortArray(total)
            var idx = 0
            for ((on, sec) in pattern) {
                val n = (sec * rate).toInt()
                if (on) {
                    for (i in 0 until n) {
                        pcm[idx + i] = (sin(2 * PI * 880 * i / rate) * 0.8 * Short.MAX_VALUE).toInt().toShort()
                    }
                }
                idx += n
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(total * 2)
                .build()
            track.write(pcm, 0, total)
            track.setLoopPoints(0, total, -1)
            speaker?.let { track.setPreferredDevice(it) }
            track.play()
            beep = track
        } catch (e: Exception) {
            Log.e(TAG, "비프음 재생 실패", e)
        }
    }

    private companion object {
        const val TAG = "AlarmSound"
    }
}
