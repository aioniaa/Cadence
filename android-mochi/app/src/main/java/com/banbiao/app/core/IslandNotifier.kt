package com.banbiao.app.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.banbiao.app.MainActivity
import com.banbiao.app.R
import com.banbiao.app.data.AppState
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Xiaomi HyperOS 3 Super Island client integration.
 *
 * Xiaomi's official route is an ongoing notification carrying the
 * "miui.focus.param" JSON payload plus a focus-notification permission.
 * The system decides whether the payload is promoted onto the island.
 * On non-HyperOS devices this falls back to the normal ongoing notification.
 *
 * Reference: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131
 */
object IslandNotifier {
    private const val CHANNEL_ID = "cadence_island"
    private const val NOTIFICATION_ID = 4201
    private const val ISLAND_PIC_KEY = "miui.focus.pic_cadence"

    fun sync(context: Context, state: AppState) {
        if (!state.islandEnabled) {
            cancel(context)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            cancel(context)
            return
        }
        val now = LocalTime.now()
        val countdown = NativeCore.countdown(state, LocalDate.now(), now)
        if (countdown.kind != "active" || countdown.seconds <= 0) {
            cancel(context)
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "超级岛下班倒计时",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "在超级岛或实时通知里显示距离下班的时间"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nowMs = System.currentTimeMillis()
        val endAt = nowMs + countdown.seconds * 1000L
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cadence)
            .setContentTitle("距离下班")
            .setContentText("还有 ${format(countdown.seconds)}")
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(countdown.seconds * 1000L + 5_000L)
            .setColor(0xFFB56576.toInt())

        if (Build.VERSION.SDK_INT >= 36) {
            val elapsed = ((1f - countdown.progress) * 100f).toInt().coerceIn(0, 100)
            builder.setStyle(
                Notification.ProgressStyle()
                    .setProgress(elapsed)
                    .setProgressIndeterminate(false),
            )
            builder.setShortCriticalText(format(countdown.seconds))
        }

        val notification = builder.build()
        if (isHyperOsIsland(context)) {
            notification.extras.putString(
                "miui.focus.param",
                buildIslandJson(context, countdown.seconds, countdown.progress, nowMs),
            )
            notification.extras.putBundle(
                "miui.focus.pics",
                Bundle().apply {
                    putParcelable(
                        ISLAND_PIC_KEY,
                        Icon.createWithResource(context, R.drawable.ic_stat_cadence),
                    )
                },
            )
        }
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    private fun format(seconds: Int): String {
        val total = seconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
    }

    private fun isHyperOsIsland(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        if (!manufacturer.contains("xiaomi") && !manufacturer.contains("redmi") && !manufacturer.contains("poco")) {
            return false
        }
        // HyperOS 4 still speaks the OS3 focus protocol on current builds.
        // Do not require the per-app focus grant here: if it is not granted,
        // the system simply falls back to the normal notification.
        val protocol = runCatching {
            Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
        }.getOrDefault(0)
        if (protocol >= 3) return true
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
            method.invoke(null, "persist.sys.feature.island", false) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun buildIslandJson(
        context: Context,
        seconds: Int,
        progress: Float,
        nowMs: Long,
    ): String {
        val totalSeconds = if (progress > 0.001f) {
            (seconds / progress).toInt().coerceAtLeast(seconds)
        } else {
            seconds
        }
        val timerInfo = JSONObject()
            .put("timerType", -1)
            .put("timerWhen", nowMs + seconds * 1000L)
            .put("timerTotal", totalSeconds * 1000L)
            .put("timerSystemCurrent", nowMs)
        val digitInfo = JSONObject()
            .put("digit", format(seconds))
            .put("content", "距离下班")
            .put("timerInfo", timerInfo)
            .put("showHighlightColor", true)
            .put("turnAnim", false)
        val picInfo = JSONObject()
            .put("type", 1)
            .put("pic", ISLAND_PIC_KEY)
        val bigIsland = JSONObject()
            .put(
                "imageTextInfoLeft",
                JSONObject()
                    .put("type", 1)
                    .put("picInfo", picInfo)
                    .put("textInfo", JSONObject().put("title", "距离下班").put("content", "Cadence")),
            )
            .put("sameWidthDigitInfo", digitInfo)
        val smallIsland = JSONObject()
            .put("picInfo", picInfo)
        val paramIsland = JSONObject()
            .put("islandProperty", 1)
            .put("islandPriority", 2)
            .put("islandTimeout", seconds + 10)
            .put("highlightColor", "#B56576")
            .put("bigIslandArea", bigIsland)
            .put("smallIslandArea", smallIsland)
        val paramV2 = JSONObject()
            .put("protocol", 1)
            .put("business", "cadence")
            .put("enableFloat", true)
            .put("islandFirstFloat", true)
            .put("updatable", true)
            .put("ticker", "距离下班 ${format(seconds)}")
            .put("aodTitle", "距离下班 ${format(seconds)}")
            .put("param_island", paramIsland)
            .put(
                "baseInfo",
                JSONObject()
                    .put("title", "距离下班")
                    .put("content", "还有 ${format(seconds)}")
                    .put("type", 2),
            )
        return JSONObject()
            .put("param_v2", paramV2)
            .toString()
    }
}
