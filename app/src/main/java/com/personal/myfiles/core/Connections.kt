package com.personal.myfiles.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ConnType(val label: String, val defaultPort: Int) {
    SMB("SMB", 445),
    FTP("FTP", 21),
    SFTP("SFTP", 22),
}

data class Connection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ConnType,
    val host: String,
    val port: Int,
    val user: String = "",
    val pass: String = "",
    val share: String = "",
    val path: String = "",
    val domain: String = "",
    val anonymous: Boolean = false,
) {
    /** Path of the root folder inside the remote file system. */
    val rootPath: String
        get() = when (type) {
            ConnType.SMB -> "/"
            else -> path.trim().ifEmpty { "/" }.let { if (it.startsWith("/")) it else "/$it" }
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("type", type.name).put("host", host)
        .put("port", port).put("user", user).put("pass", pass).put("share", share)
        .put("path", path).put("domain", domain).put("anonymous", anonymous)

    companion object {
        fun fromJson(o: JSONObject) = Connection(
            id = o.getString("id"),
            name = o.optString("name"),
            type = runCatching { ConnType.valueOf(o.optString("type")) }.getOrDefault(ConnType.SMB),
            host = o.optString("host"),
            port = o.optInt("port", 445),
            user = o.optString("user"),
            pass = o.optString("pass"),
            share = o.optString("share"),
            path = o.optString("path"),
            domain = o.optString("domain"),
            anonymous = o.optBoolean("anonymous"),
        )
    }
}

class ConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("connections", Context.MODE_PRIVATE)
    private val _list = MutableStateFlow(load())
    val list: StateFlow<List<Connection>> = _list.asStateFlow()

    private fun load(): List<Connection> = runCatching {
        val arr = JSONArray(prefs.getString("list", "[]"))
        (0 until arr.length()).map { Connection.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun save(l: List<Connection>) {
        _list.value = l
        val arr = JSONArray()
        l.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun get(id: String): Connection? = _list.value.firstOrNull { it.id == id }

    fun upsert(c: Connection) {
        val l = _list.value
        save(if (l.any { it.id == c.id }) l.map { if (it.id == c.id) c else it } else l + c)
    }

    fun remove(id: String) = save(_list.value.filterNot { it.id == id })
}
