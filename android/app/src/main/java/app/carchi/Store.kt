package app.carchi

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class Car(
    val id: String = "car1",
    val modelId: String,
    val year: Int,
    val km: Int,
    val startedAt: Long,
)

@Serializable
data class Service(val km: Int, val date: Long)

@Serializable
data class Event(
    val partId: String,
    val km: Int,
    val date: Long,
    val cost: Int = 0,
    val type: String = "done",
)

/** تنظیمات یادآوری؛ کاملاً در اختیار کاربر. */
@Serializable
data class ReminderPrefs(
    val enabled: Boolean = false,
    val hour: Int = 10,
    val minute: Int = 0,
    val onlyOverdue: Boolean = false,
    val mutedParts: Set<String> = emptySet(),
)

/** بازه‌ای که خود کاربر برای مدل خودروی خودش تنظیم کرده است. */
@Serializable
data class IntervalOverride(
    val km: Int,
    val months: Int,
    val updatedAt: Long = 0L,
)

@Serializable
data class AppState(
    val schema: Int = 2,
    val car: Car? = null,
    val services: Map<String, Service> = emptyMap(),
    val events: List<Event> = emptyList(),
    val overrides: Map<String, IntervalOverride> = emptyMap(),
    val reminders: ReminderPrefs = ReminderPrefs(),
    val catalogVersion: Int = 0,
    val lastNotified: Map<String, Long> = emptyMap(),
)

/**
 * ذخیره‌سازی امن:
 * - نوشتن اتمیک (tmp → fsync → rename)
 * - قبل از هر نوشتن، نسخه قبلی به state.bak.json می‌رود
 * - فایل خراب به state.corrupt-*.json منتقل می‌شود، نه حذف
 * - اگر main خراب باشد، بکاپ سالم بازنویسی نمی‌شود
 */
class Store(context: Context) {
    private val dir = context.filesDir
    private val file = File(dir, "state.json")
    private val backup = File(dir, "state.bak.json")
    private val tmp = File(dir, "state.tmp.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** اگر فایل و بکاپ هر دو خراب باشند true می‌شود و نوشتن متوقف می‌ماند. */
    var readOnly = false
        private set

    /** اگر آخرین [load] مهاجرت لازم داشت true می‌شود تا فراخواننده نتیجه را ذخیره کند. */
    var migrated = false
        private set

    fun load(): AppState {
        readOnly = false
        migrated = false

        // نصب تازه: هیچ داده‌ای وجود ندارد، پس قفل کردن ذخیره‌سازی معنا ندارد.
        if (!file.exists() && !backup.exists()) return AppState()

        if (file.exists()) {
            val text = runCatching { file.readText() }.getOrNull()
            text?.let(::decode)?.let { return migrate(it) }
            // main خراب است: به کنارش می‌بریم تا بکاپ سالم دست‌نخورده بماند.
            runCatching { file.renameTo(File(dir, "state.corrupt-${System.currentTimeMillis()}.json")) }
        }

        if (backup.exists()) {
            val text = runCatching { backup.readText() }.getOrNull()
            text?.let(::decode)?.let { restored ->
                // main گم شده یا خراب بوده؛ بکاپ را به‌عنوان main برمی‌گردانیم.
                runCatching {
                    FileOutputStream(file).use { out ->
                        out.write(backup.readBytes())
                        out.flush()
                        out.fd.sync()
                    }
                }
                return migrate(restored)
            }
        }

        readOnly = true
        return AppState()
    }

    /** true یعنی واقعاً روی دیسک نوشته شد. */
    fun save(state: AppState): Boolean = synchronized(lock) {
        if (readOnly) return@synchronized false
        val text = json.encodeToString(AppState.serializer(), state)
        runCatching {
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            // فقط وقتی main سالم است بکاپ را جایگزین کن.
            if (file.exists() && decode(file.readText()) != null) {
                file.copyTo(backup, overwrite = true)
            }
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }.isSuccess
    }

    /** فقط یک کلید را روی جدیدترین نسخه state علامت می‌زند. */
    fun markNotified(key: String, at: Long) {
        synchronized(lock) {
            val current = load()
            if (current.car == null || readOnly) return@synchronized
            save(current.copy(lastNotified = current.lastNotified + (key to at)))
        }
    }

    fun encode(state: AppState): String = json.encodeToString(AppState.serializer(), state)

    fun decode(text: String): AppState? =
        runCatching { json.decodeFromString(AppState.serializer(), text) }.getOrNull()

    /** مهاجرت و پاک‌سازی داده واردشده از بکاپ. */
    fun normalize(state: AppState): AppState = migrate(state)

    /** داده فعلی پاک نمی‌شود؛ آرشیو می‌شود تا بشود برگرداند. */
    fun clear() {
        val ts = System.currentTimeMillis()
        runCatching { if (file.exists()) file.renameTo(File(dir, "state.deleted-$ts.json")) }
        runCatching { if (backup.exists()) backup.renameTo(File(dir, "state.deleted-$ts.bak.json")) }
        runCatching { tmp.delete() }
        readOnly = false
    }

    private companion object {
        val lock = Any()
    }

    /** مهاجرت فقط رو‌به‌جلو. نسخه ناشناس را دست نمی‌زنیم. */
    private fun migrate(old: AppState): AppState {
        if (old.schema > 2) {
            readOnly = true
            return old
        }
        // «فرض اول» یک جزئیات داخلی بود؛ در تاریخچه کاربر جایی ندارد.
        val next = old.copy(
            schema = 2,
            events = old.events.filterNot { it.type == "baseline" },
        )
        if (next != old) migrated = true
        return next
    }
}
