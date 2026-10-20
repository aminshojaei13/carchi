@file:OptIn(ExperimentalMaterial3Api::class)

package app.carchi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFFF4F5F7)
private val Ink = Color(0xFF0F172A)
private val Muted = Color(0xFF6B7280)
private val Line = Color(0xFFE5E7EB)
private val Brand = Color(0xFF2563EB)
private val OkColor = Color(0xFF059669)
private val NearColor = Color(0xFFD97706)
private val OverColor = Color(0xFFDC2626)

private fun Level.color(): Color = when (this) {
    Level.OVER -> OverColor
    Level.NEAR -> NearColor
    Level.OK -> OkColor
}

private sealed interface Sheet {
    data object Km : Sheet
    data class Part(val id: String) : Sheet
    data class Done(val id: String) : Sheet
    data class Fix(val id: String) : Sheet
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand,
    unfocusedBorderColor = Color(0xFFD5D8DE),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedLabelColor = Brand,
    unfocusedLabelColor = Muted,
    cursorColor = Brand,
)

@Composable
fun CarCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            background = Bg,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Muted,
            outline = Line,
            error = OverColor,
        ),
        content = content,
    )
}

@Composable
fun CarCareApp() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val catalog = remember { Catalog.load(context) }

    var state by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    fun persist(next: AppState) {
        state = next
        store.save(next)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        CarCareTheme {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                val car = state.car
                if (car == null) {
                    Onboarding(catalog) { chosen ->
                        val now = System.currentTimeMillis()
                        persist(
                            AppState(
                                car = chosen,
                                services = catalog.parts.associate { it.id to Service(chosen.km, now) },
                                events = catalog.parts.map { Event(it.id, chosen.km, now, 0, "baseline") },
                            ),
                        )
                    }
                } else {
                    Dashboard(
                        state = state,
                        catalog = catalog,
                        tab = tab,
                        onTab = { tab = it },
                        onSheet = { sheet = it },
                        onReset = {
                            store.clear()
                            state = AppState()
                            tab = 0
                        },
                    )
                }

                when (val current = sheet) {
                    null -> Unit
                    else -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
                        when (current) {
                            is Sheet.Km -> KmSheet(
                                currentKm = car?.km ?: 0,
                                onSave = { value ->
                                    persist(state.copy(car = car!!.copy(km = value)))
                                    sheet = null
                                },
                            )

                            is Sheet.Part -> PartSheet(
                                row = rowsFor(state, catalog).firstOrNull { it.part.id == current.id },
                                onDone = { sheet = Sheet.Done(current.id) },
                                onFix = { sheet = Sheet.Fix(current.id) },
                            )

                            is Sheet.Done -> DoneSheet(
                                currentKm = car?.km ?: 0,
                                onSave = { km, cost ->
                                    val now = System.currentTimeMillis()
                                    persist(
                                        state.copy(
                                            car = car!!.copy(km = maxOf(car.km, km)),
                                            services = state.services + (current.id to Service(km, now)),
                                            events = state.events + Event(current.id, km, now, cost, "done"),
                                        ),
                                    )
                                    sheet = null
                                    tab = 0
                                },
                            )

                            is Sheet.Fix -> FixSheet(
                                baseKm = state.services[current.id]?.km ?: (car?.km ?: 0),
                                onSave = { km ->
                                    val now = System.currentTimeMillis()
                                    persist(
                                        state.copy(
                                            services = state.services + (current.id to Service(km, now)),
                                            events = state.events + Event(current.id, km, now, 0, "fix"),
                                        ),
                                    )
                                    sheet = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- آنبوردینگ ---------------- */

@Composable
private fun Onboarding(catalog: Catalog, onDone: (Car) -> Unit) {
    val brands = remember(catalog) { catalog.models.map { it.brand }.distinct() }
    var brand by remember { mutableStateOf<String?>(null) }
    var modelId by remember { mutableStateOf<String?>(null) }
    var year by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val models = remember(brand, catalog) {
        catalog.models.filter { it.brand == brand }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .clipToBounds()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("ماشینت را ثبت کن", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("سه فیلد. کمتر از یک دقیقه.", fontSize = 14.sp, color = Muted)

        Spacer(Modifier.height(24.dp))

        Picker(
            label = "برند",
            value = brand,
            options = brands,
            onSelect = { brand = it; modelId = null },
        )
        Spacer(Modifier.height(14.dp))
        Picker(
            label = "مدل",
            value = models.firstOrNull { it.id == modelId }?.let { "${it.brand} ${it.name}" },
            options = models.map { "${it.brand} ${it.name}" },
            enabled = brand != null,
            onSelect = { label ->
                modelId = models.firstOrNull { "${it.brand} ${it.name}" == label }?.id
            },
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = year,
            onValueChange = { year = it.filter { c -> c.isDigit() || c in "۰۱۲۳۴۵۶۷۸۹" }.take(4) },
            label = { Text("سال ساخت") },
            colors = fieldColors(),
            placeholder = { Text("مثلاً ۱۳۹۸") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = km,
            onValueChange = { km = it.filter { c -> c.isDigit() || c in "۰۱۲۳۴۵۶۷۸۹" } },
            label = { Text("کیلومتر فعلی") },
            colors = fieldColors(),
            placeholder = { Text("مثلاً ۱۲۰۰۰۰") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = OverColor, fontSize = 13.sp)
        }

        Spacer(Modifier.height(22.dp))
        Button(
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
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("شروع", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "بازه‌های تعویض در این نسخه تخمینی‌اند و مبنای رسمی ندارند.\nقبل از تصمیم واقعی با دفترچه خودرو یا مکانیک تطبیق بده.",
            fontSize = 11.sp,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Picker(
    label: String,
    value: String?,
    options: List<String>,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
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

/* ---------------- داشبورد ---------------- */

@Composable
private fun Dashboard(
    state: AppState,
    catalog: Catalog,
    tab: Int,
    onTab: (Int) -> Unit,
    onSheet: (Sheet) -> Unit,
    onReset: () -> Unit,
) {
    val car = state.car ?: return
    val model = catalog.model(car.modelId)
    val rows = remember(state) { rowsFor(state, catalog) }
    val over = rows.count { it.level == Level.OVER }
    val near = rows.count { it.level == Level.NEAR }
    val next = rows.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .clipToBounds()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Brand),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${model?.brand ?: ""} ${model?.name ?: ""} · ${car.year.faYear()}",
                        fontSize = 13.sp,
                        color = Color(0xFFBFDBFE),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        car.km.fa(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text("کیلومتر", fontSize = 12.sp, color = Color(0xFFBFDBFE))
                }
                Surface(
                    onClick = { onSheet(Sheet.Km) },
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(14.dp),
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

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton("وضعیت", tab == 0, Modifier.weight(1f)) { onTab(0) }
            TabButton("تاریخچه", tab == 1, Modifier.weight(1f)) { onTab(1) }
        }

        Spacer(Modifier.height(12.dp))

        if (tab == 0) {
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

            Surface(color = bannerColor, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
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

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    rows.forEachIndexed { index, row ->
                        PartRow(row) { onSheet(Sheet.Part(row.part.id)) }
                        if (index != rows.lastIndex) HorizontalDivider(color = Line)
                    }
                }
            }
        } else {
            History(state, catalog)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "بازه‌ها تخمینی‌اند، نه منبع رسمی.\nبرای قطعات ایمنی (ترمز، تسمه، لاستیک) با مکانیک چک کن.",
            fontSize = 11.sp,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("حذف خودرو و شروع دوباره", fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White else Color(0xFFE9EBEF),
        modifier = modifier,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Ink else Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp),
        )
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
        Box(Modifier.size(9.dp).background(row.level.color(), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.part.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                if (row.part.safety) Chip("ایمنی")
                Chip("تخمینی")
            }
            Spacer(Modifier.height(2.dp))
            Text(row.remainingText(), fontSize = 12.5.sp, color = Muted)
        }
        Text("‹", fontSize = 22.sp, color = row.level.color())
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        color = Color(0xFFF1F5F9),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.padding(start = 6.dp),
    ) {
        Text(
            text,
            fontSize = 10.sp,
            color = Color(0xFF475569),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun History(state: AppState, catalog: Catalog) {
    val events = remember(state) { state.events.reversed() }
    Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                        Box(Modifier.size(9.dp).background(Brand, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    catalog.parts.firstOrNull { it.id == event.partId }?.name ?: event.partId,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (event.type == "baseline") Chip("فرض اول")
                            }
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

/* ---------------- شیت‌ها ---------------- */

@Composable
private fun KmSheet(currentKm: Int, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf(currentKm.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("کیلومتر فعلی", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = OverColor, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = {
                val current = parseNumber(value) ?: currentKm
                value = (current + 1000).toString()
            },
            color = Color(0xFFEEF2FF),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "+ ۱٬۰۰۰",
                color = Brand,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val parsed = parseNumber(value)
                when {
                    parsed == null -> error = "عدد را درست وارد کن"
                    parsed < currentKm -> error = "کیلومتر نمی‌تواند از قبلی کمتر باشد"
                    else -> onSave(parsed)
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("ثبت", fontSize = 16.sp)
        }
    }
}

@Composable
private fun PartSheet(row: Row?, onDone: () -> Unit, onFix: () -> Unit) {
    if (row == null) return
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text(row.part.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                InfoLine("آخرین سرویس", "${row.baseKm.fa()} کیلومتر · ${jalali(row.baseMs).fa()}")
                InfoLine("موعد بعدی", "${row.dueKm?.fa() ?: "—"} کیلومتر · ${jalali(row.dueMs).fa()}")
                InfoLine("وضعیت", row.remainingText())
                row.part.hint?.let { InfoLine("یادداشت", it) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("انجام شد", fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = onFix,
            color = Color(0xFFEEF2FF),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "آخرین سرویس را اصلاح کن",
                color = Brand,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$label: ", fontSize = 13.sp, color = Muted)
        Text(value, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DoneSheet(currentKm: Int, onSave: (Int, Int) -> Unit) {
    var km by remember { mutableStateOf(currentKm.toString()) }
    var cost by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("انجام شد", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = km,
            onValueChange = { km = it },
            label = { Text("کیلومتر") },
            colors = fieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = cost,
            onValueChange = { cost = it },
            label = { Text("هزینه (اختیاری)") },
            colors = fieldColors(),
            placeholder = { Text("تومان") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = OverColor, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val parsedKm = parseNumber(km)
                if (parsedKm == null) {
                    error = "کیلومتر لازم است"
                } else {
                    onSave(parsedKm, parseNumber(cost) ?: 0)
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("ثبت", fontSize = 16.sp)
        }
    }
}

@Composable
private fun FixSheet(baseKm: Int, onSave: (Int) -> Unit) {
    var km by remember { mutableStateOf(baseKm.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("اصلاح آخرین سرویس", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("تاریخ، امروز ثبت می‌شود.", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = km,
            onValueChange = { km = it },
            label = { Text("کیلومتر آخرین سرویس") },
            colors = fieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = OverColor, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val parsed = parseNumber(km)
                if (parsed == null) error = "کیلومتر لازم است" else onSave(parsed)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("ثبت", fontSize = 16.sp)
        }
    }
}
