package io.github.leepy0.strongalarm.wear

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** 워치 앱 설정: 권한 요청 + 상태 표시 */
class WatchMainActivity : ComponentActivity() {
    private lateinit var status: TextView

    private val permissions = arrayOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACTIVITY_RECOGNITION,
    )

    private val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        val grant = Button(this).apply {
            text = "권한 허용"
            setOnClickListener { launcher.launch(permissions) }
        }
        val battery = Button(this).apply {
            text = "배터리 최적화 제외"
            setOnClickListener { requestBatteryExemption() }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 48, 24, 48)
            setBackgroundColor(Color.BLACK)
            addView(status)
            addView(grant)
            addView(battery)
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val ok = permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        status.text = buildString {
            appendLine("강력한 알람 (워치)")
            appendLine()
            appendLine("권한: ${if (ok) "허용됨" else "필요"}")
            appendLine("배터리 최적화 제외: ${if (battery) "예" else "아니오 (선택)"}")
            appendLine()
            append("폰에서 알람이 울리면 진동하고 걸음 수를 폰으로 보냄")
        }
    }

    /**
     * 워치는 요청 다이얼로그를 지원하지 않는 경우가 많음 → 최적화 목록 → 앱 정보 순으로 폴백.
     * 모두 실패하면 직접 경로 안내 (필수 아님: 막혀도 정확한 알람 경유로 서비스 시작)
     */
    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        val pkg = Uri.parse("package:$packageName")
        val candidates = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
        )
        // 지원하지 않는 화면이면 ActivityNotFoundException → 다음 후보
        val opened = candidates.any { intent -> runCatching { startActivity(intent) }.isSuccess }
        if (!opened) {
            Toast.makeText(this, "워치 설정 > 애플리케이션 > 강력한 알람 > 배터리에서 변경", Toast.LENGTH_LONG).show()
        }
    }
}
