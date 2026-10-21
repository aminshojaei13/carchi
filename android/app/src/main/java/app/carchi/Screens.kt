package app.carchi

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------------- آنبوردینگ ---------------- */

@Composable
fun OnboardingScreen(catalog: Catalog, onDone: (Car) -> Unit) {
    val brands = remember(catalog) { catalog.models.map { it.brand }.distinct() }
    var brand by remember { mutableStateOf<String?>(null) }
    var modelId by remember { mutableStateOf<String?>(null) }
    var year by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val models = catalog.models.filter { it.brand == brand }

    ScreenColumn {
        Spacer(Modifier.height(24.dp))
        Text("ماشینت را ثبت کن", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("سه فیلد. کمتر از یک دقیقه.", fontSize = 14.sp, color = Muted)

        Spacer(Modifier.height(24.dp))

        PickerField(
            label = "برند",
            value = brand,
            options = brands,
            onSelect = { brand = it; modelId = null },
        )
        Spacer(Modifier.height(14.dp))
        PickerField(
            label = "مدل",
            value = models.firstOrNull { it.id == modelId }?.let { "${it.brand} ${it.name}" },
            options = models.map { "${it.brand} ${it.name}" },
            enabled = brand != null,
            onSelect = { label -> modelId = models.firstOrNull { "${it.brand} ${it.name}" == label }?.id },
        )
        Spacer(Modifier.height(14.dp))
        NumberField(
            value = year,
            onValueChange = { year = it },
            label = "سال ساخت",
            placeholder = "مثلاً ۱۳۹۸",
            maxLength = 4,
        )
        Spacer(Modifier.height(14.dp))
        NumberField(
            value = km,
            onValueChange = { km = it },
            label = "کیلومتر فعلی",
            placeholder = "مثلاً ۱۲۰۰۰۰",
        )

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = OverColor, fontSize = 13.sp)
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = "شروع",
            onClick = {
                val y = parseNumber(year)
                val k = parseNumber(km)
                when {
                    modelId == null -> error = "مدل را انتخاب کن"
                    y == null || y < 1300 || y > 1420 -> error = "سال ساخت را درست وارد کن"
                    k == null || k < 0 -> error = "کیلومتر را درست وارد کن"
                    else -> onDone(
                        Car(
                            modelId = modelId!!,
                            year = y,
                            km = k,
                            startedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            },
        )

        Spacer(Modifier.height(28.dp))
        Text(
            "بازه‌های تعویض در این نسخه تخمینی‌اند و مبنای رسمی ندارند.\n" +
                "قبل از تصمیم واقعی با دفترچه خودرو یا مکانیک تطبیق بده.",
            fontSize = 11.sp,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ---------------- داشبورد ---------------- */

@Composable
fun DashboardScreen(
    state: AppState,
    catalog: Catalog,
    tab: Int,
    readOnly: Boolean,
    onTab: (Int) -> Unit,
    onSheet: (Sheet) -> Unit,
    onOpenReminders: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onReset: () -> Unit,
) {
    val car = state.car ?: return
    val model = catalog.model(car.modelId)
    val rows = rowsFor(state, catalog)

    ScreenColumn {
        CarHeader(
            title = "${model?.brand ?: ""} ${model?.name ?: ""} · ${car.year.faYear()}",
            km = car.km,
            onKm = { onSheet(Sheet.Km) },
        )

        Spacer(Modifier.height(12.dp))

        TabBar(
            items = listOf("وضعیت", "تاریخچه", "تنظیمات"),
            selected = tab,
            onSelect = onTab,
        )

        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> StatusTab(
                rows = rows,
                readOnly = readOnly,
                remindersOn = state.reminders.enabled,
                onPart = { onSheet(Sheet.Part(it)) },
                onOpenReminders = onOpenReminders,
            )
            1 -> HistorySection(state, catalog)
            else -> SettingsScreen(
                state = state,
                catalog = catalog,
                readOnly = readOnly,
                onOpenReminders = onOpenReminders,
                onExport = onExport,
                onImport = onImport,
                onReset = onReset,
            )
        }
    }
}

@Composable
private fun CarHeader(title: String, km: Int, onKm: () -> Unit) {
    CarchiCard(color = Brand) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = HeaderMuted)
                Spacer(Modifier.height(4.dp))
                Text(km.fa(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("کیلومتر", fontSize = 12.sp, color = HeaderMuted)
            }
            Surface(
                onClick = onKm,
                color = Color.White.copy(alpha = 0.16f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            ) {
                Text(
                    "ثبت کیلومتر",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

private val HeaderMuted = Color(0xFFBFDBFE)

@Composable
private fun StatusTab(
    rows: List<Row>,
    readOnly: Boolean,
    remindersOn: Boolean,
    onPart: (String) -> Unit,
    onOpenReminders: () -> Unit,
) {
    val over = rows.count { it.level == Level.OVER }
    val near = rows.count { it.level == Level.NEAR }
    val next = rows.firstOrNull()

    if (readOnly) {
        NoticeCard(
            title = "ذخیره‌سازی قفل است",
            text = "تغییرات ذخیره نمی‌شوند. در تنظیمات توضیح داده شده.",
            tone = OverColor,
        )
        Spacer(Modifier.height(10.dp))
    }
    if (!remindersOn) {
        CarchiCard(color = Color(0xFFFFFBEB)) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("یادآوری خاموش است", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E))
                    Text("ساعت و قطعه‌ها را خودت انتخاب می‌کنی.", fontSize = 11.sp, color = Muted)
                }
                TextButton(onClick = onOpenReminders) {
                    Text("تنظیم", fontSize = 13.sp, color = Brand)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    val bannerColor = when {
        over > 0 -> OverColor
        near > 0 -> NearColor
        else -> OkColor
    }
    val bannerTitle = when {
        over > 0 -> "${over.fa()} قطعه از موعد گذشته"
        near > 0 -> "${near.fa()} قطعه نزدیک موعد"
        else -> "همه‌چیز مرتب است"
    }

    Surface(
        color = bannerColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(bannerTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            next?.let {
                Text(
                    "${it.part.name}: ${it.remainingText()}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    CarchiCard {
        rows.forEachIndexed { index, row ->
            PartRow(row) { onPart(row.part.id) }
            if (index != rows.lastIndex) HorizontalDivider(color = Line)
        }
    }

    Spacer(Modifier.height(20.dp))
    Text(
        "بازه‌ها تخمینی‌اند، نه منبع رسمی. هر بازه را می‌توانی از داخل همان قطعه اصلاح کنی.\n" +
            "برای قطعات ایمنی (ترمز، تسمه، لاستیک) با مکانیک چک کن.",
        fontSize = 11.sp,
        color = Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun NoticeCard(title: String, text: String, tone: Color) {
    Surface(
        color = tone.copy(alpha = 0.06f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tone)
            Text(text, fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun PartRow(row: Row, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(row.level.color())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.part.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                if (row.part.safety) {
                    Spacer(Modifier.width(6.dp))
                    Tag("ایمنی")
                }
                Spacer(Modifier.width(6.dp))
                if (row.overridden) {
                    Tag("تنظیم شخصی", fg = Brand, bg = BrandSoft)
                } else {
                    Tag("تخمینی")
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(row.remainingText(), fontSize = 12.5.sp, color = Muted)
        }
        Text("›", fontSize = 22.sp, color = row.level.color())
    }
}

@Composable
fun HistorySection(state: AppState, catalog: Catalog) {
    val events = state.events.reversed()
    val totalCost = events.sumOf { it.cost }

    if (totalCost > 0) {
        CarchiCard {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("مجموع هزینه ثبت‌شده", fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
                Text("${totalCost.fa()} تومان", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    CarchiCard {
        if (events.isEmpty()) {
            Text(
                "هنوز چیزی ثبت نشده",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            Column {
                events.forEachIndexed { index, event ->
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(Brand)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                catalog.parts.firstOrNull { it.id == event.partId }?.name ?: event.partId,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                buildString {
                                    append(event.km.fa())
                                    append(" کیلومتر · ")
                                    append(jalali(event.date).fa())
                                    if (event.cost > 0) {
                                        append(" · ")
                                        append(event.cost.fa())
                                        append(" تومان")
                                    }
                                },
                                fontSize = 12.sp,
                                color = Muted,
                            )
                        }
                    }
                    if (index != events.lastIndex) HorizontalDivider(color = Line)
                }
            }
        }
    }
}

/* ---------------- تنظیمات ---------------- */

@Composable
fun SettingsScreen(
    state: AppState,
    catalog: Catalog,
    readOnly: Boolean,
    onOpenReminders: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onReset: () -> Unit,
) {
    val ctx = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExport) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }

    val prefs = state.reminders

    SectionCard("یادآوری") {
        Text(
            if (prefs.enabled) "روشن؛ ساعت ${timeText(prefs.hour, prefs.minute)}" else "خاموش",
            fontSize = 13.sp,
            color = Muted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (prefs.onlyOverdue) "فقط وقتی از موعد گذشته باشد خبر می‌دهد."
            else "وقتی قطعه نزدیک موعد شد هم خبر می‌دهد.",
            fontSize = 11.sp,
            color = Muted,
        )
        Spacer(Modifier.height(10.dp))
        ActionTile("تنظیم ساعت، قطعه‌ها و حالت اعلان", onClick = onOpenReminders)
    }

    Spacer(Modifier.height(10.dp))

    SectionCard("پشتیبان‌گیری") {
        Text("خودرو، سرویس‌ها، بازه‌های شخصی و تاریخچه در یک فایل.", fontSize = 13.sp, color = Muted)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("ذخیره فایل", Modifier.weight(1f)) { exportLauncher.launch("carchi-backup.json") }
            ActionTile("بازیابی", Modifier.weight(1f)) { importLauncher.launch(arrayOf("application/json", "text/plain")) }
        }
    }

    if (readOnly) {
        Spacer(Modifier.height(10.dp))
        NoticeCard(
            title = "ذخیره‌سازی قفل است",
            text = "فایل داده‌ها قابل خواندن نبود؛ برای جلوگیری از پاک شدن چیزی نوشته نمی‌شود.",
            tone = OverColor,
        )
    }

    Spacer(Modifier.height(10.dp))

    SectionCard("درباره") {
        InfoRow("کاتالوگ", "نسخه ${catalog.version.fa()}")
        InfoRow("مدل‌ها", catalog.models.size.fa())
        InfoRow("قطعات", catalog.parts.size.fa())
        val manual = catalog.model(state.car?.modelId)?.manualUrl
        if (manual != null) {
            Spacer(Modifier.height(10.dp))
            ActionTile("دفترچه راهنمای این خودرو (PDF)") { openManual(ctx, manual) }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { confirmReset = true }) {
            Text("حذف خودرو و شروع دوباره", fontSize = 12.sp, color = OverColor)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("حذف خودرو؟") },
            text = { Text("تاریخچه و هزینه‌ها آرشیو می‌شوند و صفحه از نو شروع می‌شود.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) {
                    Text("حذف", color = OverColor)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("لغو") } },
        )
    }
}

/* ---------------- پیش‌نمایش‌ها ---------------- */

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa", widthDp = 400, heightDp = 800)
@Composable
private fun PreviewOnboarding() {
    PreviewShell { OnboardingScreen(previewCatalog) {} }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa", widthDp = 400, heightDp = 900)
@Composable
private fun PreviewDashboardStatus() {
    PreviewShell {
        DashboardScreen(
            state = previewState,
            catalog = previewCatalog,
            tab = 0,
            readOnly = false,
            onTab = {},
            onSheet = {},
            onOpenReminders = {},
            onExport = {},
            onImport = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa", widthDp = 400, heightDp = 900)
@Composable
private fun PreviewDashboardHistory() {
    PreviewShell {
        DashboardScreen(
            state = previewState,
            catalog = previewCatalog,
            tab = 1,
            readOnly = false,
            onTab = {},
            onSheet = {},
            onOpenReminders = {},
            onExport = {},
            onImport = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa", widthDp = 400, heightDp = 900)
@Composable
private fun PreviewSettings() {
    PreviewShell {
        ScreenColumn {
            SettingsScreen(
                state = previewState,
                catalog = previewCatalog,
                readOnly = false,
                onOpenReminders = {},
                onExport = {},
                onImport = {},
                onReset = {},
            )
        }
    }
}
