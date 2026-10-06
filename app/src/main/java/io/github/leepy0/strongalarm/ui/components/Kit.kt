@file:OptIn(ExperimentalLayoutApi::class)

package io.github.leepy0.strongalarm.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.R
import io.github.leepy0.strongalarm.ui.theme.Palette

/** 화면 좌우 여백 */
val ScreenPadding = 20.dp

@Composable
fun AppIcon(@DrawableRes id: Int, tint: Color, modifier: Modifier = Modifier, size: Int = 22) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size.dp))
}

/** 묶음 위 작은 제목 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Palette.Mist,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** 설정 행 묶음 (한 덩어리의 둥근 표면) */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Dusk),
        content = content,
    )
}

@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 1.dp, color = Palette.Line)
}

/** 묶음 안의 한 줄. onClick이 있으면 오른쪽 화살표 */
@Composable
fun RowItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = Palette.Mist,
    @DrawableRes leading: Int? = null,
    leadingTint: Color = Palette.Mist,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            AppIcon(leading, leadingTint, Modifier.padding(end = 12.dp), size = 20)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Mist)
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        trailing?.invoke()
        if (onClick != null && trailing == null) {
            AppIcon(R.drawable.ic_chevron, Palette.Mist, Modifier.padding(start = 6.dp), size = 18)
        }
    }
}

/** 하위 화면 공통 틀: 뒤로 + 제목 + 스크롤 본문 */
@Composable
fun Subpage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = ScreenPadding, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { AppIcon(R.drawable.ic_back, Palette.Ink) }
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = ScreenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
    }
}

enum class ChipTone { SUN, MOON, PLAIN }

/** 키워드 칩: ✕로 삭제, '추가'로 입력 */
@Composable
fun KeywordChips(keywords: List<String>, tone: ChipTone, onChange: (List<String>) -> Unit) {
    val (bg, fg) = when (tone) {
        ChipTone.SUN -> Palette.SunSoft to Palette.Sun
        ChipTone.MOON -> Palette.MoonSoft to Palette.Moon
        ChipTone.PLAIN -> Palette.DuskHigh to Palette.Ink
    }
    var adding by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun commit() {
        val k = input.trim()
        if (k.isNotEmpty() && k !in keywords) onChange(keywords + k)
        input = ""
        adding = false
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keywords.forEach { k ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onChange(keywords - k) }
                    .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(k, style = MaterialTheme.typography.labelLarge, color = fg)
                AppIcon(R.drawable.ic_close, fg.copy(alpha = 0.7f), Modifier.padding(start = 6.dp), size = 14)
            }
        }
        if (adding) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, fg, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .width(110.dp),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelLarge.copy(color = Palette.Ink),
                    cursorBrush = SolidColor(fg),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier.focusRequester(focus),
                )
            }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        } else {
            Row(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, Palette.Line, CircleShape)
                    .clickable { adding = true }
                    .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(R.drawable.ic_plus, Palette.Mist, Modifier.padding(end = 4.dp), size = 14)
                Text("추가", style = MaterialTheme.typography.labelLarge, color = Palette.Mist)
            }
        }
    }
}

/** 결과 꼬리표: 울림(해) / 쉼(달) */
@Composable
fun OutcomeTag(ring: Boolean) {
    val (bg, fg, text) = if (ring) Triple(Palette.SunSoft, Palette.Sun, "울림") else Triple(Palette.MoonSoft, Palette.Moon, "쉼")
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 상태 줄: 아이콘 + 문장 + 선택 동작 */
@Composable
fun StatusLine(ok: Boolean?, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val tint = when (ok) {
        true -> Palette.Sun
        false -> Palette.Ember
        null -> Palette.Mist
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(if (ok == false) R.drawable.ic_alert else R.drawable.ic_check, tint, size = 18)
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok == false) Palette.Ink else Palette.Mist,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = Palette.Sun)
        }
    }
}

@Composable
fun VSpace(dp: Int) = Spacer(Modifier.height(dp.dp))
