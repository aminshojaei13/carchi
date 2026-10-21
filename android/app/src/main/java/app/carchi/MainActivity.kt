package app.carchi

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // اپ همیشه روشن است؛ آیکون‌های نوار وضعیت باید تیره باشند،
        // حتی اگر گوشی در حالت تاریک باشد.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        scheduleReminders(applicationContext, Store(applicationContext).load().reminders)
        // فقط برای تست دستی روی دستگاه: adb ... --ez run_reminder_now true
        if (intent?.getBooleanExtra("run_reminder_now", false) == true) {
            WorkManager.getInstance(applicationContext)
                .enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
        }
        setContent { CarCareApp() }
    }
}
