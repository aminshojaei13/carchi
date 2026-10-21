package app.carchi

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

sealed interface Sheet {
    data object Km : Sheet
    data class Part(val id: String) : Sheet
    data class Done(val id: String) : Sheet
    data class Fix(val id: String) : Sheet
    data class Interval(val id: String) : Sheet
    data object Reminder : Sheet
}

@Composable
private fun SheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        content = content,
    )
}

/* ---------------- کیلومتر ---------------- */

@Composable
fun KmSheet(currentKm: Int, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf(currentKm.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var allowLower by remember { mutableStateOf(false) }

    SheetColumn {
        Text("کیلومتر فعلی", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        NumberField(value = value, onValueChange = { value = it; allowLower = false }, label = "کیلومتر")
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = OverColor, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        ActionTile("+ ۱٬۰۰۰") {
            val current = parseNumber(value) ?: currentKm
            value = (current + 1000).toString()
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = "ثبت",
            onClick = {
                val parsed = parseNumber(value)
                when {
                    parsed == null || parsed < 0 -> error = "عدد را درست وارد کن"
                    parsed < currentKm && !allowLower -> {
                        allowLower = true
                        error = "کمتر از کارکرد فعلی است. اگر اشتباه ثبت شده، دوباره بزن."
                    }
                    else -> onSave(parsed)
                }
            },
        )
    }
}

/* ---------------- جزئیات قطعه ---------------- */

@Composable
fun PartSheet(
    row: Row?,
    override: IntervalOverride?,
    onDone: () -> Unit,
    onFix: () -> Unit,
    onInterval: () -> Unit,
) {
    if (row == null) return
    SheetColumn {
        Text(row.part.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (override != null) "بازه: تنظیم شخصی تو" else "بازه: تخمینی (پیش‌فرض اپ)",
            fontSize = 12.sp,
            color = if (override != null) Brand else Muted,
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                InfoRow("بازه", "${row.part.km.fa()} کیلومتر / ${row.part.months.fa()} ماه")
                InfoRow("آخرین سرویس", "${row.baseKm.fa()} کیلومتر · ${jalali(row.baseMs).fa()}")
                InfoRow("موعد بعدی", "${row.dueKm?.fa() ?: "—"} کیلومتر · ${jalali(row.dueMs).fa()}")
                InfoRow("وضعیت", row.remainingText())
                row.part.hint?.let { InfoRow("یادداشت", it) }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("انجام شد", onDone)
        Spacer(Modifier.height(8.dp))
        ActionTile("آخرین سرویس را اصلاح کن", onClick = onFix)
        Spacer(Modifier.height(8.dp))
        ActionTile("تنظیم بازه تعویض", onClick = onInterval)
    }
}

/* ---------------- انجام شد ---------------- */

@Composable
fun DoneSheet(currentKm: Int, onSave: (Int, Int) -> Unit) {
    var km by remember { mutableStateOf(currentKm.toString()) }
    var cost by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var allowLower by remember { mutableStateOf(false) }

    SheetColumn {
        Text("انجام شد", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        NumberField(
            value = km,
            onValueChange = { km = it; allowLower = false; error = null },
            label = "کیلومتر",
        )
        Spacer(Modifier.height(12.dp))
        NumberField(value = cost, onValueChange = { cost = it }, label = "هزینه (اختیاری)", placeholder = "تومان")
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = OverColor, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "ثبت",
            onClick = {
                val parsedKm = parseNumber(km)
                when {
                    parsedKm == null || parsedKm <= 0 -> error = "کیلومتر معتبر لازم است"
                    parsedKm < currentKm && !allowLower -> {
                        allowLower = true
                        error = "کمتر از کیلومتر فعلی است. اگر مطمئنی دوباره بزن."
                    }
                    else -> onSave(parsedKm, parseNumber(cost) ?: 0)
                }
            },
        )
    }
}

/* ---------------- اصلاح سرویس ---------------- */

@Composable
fun FixSheet(baseKm: Int, maxKm: Int, onSave: (Int) -> Unit) {
    var km by remember { mutableStateOf(baseKm.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    SheetColumn {
        Text("اصلاح آخرین سرویس", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("تاریخ، امروز ثبت می‌شود.", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(14.dp))
        NumberField(value = km, onValueChange = { km = it }, label = "کیلومتر آخرین سرویس")
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = OverColor, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "ثبت",
            onClick = {
                val parsed = parseNumber(km)
                when {
                    parsed == null || parsed <= 0 -> error = "کیلومتر معتبر لازم است"
                    maxKm > 0 && parsed > maxKm -> error = "بیشتر از کیلومتر فعلی خودرو است"
                    else -> onSave(parsed)
                }
            },
        )
    }
}

/* ---------------- بازه تعویض ---------------- */

@Composable
fun IntervalSheet(
    part: Part,
    override: IntervalOverride?,
    onSave: (Int, Int) -> Unit,
    onReset: () -> Unit,
) {
    var km by remember { mutableStateOf((override?.km ?: part.km).toString()) }
    var months by remember { mutableStateOf((override?.months ?: part.months).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmWide by remember { mutableStateOf(false) }

    val defaultKm = part.km
    val defaultMonths = part.months

    SheetColumn {
        Text("بازه تعویض", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "این عدد فقط برای مدل خودروی تو ذخیره می‌شود و برای بقیه کاربران تغییری نمی‌دهد.",
            fontSize = 12.sp,
            color = Muted,
        )
        Spacer(Modifier.height(14.dp))
        NumberField(
            value = km,
            onValueChange = { km = it; confirmWide = false; error = null },
            label = "هر چند کیلومتر",
        )
        Spacer(Modifier.height(12.dp))
        NumberField(
            value = months,
            onValueChange = { months = it; confirmWide = false; error = null },
            label = "هر چند ماه",
        )
        Spacer(Modifier.height(8.dp))
        Text("پیش‌فرض اپ: ${defaultKm.fa()} کیلومتر / ${defaultMonths.fa()} ماه", fontSize = 11.sp, color = Muted)
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = OverColor, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "ذخیره بازه",
            onClick = {
                val k = parseNumber(km)
                val m = parseNumber(months)
                when {
                    k == null || k < 0 -> error = "بازه کیلومتری را درست وارد کن"
                    m == null || m < 1 || m > 60 -> error = "بازه ماهانه بین ۱ تا ۶۰ باشد"
                    defaultKm > 0 && k < 500 -> error = "کمتر از ۵۰۰ کیلومتر منطقی نیست"
                    !confirmWide && defaultKm > 0 && (k < defaultKm / 2 || k > defaultKm * 2) -> {
                        confirmWide = true
                        error = "این عدد با پیش‌فرض خیلی فاصله دارد. اگر مطمئنی دوباره بزن."
                    }
                    !confirmWide && (m < defaultMonths / 2 || m > defaultMonths * 2) -> {
                        confirmWide = true
                        error = "این عدد با پیش‌فرض خیلی فاصله دارد. اگر مطمئنی دوباره بزن."
                    }
                    else -> onSave(k, m)
                }
            },
        )
        if (override != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("بازگشت به پیش‌فرض اپ", fontSize = 13.sp, color = Muted)
            }
        }
    }
}

/* ---------------- تنظیمات یادآور ---------------- */

@Composable
fun ReminderSheet(
    prefs: ReminderPrefs,
    parts: List<Part>,
    onChange: (ReminderPrefs) -> Unit,
    onTest: () -> Unit,
) {
    val ctx = LocalContext.current
    var testAfterGrant by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current

    // اگر کاربر از تنظیمات سیستم برگردد، وضعیت مجوز/کانال دوباره خوانده شود.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        when {
            !granted -> {
                Toast.makeText(ctx, "بدون مجوز اعلان، یادآوری کار نمی‌کند", Toast.LENGTH_LONG).show()
                openNotificationSettings(ctx)
            }
            testAfterGrant -> onTest()
            else -> onChange(prefs.copy(enabled = true))
        }
        testAfterGrant = false
    }

    val channelBlocked = remember(resumeTick) { isChannelBlocked(ctx) }
    val notificationsOff = remember(resumeTick) {
        !NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun canNotify(): Boolean =
        hasNotificationPermission(ctx) && !notificationsOff && !channelBlocked

    SheetColumn {
        Text("یادآوری", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("همه‌چیز اختیاری است؛ هر وقت خواستی خاموشش کن.", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(12.dp))

        SwitchRow(
            title = "یادآوری",
            subtitle = if (prefs.enabled) "روشن؛ ساعت ${timeText(prefs.hour, prefs.minute)}" else "هیچ اعلانی نمی‌فرستیم",
            checked = prefs.enabled,
            onCheckedChange = { on ->
                if (!on) {
                    onChange(prefs.copy(enabled = false))
                } else if (!hasNotificationPermission(ctx)) {
                    testAfterGrant = false
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (channelBlocked) {
                    openChannelSettings(ctx)
                } else if (notificationsOff) {
                    openNotificationSettings(ctx)
                } else {
                    onChange(prefs.copy(enabled = true))
                }
            },
        )

        if (prefs.enabled && (channelBlocked || notificationsOff)) {
            Spacer(Modifier.height(8.dp))
            NoticeCard(
                title = "اعلان در گوشی خاموش است",
                text = if (channelBlocked) {
                    "کانال «یادآوری سرویس» در تنظیمات سیستم خاموش شده؛ تا روشنش نکنی اعلانی نمی‌آید."
                } else {
                    "اعلان‌های کارچی در تنظیمات سیستم خاموش است."
                },
                tone = NearColor,
            )
            Spacer(Modifier.height(6.dp))
            ActionTile("باز کردن تنظیمات اعلان") {
                if (channelBlocked) openChannelSettings(ctx) else openNotificationSettings(ctx)
            }
        }

        HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 10.dp))

        Text("ساعت اعلان", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerField(
                label = "ساعت",
                value = prefs.hour.fa(),
                options = (0..23).map { it.fa() },
                modifier = Modifier.weight(1f),
                onSelect = { onChange(prefs.copy(hour = parseNumber(it) ?: prefs.hour)) },
            )
            PickerField(
                label = "دقیقه",
                value = prefs.minute.toString().padStart(2, '0').persianDigits(),
                options = listOf("۰۰", "۱۵", "۳۰", "۴۵"),
                modifier = Modifier.weight(1f),
                onSelect = { onChange(prefs.copy(minute = parseNumber(it) ?: prefs.minute)) },
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("چه زمانی خبر بده", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceTile(
                title = "نزدیک موعد",
                selected = !prefs.onlyOverdue,
                modifier = Modifier.weight(1f),
            ) { onChange(prefs.copy(onlyOverdue = false)) }
            ChoiceTile(
                title = "فقط گذشته",
                selected = prefs.onlyOverdue,
                modifier = Modifier.weight(1f),
            ) { onChange(prefs.copy(onlyOverdue = true)) }
        }

        Spacer(Modifier.height(14.dp))
        Text("کدام قطعه‌ها خبر بدهند", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(4.dp))
        parts.forEach { part ->
            SwitchRow(
                title = part.name,
                checked = part.id !in prefs.mutedParts,
                onCheckedChange = { on ->
                    val muted = if (on) prefs.mutedParts - part.id else prefs.mutedParts + part.id
                    onChange(prefs.copy(mutedParts = muted))
                },
            )
        }

        Spacer(Modifier.height(10.dp))
        ActionTile("ارسال اعلان آزمایشی") {
            if (canNotify()) {
                onTest()
            } else if (!hasNotificationPermission(ctx)) {
                testAfterGrant = true
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(ctx)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "بعضی گوشی‌ها (سامسونگ، شیائومی) برای صرفه‌جویی باتری اعلان‌ها را محدود می‌کنند. " +
                "اگر اعلان نیامد، کارچی را در بهینه‌سازی باتری استثنا کن.",
            fontSize = 11.sp,
            color = Muted,
        )
    }
}

@Composable
private fun ChoiceTile(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (selected) BrandSoft else Color(0xFFF1F5F9),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Brand else Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
        )
    }
}

/* ---------------- پیش‌نمایش‌ها ---------------- */

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa", widthDp = 400, heightDp = 700)
@Composable
private fun PreviewKmSheet() {
    PreviewShell { KmSheet(30000) {} }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa", widthDp = 400, heightDp = 760)
@Composable
private fun PreviewPartSheet() {
    PreviewShell {
        PartSheet(
            row = previewRows().first(),
            override = null,
            onDone = {},
            onFix = {},
            onInterval = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa", widthDp = 400, heightDp = 760)
@Composable
private fun PreviewIntervalSheet() {
    PreviewShell {
        IntervalSheet(
            part = previewCatalog.parts.first(),
            override = null,
            onSave = { _, _ -> },
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa", widthDp = 400, heightDp = 900)
@Composable
private fun PreviewReminderSheet() {
    PreviewShell {
        ReminderSheet(
            prefs = previewState.reminders,
            parts = previewCatalog.parts,
            onChange = {},
            onTest = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, locale = "fa", widthDp = 400, heightDp = 620)
@Composable
private fun PreviewDoneSheet() {
    PreviewShell { DoneSheet(30000) { _, _ -> } }
}
