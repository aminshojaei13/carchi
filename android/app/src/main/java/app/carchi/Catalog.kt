package app.carchi

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Part(
    val id: String,
    val name: String,
    val km: Int = 0,
    val months: Int = 12,
    val hint: String? = null,
    val safety: Boolean = false,
    val beltOnly: Boolean = false,
    /** estimated | custom | official */
    val confidence: String = "estimated",
    val source: String? = null,
)

@Serializable
data class ModelSpec(
    val id: String,
    val brand: String,
    val name: String,
    val chain: Boolean = false,
    val source: String? = null,
    /** لینک دفترچه راهنمای رسمی (PDF) */
    val manualUrl: String? = null,
)

@Serializable
data class Catalog(
    val version: Int = 1,
    val models: List<ModelSpec>,
    val parts: List<Part>,
) {
    fun model(id: String?): ModelSpec? = models.firstOrNull { it.id == id }

    fun part(id: String?): Part? = parts.firstOrNull { it.id == id }

    /** بازه مؤثر: تنظیم شخصی کاربر همیشه بر کاتالوگ اولویت دارد. */
    fun effectiveParts(
        modelId: String?,
        overrides: Map<String, IntervalOverride>,
    ): List<Part> = parts.map { part ->
        val o = overrides[key(modelId, part.id)]
        if (o == null) part
        else part.copy(km = o.km, months = o.months, confidence = "custom")
    }

    companion object {
        fun key(modelId: String?, partId: String): String = "${modelId.orEmpty()}:$partId"

        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): Catalog {
            val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
            return json.decodeFromString(Catalog.serializer(), text)
        }
    }
}

/**
 * اگر کاتالوگ نسخه جدید قطعه تازه‌ای اضافه کرده باشد، برای همان قطعه
 * یک نقطه شروع امروز می‌سازیم تا از روز اول «گذشته از موعد» نشان داده نشود.
 */
fun alignCatalog(
    state: AppState,
    catalog: Catalog,
    now: Long = System.currentTimeMillis(),
): AppState {
    val car = state.car ?: return state.copy(catalogVersion = catalog.version)
    if (state.catalogVersion >= catalog.version) return state

    val missing = catalog.parts.filter { it.id !in state.services }
    return state.copy(
        services = if (missing.isEmpty()) {
            state.services
        } else {
            state.services + missing.associate { it.id to Service(car.km, now) }
        },
        catalogVersion = catalog.version,
    )
}
