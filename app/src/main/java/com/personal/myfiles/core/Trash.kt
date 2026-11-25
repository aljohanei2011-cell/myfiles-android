package com.personal.myfiles.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class TrashManager(context: Context) {
    data class Item(
        val id: String,
        val name: String,
        val original: String,
        val trashPath: String,
        val deletedAt: Long,
        val isDir: Boolean,
        val size: Long,
    )

    private val indexFile = File(context.filesDir, "trash.json")
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private fun load(): List<Item> = runCatching {
        if (!indexFile.exists()) return@runCatching emptyList<Item>()
        val arr = JSONArray(indexFile.readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Item(o.getString("id"), o.getString("name"), o.getString("original"), o.getString("trash"),
                o.getLong("at"), o.optBoolean("dir"), o.optLong("size"))
        }.filter { File(it.trashPath).exists() }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(list: List<Item>) {
        _items.value = list
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("original", it.original)
                .put("trash", it.trashPath).put("at", it.deletedAt).put("dir", it.isDir).put("size", it.size))
        }
        runCatching { indexFile.writeText(arr.toString()) }
    }

    @Synchronized
    fun moveToTrash(f: File) {
        val root = Storage.volumeRootOf(f.path)
        val dir = File(root, ".MyFilesTrash")
        dir.mkdirs()
        runCatching { File(dir, ".nomedia").createNewFile() }
        val id = "${System.currentTimeMillis()}_${(1000..9999).random()}"
        val dest = File(dir, id)
        val size = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
        val isDir = f.isDirectory
        if (!f.renameTo(dest)) throw IOException("تعذّر نقل ${f.name} إلى المهملات")
        save(_items.value + Item(id, f.name, f.path, dest.path, System.currentTimeMillis(), isDir, size))
    }

    @Synchronized
    fun restore(item: Item): String {
        var target = File(item.original)
        target.parentFile?.mkdirs()
        var n = 1
        while (target.exists()) {
            val base = item.name.substringBeforeLast('.', item.name)
            val ext = item.name.substringAfterLast('.', "")
            target = File(File(item.original).parentFile, if (ext.isEmpty() || item.isDir) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        if (!File(item.trashPath).renameTo(target)) throw IOException("تعذّرت الاستعادة")
        save(_items.value.filterNot { it.id == item.id })
        return target.path
    }

    @Synchronized
    fun deleteForever(item: Item) {
        File(item.trashPath).deleteRecursively()
        save(_items.value.filterNot { it.id == item.id })
    }

    @Synchronized
    fun empty() {
        _items.value.forEach { File(it.trashPath).deleteRecursively() }
        save(emptyList())
    }

    fun purge(days: Int) {
        val limit = System.currentTimeMillis() - days * 24L * 3600L * 1000L
        _items.value.filter { it.deletedAt < limit }.forEach { deleteForever(it) }
    }
}
