package app.carchi

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

const val CHANNEL_ID = "reminders"
private const val DAILY_NOTIFY_ID = 1001
private const val TEST_NOTIFY_ID = 1002
private const val WORK_NAME = "daily-reminder"

fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
        CHANNEL_ID,
        "یادآوری سرویس",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "وقتی قطعه‌ای به موعد تعویض نزدیک شد"
    }
    context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
}

/** یادآوری روزانه؛ فقط یک خلاصه، حداکثر یک‌بار در روز. */
class ReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        val state = Store(ctx).load()
        val car = state.car ?: return Result.success()
        if (state.schema > 2) return Result.success()

        val prefs = state.reminders
        if (!prefs.enabled) return Result.success()

        val catalog = Catalog.load(ctx)
        val due = rowsFor(state, catalog)
            .filter { it.level != Level.OK }
            .filter { it.part.id !in prefs.mutedParts }
            .filter { !prefs.onlyOverdue || it.level == Level.OVER }

        if (due.isEmpty()) return Result.success()
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return Result.success()

        val channel = NotificationManagerCompat.from(ctx).getNotificationChannel(CHANNEL_ID)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val key = "digest:${car.id}"
        if (now - (state.lastNotified[key] ?: 0L) < 20 * 3_600_000L) return Result.success()

        ensureChannel(ctx)
        val over = due.count { it.level == Level.OVER }
        val title = if (over > 0) "${over.fa()} قطعه از موعد گذشته" else "${due.size.fa()} قطعه نزدیک موعد"
        val body = due.take(3).joinToString(" · ") { "${it.part.name}: ${it.shortText()}" }

        val sent = runCatching { post(ctx, DAILY_NOTIFY_ID, title, body) }.isSuccess
        if (sent) Store(ctx).markNotified(key, now)
        return Result.success()
    }
}

@SuppressLint("MissingPermission")
private fun post(context: Context, id: Int, title: String, body: String) {
    if (!hasNotificationPermission(context)) return
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pending = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_carchi)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()
    NotificationManagerCompat.from(context).notify(id, notification)
}

fun sendTestNotification(context: Context) {
    ensureChannel(context)
    post(
        context,
        TEST_NOTIFY_ID,
        "کارچی آماده است",
        "این یک اعلان آزمایشی است؛ تنظیمات یادآور درست کار می‌کند.",
    )
}

/** اگر کاربر خود کانال «یادآوری سرویس» را خاموش کرده باشد. */
fun isChannelBlocked(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val channel = context.getSystemService(NotificationManager::class.java)
        ?.getNotificationChannel(CHANNEL_ID) ?: return false
    return channel.importance == NotificationManager.IMPORTANCE_NONE
}

fun openChannelSettings(context: Context) {
    runCatching {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        } else {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * زمان‌بندی روزانه. با هر تغییر تنظیمات، [update] را true بده تا ساعت جدید اعمال شود.
 */
fun scheduleReminders(
    context: Context,
    prefs: ReminderPrefs,
    update: Boolean = false,
) {
    val workManager = WorkManager.getInstance(context)
    if (!prefs.enabled) {
        workManager.cancelUniqueWork(WORK_NAME)
        return
    }
    // UPDATE تاریخ اجرای بعدی را بازنشانی نمی‌کند؛ برای تغییر ساعت صریحاً از نو می‌سازیم.
    if (update) workManager.cancelUniqueWork(WORK_NAME)
    ensureChannel(context)
    // فلکس ۱۵ دقیقه‌ای: سیستم می‌تواند برای باتری کمی جابه‌جا کند، نه کل ۲۴ ساعت.
    val request = PeriodicWorkRequestBuilder<ReminderWorker>(
        repeatInterval = 1,
        repeatIntervalTimeUnit = TimeUnit.DAYS,
        flexTimeInterval = 15,
        flexTimeIntervalUnit = TimeUnit.MINUTES,
    )
        .setInitialDelay(msUntil(prefs.hour, prefs.minute), TimeUnit.MILLISECONDS)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .build()

    workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}

private fun msUntil(hour: Int, minute: Int): Long {
    val now = ZonedDateTime.now()
    var next = now.withHour(hour.coerceIn(0, 23))
        .withMinute(minute.coerceIn(0, 59))
        .withSecond(0)
        .withNano(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next).toMillis()
}
