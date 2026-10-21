package app.carchi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color

fun Level.color(): Color = when (this) {
    Level.OVER -> OverColor
    Level.NEAR -> NearColor
    Level.OK -> OkColor
}

fun timeText(hour: Int, minute: Int): String =
    "${hour.fa()}:${minute.toString().padStart(2, '0').persianDigits()}"

fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun openManual(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "مرورگر باز نشد", Toast.LENGTH_SHORT).show()
    }
}
