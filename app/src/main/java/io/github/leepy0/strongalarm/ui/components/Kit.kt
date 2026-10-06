@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package io.github.leepy0.strongalarm.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.leepy0.strongalarm.R
import io.github.leepy0.strongalarm.ui.theme.Palette

/** 화면 좌우 여백 */
val ScreenPadding = 16.dp

@Composable
fun AppIcon(@DrawableRes id: Int, tint: Color, modifier: Modifier = Modifier, size: Int = 24) {
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

/** 설정 행 묶음: 테두리 없이 배경 차이로 묶음 */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Palette.Dusk),
        content = content,
    )
}

@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 1.dp, color = Palette.Line)
}

/** 묶음 안의 한 줄 (M3 ListItem). onClick이 있으면 오른쪽 화살표 */
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
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = leading?.let { { AppIcon(it, leadingTint, size = 20) } },
        trailingContent = if (value == null && trailing == null && onClick == null) {
            null
        } else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
                    trailing?.invoke()
                    if (onClick != null && trailing == null) {
                        AppIcon(R.drawable.ic_chevron, Palette.Mist, Modifier.padding(start = 8.dp), size = 20)
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = Palette.Ink,
            supportingColor = Palette.Mist,
        ),
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/** 하위 화면 공통 틀: M3 상단 앱 바(뒤로 + 제목) + 스크롤 본문. 키보드가 올라오면 본문을 밀어 올림 */
@Composable
fun Subpage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_back, Palette.Ink) } },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Palette.Night,
                titleContentColor = Palette.Ink,
            ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = ScreenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
    }
}

enum class ChipTone { SUN, MOON, PLAIN }

/**
 * 키워드 칩 (M3 InputChip / AssistChip). 칩을 누르면 삭제(되돌리기는 호출 측 스낵바), '추가'는 입력 대화상자.
 * @param addTitle 추가 대화상자 제목
 */
@Composable
fun KeywordChips(keywords: List<String>, tone: ChipTone, addTitle: String, onChange: (List<String>) -> Unit) {
    val (bg, fg) = when (tone) {
        ChipTone.SUN -> Palette.SunSoft to Palette.SunText
        ChipTone.MOON -> Palette.MoonSoft to Palette.Moon
        ChipTone.PLAIN -> Palette.DuskHigh to Palette.Ink
    }
    var adding by remember { mutableStateOf(false) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keywords.forEach { k ->
            InputChip(
                selected = false,
                onClick = { onChange(keywords - k) },
                label = { Text(k, style = MaterialTheme.typography.labelLarge) },
                trailingIcon = { AppIcon(R.drawable.ic_close, fg, size = 16) },
                colors = InputChipDefaults.inputChipColors(containerColor = bg, labelColor = fg),
                border = null,
            )
        }
        AssistChip(
            onClick = { adding = true },
            label = { Text("추가", style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { AppIcon(R.drawable.ic_plus, Palette.Mist, size = 16) },
            colors = AssistChipDefaults.assistChipColors(labelColor = Palette.Ink),
        )
    }

    if (adding) {
        AddKeywordDialog(addTitle, keywords, onDismiss = { adding = false }) { k ->
            adding = false
            onChange(keywords + k)
        }
    }
}

@Composable
private fun AddKeywordDialog(title: String, existing: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val k = input.trim()
    val duplicate = k in existing
    val canAdd = k.isNotEmpty() && !duplicate
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Dusk,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                isError = duplicate,
                supportingText = { Text(if (duplicate) "이미 있는 키워드예요" else "일정 제목에 포함되면 적용돼요. 띄어쓰기는 무시해요.") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canAdd) onAdd(k) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(k) }, enabled = canAdd) { Text("추가") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** 결과 꼬리표: 울림 / 쉼 (색 + 글자) */
@Composable
fun OutcomeTag(ring: Boolean) {
    val (bg, fg, text) = if (ring) Triple(Palette.SunSoft, Palette.SunText, "울림") else Triple(Palette.MoonSoft, Palette.Moon, "쉼")
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * 상태 줄: 아이콘 + 문장 + 선택 동작.
 * 평상시(ok=true·null)는 회색, 조치가 필요한 상태(ok=false)만 경고색
 */
@Composable
fun StatusLine(ok: Boolean?, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val problem = ok == false
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(if (problem) R.drawable.ic_alert else R.drawable.ic_check, if (problem) Palette.Ember else Palette.Mist, size = 20)
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem) Palette.Ink else Palette.Mist,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = Palette.SunText)
        }
    }
}
