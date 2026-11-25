package com.personal.myfiles.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppSettings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun int(key: String, def: Int) = mutableIntStateOf(p.getInt(key, def))
    private fun bool(key: String, def: Boolean) = mutableStateOf(p.getBoolean(key, def))

    private val _theme = int("theme", 0)
    private val _accent = int("accent", 0)
    private val _dynamic = bool("dynamic", false)
    private val _amoled = bool("amoled", false)
    private val _hidden = bool("hidden", false)
    private val _grid = bool("grid", false)
    private val _sortBy = int("sortBy", 0)
    private val _sortDesc = bool("sortDesc", false)
    private val _foldersFirst = bool("foldersFirst", true)
    private val _useTrash = bool("useTrash", true)
    private val _trashDays = int("trashDays", 30)
    private val _confirmDelete = bool("confirmDelete", true)
    private var _favorites by mutableStateOf(p.getStringSet("favorites", emptySet())!!.toSet())

    /** 0 = system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = _theme.intValue
        set(v) { _theme.intValue = v; p.edit().putInt("theme", v).apply() }
    var accent: Int
        get() = _accent.intValue
        set(v) { _accent.intValue = v; p.edit().putInt("accent", v).apply() }
    var dynamicColor: Boolean
        get() = _dynamic.value
        set(v) { _dynamic.value = v; p.edit().putBoolean("dynamic", v).apply() }
    var amoled: Boolean
        get() = _amoled.value
        set(v) { _amoled.value = v; p.edit().putBoolean("amoled", v).apply() }
    var showHidden: Boolean
        get() = _hidden.value
        set(v) { _hidden.value = v; p.edit().putBoolean("hidden", v).apply() }
    var gridView: Boolean
        get() = _grid.value
        set(v) { _grid.value = v; p.edit().putBoolean("grid", v).apply() }
    /** 0 name, 1 date, 2 size, 3 type */
    var sortBy: Int
        get() = _sortBy.intValue
        set(v) { _sortBy.intValue = v; p.edit().putInt("sortBy", v).apply() }
    var sortDesc: Boolean
        get() = _sortDesc.value
        set(v) { _sortDesc.value = v; p.edit().putBoolean("sortDesc", v).apply() }
    var foldersFirst: Boolean
        get() = _foldersFirst.value
        set(v) { _foldersFirst.value = v; p.edit().putBoolean("foldersFirst", v).apply() }
    var useTrash: Boolean
        get() = _useTrash.value
        set(v) { _useTrash.value = v; p.edit().putBoolean("useTrash", v).apply() }
    var trashDays: Int
        get() = _trashDays.intValue
        set(v) { _trashDays.intValue = v; p.edit().putInt("trashDays", v).apply() }
    var confirmDelete: Boolean
        get() = _confirmDelete.value
        set(v) { _confirmDelete.value = v; p.edit().putBoolean("confirmDelete", v).apply() }

    val favorites: Set<String> get() = _favorites

    fun isFavorite(path: String) = path in _favorites

    fun toggleFavorite(path: String) {
        _favorites = if (path in _favorites) _favorites - path else _favorites + path
        p.edit().putStringSet("favorites", _favorites).apply()
    }
}
