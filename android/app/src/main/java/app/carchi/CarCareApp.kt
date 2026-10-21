@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.carchi

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun CarCareApp() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val catalog = remember { Catalog.load(context) }

    var state by remember {
        val loaded = store.load()
        val aligned = alignCatalog(loaded, catalog)
        if (aligned != loaded || store.migrated) store.save(aligned)
        mutableStateOf(aligned)
    }
    var readOnly by remember { mutableStateOf(store.readOnly) }
    var tab by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var pendingImport by remember { mutableStateOf<AppState?>(null) }

    fun persist(next: AppState): Boolean {
        val ok = store.save(next)
        if (ok) state = next
        readOnly = store.readOnly
        if (!ok) Toast.makeText(context, "ذخیره نشد", Toast.LENGTH_SHORT).show()
        return ok
    }

    fun updateReminders(next: ReminderPrefs) {
        val scheduleChanged = next.enabled != state.reminders.enabled ||
            next.hour != state.reminders.hour ||
            next.minute != state.reminders.minute
        val nextState = state.copy(
            reminders = next,
            // با تغییر ساعت، سابقه ارسال قبلی جلوی اعلان امروز را نگیرد.
            lastNotified = if (scheduleChanged) emptyMap() else state.lastNotified,
        )
        if (persist(nextState)) {
            scheduleReminders(context, next, update = true)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        CarchiTheme {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                val car = state.car
                if (car == null) {
                    if (readOnly) {
                        RecoveryScreen {
                            store.clear()
                            state = AppState()
                            readOnly = false
                        }
                    } else {
                        OnboardingScreen(catalog) { chosen ->
                            val now = System.currentTimeMillis()
                            persist(
                                AppState(
                                    car = chosen,
                                    services = catalog.parts.associate { it.id to Service(chosen.km, now) },
                                    events = emptyList(),
                                    catalogVersion = catalog.version,
                                ),
                            )
                        }
                    }
                } else {
                    DashboardScreen(
                        state = state,
                        catalog = catalog,
                        tab = tab,
                        readOnly = readOnly,
                        onTab = { tab = it },
                        onSheet = { sheet = it },
                        onOpenReminders = { sheet = Sheet.Reminder },
                        onExport = { uri ->
                            val ok = runCatching {
                                context.contentResolver.openOutputStream(uri)?.use { out ->
                                    out.write(store.encode(state).toByteArray(Charsets.UTF_8))
                                } ?: error("stream")
                            }.isSuccess
                            Toast.makeText(
                                context,
                                if (ok) "پشتیبان ذخیره شد" else "ذخیره نشد",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onImport = { uri ->
                            val imported = runCatching {
                                context.contentResolver.openInputStream(uri)
                                    ?.bufferedReader()
                                    ?.use { it.readText() }
                            }.getOrNull()
                                ?.let { store.decode(it) }
                                ?.takeIf { it.schema <= 2 }
                                ?.let { alignCatalog(store.normalize(it), catalog) }
                                ?.copy(lastNotified = emptyMap())
                            when {
                                imported == null || imported.car == null ->
                                    Toast.makeText(
                                        context,
                                        "فایل معتبر یا متعلق به این نسخه نیست",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                store.readOnly ->
                                    Toast.makeText(context, "ذخیره‌سازی قفل است", Toast.LENGTH_SHORT).show()
                                else -> pendingImport = imported
                            }
                        },
                        onReset = {
                            store.clear()
                            persist(AppState())
                            tab = 0
                            sheet = null
                            scheduleReminders(context, ReminderPrefs(), update = true)
                        },
                    )
                }

                when (val current = sheet) {
                    null -> Unit
                    else -> ModalBottomSheet(
                        onDismissRequest = { sheet = null },
                        containerColor = androidx.compose.ui.graphics.Color.White,
                    ) {
                        when (current) {
                            is Sheet.Km -> KmSheet(
                                currentKm = car?.km ?: 0,
                                onSave = { value ->
                                    if (persist(state.copy(car = car!!.copy(km = value)))) sheet = null
                                },
                            )

                            is Sheet.Part -> PartSheet(
                                row = rowsFor(state, catalog).firstOrNull { it.part.id == current.id },
                                override = car?.let {
                                    state.overrides[Catalog.key(it.modelId, current.id)]
                                },
                                onDone = { sheet = Sheet.Done(current.id) },
                                onFix = { sheet = Sheet.Fix(current.id) },
                                onInterval = { sheet = Sheet.Interval(current.id) },
                            )

                            is Sheet.Done -> DoneSheet(
                                currentKm = car?.km ?: 0,
                                onSave = { km, cost ->
                                    val now = System.currentTimeMillis()
                                    val ok = persist(
                                        state.copy(
                                            car = car!!.copy(km = maxOf(car.km, km)),
                                            services = state.services + (current.id to Service(km, now)),
                                            events = state.events + Event(current.id, km, now, cost, "done"),
                                        ),
                                    )
                                    if (ok) {
                                        sheet = null
                                        tab = 0
                                    }
                                },
                            )

                            is Sheet.Fix -> FixSheet(
                                baseKm = state.services[current.id]?.km ?: (car?.km ?: 0),
                                maxKm = car?.km ?: 0,
                                onSave = { km ->
                                    val now = System.currentTimeMillis()
                                    val ok = persist(
                                        state.copy(
                                            services = state.services + (current.id to Service(km, now)),
                                            events = state.events + Event(current.id, km, now, 0, "fix"),
                                        ),
                                    )
                                    if (ok) sheet = null
                                },
                            )

                            is Sheet.Interval -> {
                                val part = catalog.part(current.id)
                                if (part != null) {
                                    IntervalSheet(
                                        part = part,
                                        override = car?.let {
                                            state.overrides[Catalog.key(it.modelId, current.id)]
                                        },
                                        onSave = { km, months ->
                                            val c = car
                                            if (c != null) {
                                                val key = Catalog.key(c.modelId, current.id)
                                                val ok = persist(
                                                    state.copy(
                                                        overrides = state.overrides + (
                                                            key to IntervalOverride(
                                                                km = km,
                                                                months = months,
                                                                updatedAt = System.currentTimeMillis(),
                                                            )
                                                            ),
                                                    ),
                                                )
                                                if (ok) sheet = null
                                            }
                                        },
                                        onReset = {
                                            val c = car
                                            if (c != null) {
                                                val key = Catalog.key(c.modelId, current.id)
                                                if (persist(state.copy(overrides = state.overrides - key))) sheet = null
                                            }
                                        },
                                    )
                                }
                            }

                            is Sheet.Reminder -> ReminderSheet(
                                prefs = state.reminders,
                                parts = catalog.parts,
                                onChange = { updateReminders(it) },
                                onTest = { sendTestNotification(context) },
                            )
                        }
                    }
                }

                pendingImport?.let { incoming ->
                    val m = catalog.model(incoming.car?.modelId)
                    AlertDialog(
                        onDismissRequest = { pendingImport = null },
                        title = { Text("جایگزینی داده‌ها؟") },
                        text = {
                            Text(
                                "داده فعلی با «${m?.brand ?: ""} ${m?.name ?: ""}» و " +
                                    "${incoming.events.size.fa()} رکورد تاریخچه جایگزین می‌شود. " +
                                    "نسخه فعلی در بکاپ می‌ماند.",
                                fontSize = 13.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (persist(incoming)) {
                                        scheduleReminders(context, incoming.reminders, update = true)
                                        pendingImport = null
                                        tab = 0
                                        Toast.makeText(context, "بازیابی شد", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            ) { Text("بازیابی") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingImport = null }) { Text("لغو") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun RecoveryScreen(onStartFresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("فایل داده‌ها خوانده نشد", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(
            "فایل قبلی خراب بوده و کنار گذاشته شده است. برای جلوگیری از بازنویسی، ذخیره‌سازی موقتاً خاموش است.",
            fontSize = 14.sp,
            color = Muted,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onStartFresh,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        ) {
            Text("شروع تازه", fontSize = 16.sp)
        }
    }
}
