package com.banbiao.app.data

import android.content.Context

class StateStore(context: Context) {
    private val prefs = context.getSharedPreferences("banbiao.native", Context.MODE_PRIVATE)

    fun load(): AppState = AppState.fromJson(prefs.getString(KEY_STATE, null))

    fun save(state: AppState) {
        prefs.edit().putString(KEY_STATE, state.toJson().toString()).apply()
    }

    fun isMigrated(): Boolean = prefs.getBoolean(KEY_MIGRATED, false)

    fun markMigrated() {
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    fun importLegacy(raw: String?, theme: String?): AppState {
        val old = AppState.fromJson(raw)
        val state = old.copy(themeMode = ThemeMode.fromStorage(theme))
        save(state)
        markMigrated()
        return state
    }

    companion object {
        private const val KEY_STATE = "state"
        private const val KEY_MIGRATED = "migrated_v1"
    }
}
