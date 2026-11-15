package com.banbiao.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A user-defined shift. There is intentionally no per-shift colour: the app uses
 * one accent plus grayscale, so shift identity comes from its name and time.
 */
data class ShiftDef(
    val id: String,
    val name: String,
    val start: String = "",
    val end: String = "",
    val rest: Boolean = false,
    /** 0..2: three grayscale levels for work shifts. Rest uses its own light tone. */
    val tone: Int = 0,
) {
    val short: String
        get() = if (rest) "休" else name.trim().firstOrNull()?.toString() ?: "班"

    val timeLabel: String
        get() = if (rest) "全天" else "$start – $end"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("start", if (rest) "" else start)
        .put("end", if (rest) "" else end)
        .put("rest", rest)
        .put("tone", tone.coerceIn(0, 2))

    companion object {
        private val TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

        fun defaultTone(id: String): Int = when (id) {
            "morning" -> 0
            "middle" -> 1
            "night" -> 2
            else -> 0
        }

        fun fromJson(obj: JSONObject): ShiftDef? {
            val id = obj.optString("id").trim()
            val name = obj.optString("name").trim()
            if (id.isBlank() || name.isBlank()) return null
            val rest = obj.optBoolean("rest", false)
            if (rest) return ShiftDef(id, name.take(16), rest = true)
            val start = obj.optString("start")
            val end = obj.optString("end")
            if (!TIME.matches(start) || !TIME.matches(end) || start == end) return null
            return ShiftDef(
                id = id,
                name = name.take(16),
                start = start,
                end = end,
                rest = false,
                tone = obj.optInt("tone", defaultTone(id)).coerceIn(0, 2),
            )
        }
    }
}

enum class ThemeMode(val storage: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色");

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storage == value } ?: System
    }
}

data class CycleSegment(val shiftId: String, val days: Int) {
    fun toJson(): JSONObject = JSONObject().put("shift", shiftId).put("days", days)
}

data class AutoRule(
    val segments: List<CycleSegment>,
    val anchorKey: String,
    val anchorIndex: Int,
) {
    val totalDays: Int get() = segments.sumOf { it.days }

    fun toJson(): JSONObject = JSONObject()
        .put("segments", JSONArray().apply { segments.forEach { put(it.toJson()) } })
        .put("anchorKey", anchorKey)
        .put("anchorIndex", anchorIndex)

    companion object {
        private val KEY = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        fun fromJson(obj: JSONObject?, validShiftIds: Set<String>): AutoRule? {
            if (obj == null) return null
            val array = obj.optJSONArray("segments") ?: return null
            val segments = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("shift")
                    val days = item.optInt("days", 0)
                    if (id in validShiftIds && days in 1..60) add(CycleSegment(id, days))
                }
            }
            if (segments.isEmpty()) return null
            val anchorKey = obj.optString("anchorKey")
            if (!KEY.matches(anchorKey)) return null
            val total = segments.sumOf { it.days }
            val anchor = obj.optInt("anchorIndex", 0).coerceIn(0, (total - 1).coerceAtLeast(0))
            return AutoRule(segments, anchorKey, anchor)
        }
    }
}

data class AppState(
    /** date (yyyy-MM-dd) -> shift definition id */
    val assignments: Map<String, String> = emptyMap(),
    val shiftTypes: List<ShiftDef> = defaultShiftTypes(),
    val rule: AutoRule? = null,
    val autoEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
    val islandEnabled: Boolean = false,
    /** Date keys marked as personal leave; auto rotation pauses across them. */
    val leaves: Set<String> = emptySet(),
    /** Month key (yyyy-MM) -> salary in cents. Kept local only. */
    val salaries: Map<String, Long> = emptyMap(),
) {
    fun shift(id: String?): ShiftDef? = shiftTypes.firstOrNull { it.id == id }

    fun isLeave(key: String): Boolean = key in leaves

    fun salaryOf(key: String): Long = salaries[key] ?: 0L

    fun salaryYear(year: Int): Long =
        salaries.entries.filter { it.key.startsWith(salaryPrefix(year)) }.sumOf { it.value }

    fun salaryMonths(year: Int): List<Pair<Int, Long>> {
        val prefix = salaryPrefix(year)
        return salaries.entries
            .mapNotNull { (key, cents) ->
                if (!key.startsWith(prefix)) return@mapNotNull null
                val month = key.substringAfter('-').toIntOrNull() ?: return@mapNotNull null
                if (month !in 1..12) null else month to cents
            }
            .sortedBy { it.first }
    }

    fun totalSalary(): Long = salaries.values.sumOf { it }

    fun withSalary(key: String, cents: Long): AppState =
        copy(salaries = if (cents <= 0L) salaries - key else salaries + (key to cents))

    fun toggleLeave(key: String): AppState {
        return if (key in leaves) {
            copy(leaves = leaves - key)
        } else {
            copy(
                leaves = leaves + key,
                assignments = assignments - key,
            )
        }
    }

    fun nextTone(): Int {
        val used = shiftTypes.filterNot { it.rest }.map { it.tone }.toSet()
        return (0..2).firstOrNull { it !in used } ?: (shiftTypes.count { !it.rest } % 3)
    }

    fun withShift(def: ShiftDef): AppState {
        val index = shiftTypes.indexOfFirst { it.id == def.id }
        val next = if (index >= 0) {
            shiftTypes.toMutableList().also { it[index] = def }
        } else {
            shiftTypes + def
        }
        return copy(shiftTypes = next)
    }

    fun removeShift(id: String): AppState {
        val remaining = shiftTypes.filterNot { it.id == id }
        val nextAssignments = assignments.filterValues { it != id }
        val nextRule = rule?.let { old ->
            val segments = old.segments.filterNot { it.shiftId == id }
            if (segments.isEmpty()) {
                null
            } else {
                val total = segments.sumOf { it.days }
                old.copy(
                    segments = segments,
                    anchorIndex = old.anchorIndex.coerceIn(0, (total - 1).coerceAtLeast(0)),
                )
            }
        }
        return copy(assignments = nextAssignments, shiftTypes = remaining, rule = nextRule)
    }

    fun toJson(): JSONObject {
        val shiftJson = JSONObject()
        assignments.forEach { (key, shiftId) -> shiftJson.put(key, shiftId) }
        return JSONObject()
            .put("version", 6)
            .put("shifts", shiftJson)
            .put("shiftTypes", JSONArray().apply { shiftTypes.forEach { put(it.toJson()) } })
            .put("rule", rule?.toJson() ?: JSONObject.NULL)
            .put("autoEnabled", autoEnabled)
            .put("islandEnabled", islandEnabled)
            .put("theme", themeMode.storage)
            .put("leaves", JSONArray().apply { leaves.sorted().forEach { put(it) } })
            .put("salaries", JSONObject().apply {
                salaries.toSortedMap().forEach { (key, cents) -> put(key, cents) }
            })
    }

    companion object {
        private val KEY = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val SALARY_KEY = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

        private fun salaryPrefix(year: Int): String =
            year.toString().padStart(4, '0') + "-"

        fun salaryKey(year: Int, month: Int): String =
            salaryPrefix(year) + month.toString().padStart(2, '0')

        fun newShiftId(): String = "s_" + UUID.randomUUID().toString().replace("-", "").take(10)

        fun defaultShiftTypes(): List<ShiftDef> = listOf(
            ShiftDef("morning", "白班", "08:00", "16:00", tone = 0),
            ShiftDef("middle", "中班", "12:00", "20:00", tone = 1),
            ShiftDef("night", "夜班", "20:00", "08:00", tone = 2),
            ShiftDef("rest", "休息", rest = true, tone = 0),
        )

        fun defaultSegments(defs: List<ShiftDef>): List<CycleSegment> {
            if (defs.isEmpty()) return emptyList()
            val work = defs.filterNot { it.rest }
            return buildList {
                if (work.isNotEmpty()) add(CycleSegment(work.first().id, 4))
                add(CycleSegment(defs.first().id, 2))
                if (work.size > 1) add(CycleSegment(work[1].id, 4))
                add(CycleSegment(defs.first().id, 2))
            }
        }

        private fun legacyTypes(obj: JSONObject): List<ShiftDef> {
            val times = obj.optJSONObject("shiftTimes")
            return defaultShiftTypes().map { def ->
                if (def.rest) return@map def
                val time = times?.optJSONObject(def.id) ?: return@map def
                val start = time.optString("start")
                val end = time.optString("end")
                if (ShiftDef.fromJson(JSONObject().put("id", def.id).put("name", def.name)
                        .put("start", start).put("end", end)) != null
                ) def.copy(start = start, end = end) else def
            }
        }

        fun fromJson(raw: String?): AppState {
            if (raw.isNullOrBlank()) return AppState()
            return runCatching {
                val obj = JSONObject(raw)
                val typesArray = obj.optJSONArray("shiftTypes")
                val types: List<ShiftDef> = if (typesArray != null) {
                    buildList {
                        for (i in 0 until typesArray.length()) {
                            typesArray.optJSONObject(i)?.let { ShiftDef.fromJson(it)?.let(::add) }
                        }
                    }
                } else {
                    legacyTypes(obj)
                }
                val ids = types.mapTo(mutableSetOf()) { it.id }
                val assignments = buildMap {
                    obj.optJSONObject("shifts")?.let { map ->
                        map.keys().forEach { key ->
                            val id = map.optString(key)
                            if (id in ids) put(key, id)
                        }
                    }
                }
                val leaves = buildSet {
                    val array = obj.optJSONArray("leaves") ?: JSONArray()
                    for (i in 0 until array.length()) {
                        val key = array.optString(i)
                        if (KEY.matches(key)) add(key)
                    }
                }
                val salaries = buildMap {
                    obj.optJSONObject("salaries")?.let { map ->
                        map.keys().forEach { key ->
                            val cents = map.optLong(key, 0L)
                            if (SALARY_KEY.matches(key) && cents > 0L) put(key, cents)
                        }
                    }
                }
                AppState(
                    assignments = assignments,
                    shiftTypes = types,
                    rule = AutoRule.fromJson(obj.optJSONObject("rule"), ids),
                    autoEnabled = obj.optBoolean("autoEnabled", true),
                    islandEnabled = obj.optBoolean("islandEnabled", false),
                    themeMode = ThemeMode.fromStorage(obj.optString("theme")),
                    leaves = leaves,
                    salaries = salaries,
                )
            }.getOrElse { AppState() }
        }
    }
}
