package io.github.leepy0.strongalarm.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.leepy0.strongalarm.R
import io.github.leepy0.strongalarm.ui.components.Group
import io.github.leepy0.strongalarm.ui.components.GroupDivider
import io.github.leepy0.strongalarm.ui.components.RowItem
import io.github.leepy0.strongalarm.ui.components.SectionLabel
import io.github.leepy0.strongalarm.ui.components.Subpage
import io.github.leepy0.strongalarm.ui.theme.Palette

enum class PermissionKey { NOTIFICATIONS, CALENDAR, ACTIVITY, FULL_SCREEN, BATTERY, EXACT_ALARM, OVERLAY }

data class PermissionItem(
    val key: PermissionKey,
    val title: String,
    val why: String,
    val granted: Boolean,
    val optional: Boolean = false,
)

@Composable
fun PermissionsScreen(items: List<PermissionItem>, onBack: () -> Unit, onGrant: (PermissionKey) -> Unit) {
    Subpage("권한과 기기 설정", onBack) {
        val required = items.filter { !it.optional }
        val optional = items.filter { it.optional }
        Column {
            SectionLabel("알람이 확실히 울리려면 필요해요")
            PermissionGroup(required, onGrant)
        }
        if (optional.isNotEmpty()) {
            Column {
                SectionLabel("있으면 더 좋아요")
                PermissionGroup(optional, onGrant)
            }
        }
        Column {
            SectionLabel("휴대폰 설정에서 직접")
            Group {
                RowItem(
                    title = "앱 절전 제외",
                    subtitle = "설정 > 배터리 > 백그라운드 사용 제한에서 이 앱을 '제한 없음'으로",
                )
                GroupDivider()
                RowItem(
                    title = "방해 금지 예외",
                    subtitle = "방해 금지 모드를 쓴다면 예외에 '알람'이 켜져 있어야 소리가 나요",
                )
            }
        }
    }
}

@Composable
private fun PermissionGroup(items: List<PermissionItem>, onGrant: (PermissionKey) -> Unit) {
    Group {
        items.forEachIndexed { i, item ->
            if (i > 0) GroupDivider()
            RowItem(
                title = item.title,
                subtitle = item.why,
                leading = if (item.granted) R.drawable.ic_check else R.drawable.ic_alert,
                leadingTint = when {
                    // 허용됨은 평상시 상태라 중립색, 문제만 경고색
                    item.granted -> Palette.Mist
                    item.optional -> Palette.Mist
                    else -> Palette.Ember
                },
                trailing = {
                    if (!item.granted) {
                        TextButton(onClick = { onGrant(item.key) }) {
                            Text("허용", style = MaterialTheme.typography.labelLarge, color = Palette.SunText)
                        }
                    }
                },
            )
        }
    }
}
