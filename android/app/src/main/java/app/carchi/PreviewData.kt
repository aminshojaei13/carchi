package app.carchi

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.ActivityOptionsCompat

/** ثبت‌نام‌های ActivityResult در Preview واقعی نیستند؛ این جایگزین بی‌اثر جلوی کرش را می‌گیرد. */
private object PreviewResultRegistry : ActivityResultRegistry() {
    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) = Unit
}

private object PreviewRegistryOwner : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry = PreviewResultRegistry
}

@Composable
fun PreviewShell(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalActivityResultRegistryOwner provides PreviewRegistryOwner,
    ) {
        CarchiTheme {
            Surface(Modifier.fillMaxSize(), color = Bg) { content() }
        }
    }
}

internal val previewCatalog = Catalog(
    version = 2,
    models = listOf(
        ModelSpec("tara", "تارا", "اتوماتیک", manualUrl = "https://example.com/tara.pdf"),
        ModelSpec("s5", "هایما", "S5"),
        ModelSpec("quick", "کوییک", "R"),
    ),
    parts = listOf(
        Part("oil", "روغن موتور", 8000, 6, hint = "روغن سینتتیک", safety = false),
        Part("oilfilter", "فیلتر روغن", 8000, 6),
        Part("airfilter", "فیلتر هوا", 15000, 12),
        Part("brakeF", "لنت جلو", 40000, 24, safety = true),
        Part("battery", "باتری", 0, 36),
    ),
)

private const val DAY = 86_400_000L

internal val previewState = AppState(
    car = Car(
        modelId = "tara",
        year = 1402,
        km = 30000,
        startedAt = System.currentTimeMillis() - 120 * DAY,
    ),
    services = mapOf(
        "oil" to Service(24000, System.currentTimeMillis() - 30 * DAY),
        "brakeF" to Service(1000, System.currentTimeMillis() - 400 * DAY),
    ),
    events = listOf(
        Event("oil", 24000, System.currentTimeMillis() - 30 * DAY, 1_800_000),
        Event("brakeF", 1000, System.currentTimeMillis() - 400 * DAY, 4_500_000),
    ),
    reminders = ReminderPrefs(enabled = true, hour = 9, minute = 30),
    catalogVersion = 2,
)

internal fun previewRows() = rowsFor(previewState, previewCatalog)
