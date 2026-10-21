package app.carchi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand,
    unfocusedBorderColor = Color(0xFFD5D8DE),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedLabelColor = Brand,
    unfocusedLabelColor = Muted,
    cursorColor = Brand,
)

/** اسکرول صفحه استاندارد؛ پدینگ نوار وضعیت و پایین در یک جا. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .clipToBounds()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun CarchiCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    onClick: (() -> Unit)? = null,
    border: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val stroke = if (border) BorderStroke(1.dp, Line) else null
    if (onClick == null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = color),
            shape = shape,
            border = stroke,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    } else {
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = color),
            shape = shape,
            border = stroke,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(text, fontSize = 16.sp)
    }
}

@Composable
fun ActionTile(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = Brand,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = tone.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Text(
            text,
            color = if (enabled) tone else Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
        )
    }
}

@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    fg: Color = Color(0xFF475569),
    bg: Color = Color(0xFFF1F5F9),
) {
    Surface(color = bg, shape = RoundedCornerShape(999.dp), modifier = modifier) {
        Text(
            text,
            fontSize = 10.sp,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 3.dp)) {
        Text("$label: ", fontSize = 13.sp, color = Muted)
        Text(value, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CarchiCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun TabBar(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, label ->
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected == index) Color.White else Color(0xFFE9EBEF),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected == index) Ink else Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
fun PickerField(
    label: String,
    value: String?,
    options: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Surface(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(label, fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(2.dp))
                Text(
                    value ?: "انتخاب کن",
                    fontSize = 16.sp,
                    color = if (value != null) Ink else Muted,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 15.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    maxLength: Int = Int.MAX_VALUE,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val filtered = input.filter { c -> c.isDigit() || c in "۰۱۲۳۴۵۶۷۸۹" }
            onValueChange(filtered.take(maxLength))
        },
        label = { Text(label) },
        colors = fieldColors(),
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).background(color, CircleShape))
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = if (enabled) Ink else Muted)
            subtitle?.let { Text(it, fontSize = 11.sp, color = Muted) }
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
fun SpacerH(height: Int) = Spacer(Modifier.height(height.dp))

/* ---------------- Previews ---------------- */

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa")
@Composable
private fun PreviewTags() {
    PreviewShell {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("تخمینی")
            Tag("ایمنی", fg = NearColor, bg = NearColor.copy(alpha = 0.10f))
            Tag("تنظیم شخصی", fg = Brand, bg = BrandSoft)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa")
@Composable
private fun PreviewButtons() {
    PreviewShell {
        Column(Modifier.padding(16.dp)) {
            PrimaryButton("ذخیره", onClick = {})
            Spacer(Modifier.height(8.dp))
            ActionTile("بازگشت به پیش‌فرض", onClick = {})
            Spacer(Modifier.height(8.dp))
            ActionTile("حذف", onClick = {}, tone = OverColor)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa")
@Composable
private fun PreviewSectionCard() {
    PreviewShell {
        Column(Modifier.padding(16.dp)) {
            SectionCard("درباره") {
                InfoRow("کاتالوگ", "نسخه ۲")
                InfoRow("مدل‌ها", "۲۳")
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa")
@Composable
private fun PreviewTabBar() {
    PreviewShell {
        Column(Modifier.padding(16.dp)) {
            TabBar(listOf("وضعیت", "تاریخچه", "تنظیمات"), selected = 0, onSelect = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa")
@Composable
private fun PreviewFields() {
    PreviewShell {
        Column(Modifier.padding(16.dp)) {
            PickerField("برند", "ایران خودرو", listOf("ایران خودرو", "سایپا"), onSelect = {})
            Spacer(Modifier.height(12.dp))
            NumberField("120000", {}, "کیلومتر فعلی", placeholder = "مثلاً ۱۲۰۰۰۰")
        }
    }
}
