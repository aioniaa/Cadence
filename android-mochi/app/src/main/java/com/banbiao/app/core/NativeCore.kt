package com.banbiao.app.core

import android.util.Log
import com.banbiao.app.data.AppState
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

data class MonthCell(
    val key: String,
    val day: Int,
    val inMonth: Boolean,
    val shiftId: String?,
    val source: String?,
    val leave: Boolean = false,
)

data class Countdown(
    val kind: String,
    val shiftId: String?,
    val label: String,
    val seconds: Int,
    val progress: Float,
    val meta: String,
    val source: String?,
    val leave: Boolean = false,
)

object NativeCore {
    private const val TAG = "NativeCore"
    private var loaded = false

    init {
        loaded = try {
            System.loadLibrary("banbiao_core")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Rust core unavailable", t)
            false
        }
    }

    private external fun projectMonth(stateJson: String, year: Int, month: Int): String
    private external fun projectDays(stateJson: String, startKey: String, count: Int): String
    private external fun countdown(stateJson: String, todayKey: String, nowMinutes: Int): String

    fun month(state: AppState, year: Int, month: Int): List<MonthCell> {
        if (!loaded) return emptyList()
        return runCatching {
            val root = JSONObject(projectMonth(state.toJson().toString(), year, month))
            val days = root.optJSONArray("days") ?: return emptyList()
            buildList {
                for (i in 0 until days.length()) {
                    val obj = days.optJSONObject(i) ?: continue
                    add(
                        MonthCell(
                            key = obj.optString("key"),
                            day = obj.optInt("day"),
                            inMonth = obj.optBoolean("inMonth"),
                            shiftId = obj.optString("shift").takeIf { it.isNotBlank() && it != "null" },
                            source = obj.optString("source").takeIf { it.isNotBlank() && it != "null" },
                            leave = obj.optBoolean("leave", false),
                        ),
                    )
                }
            }
        }.getOrElse {
            Log.e(TAG, "month failed", it)
            emptyList()
        }
    }

    fun days(state: AppState, startKey: String, count: Int): Map<String, MonthCell> {
        if (!loaded) return emptyMap()
        return runCatching {
            val array = org.json.JSONArray(projectDays(state.toJson().toString(), startKey, count))
            buildMap {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    put(
                        obj.optString("key"),
                        MonthCell(
                            key = obj.optString("key"),
                            day = 0,
                            inMonth = true,
                            shiftId = obj.optString("shift").takeIf { it.isNotBlank() && it != "null" },
                            source = obj.optString("source").takeIf { it.isNotBlank() && it != "null" },
                            leave = obj.optBoolean("leave", false),
                        ),
                    )
                }
            }
        }.getOrElse { emptyMap() }
    }

    fun countdown(state: AppState, now: LocalDate = LocalDate.now(), time: LocalTime = LocalTime.now()): Countdown {
        val fallback = Countdown("none", null, "今天还没有排班", 0, 0f, "去排班", null)
        if (!loaded) return fallback
        return runCatching {
            val obj = JSONObject(
                countdown(
                    state.toJson().toString(),
                    now.toString(),
                    time.toSecondOfDay(),
                ),
            )
            Countdown(
                kind = obj.optString("kind", "none"),
                shiftId = obj.optString("shift").takeIf { it.isNotBlank() && it != "null" },
                label = obj.optString("label", "今天还没有排班"),
                seconds = obj.optInt("seconds", 0),
                progress = obj.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                meta = obj.optString("meta", ""),
                source = obj.optString("source").takeIf { it.isNotBlank() && it != "null" },
                leave = obj.optBoolean("leave", false),
            )
        }.getOrElse {
            Log.e(TAG, "countdown failed", it)
            fallback
        }
    }
}
