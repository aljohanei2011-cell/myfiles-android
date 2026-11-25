package com.personal.myfiles.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

object Storage {
    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
    }

    val primaryPath: String get() = Environment.getExternalStorageDirectory().path

    data class Volume(
        val name: String,
        val path: String,
        val total: Long,
        val free: Long,
        val primary: Boolean,
        val removable: Boolean,
    ) {
        val used: Long get() = (total - free).coerceAtLeast(0)
        val isUsb: Boolean get() = removable && name.contains("usb", ignoreCase = true)
    }

    @Volatile
    private var cachedRoots: List<String> = emptyList()

    @SuppressLint("DiscouragedPrivateApi")
    private fun volumePath(v: StorageVolume): String? =
        if (Build.VERSION.SDK_INT >= 30) v.directory?.path
        else runCatching { v.javaClass.getMethod("getPath").invoke(v) as? String }.getOrNull()

    private fun stat(path: String): Pair<Long, Long> =
        runCatching { StatFs(path).let { it.totalBytes to it.availableBytes } }.getOrDefault(0L to 0L)

    fun volumes(): List<Volume> {
        val out = mutableListOf<Volume>()
        runCatching {
            val sm = app.getSystemService(StorageManager::class.java)
            for (v in sm.storageVolumes) {
                val state = v.state
                if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) continue
                val path = volumePath(v) ?: continue
                val (t, f) = stat(path)
                val name = if (v.isPrimary) "الذاكرة الداخلية" else (v.getDescription(app) ?: "ذاكرة خارجية")
                out += Volume(name, path, t, f, v.isPrimary, v.isRemovable)
            }
        }
        if (out.none { it.primary }) {
            val (t, f) = stat(primaryPath)
            out.add(0, Volume("الذاكرة الداخلية", primaryPath, t, f, primary = true, removable = false))
        }
        cachedRoots = out.map { it.path }
        return out.sortedByDescending { it.primary }
    }

    private fun roots(): List<String> = cachedRoots.ifEmpty { volumes().map { it.path } }

    fun isVolumeRoot(path: String): Boolean = roots().any { it.trimEnd('/') == path.trimEnd('/') } || path == "/"

    fun volumeRootOf(path: String): String =
        roots().filter { path == it || path.startsWith("$it/") }.maxByOrNull { it.length } ?: primaryPath

    fun volumeNameOf(path: String): String {
        val root = volumeRootOf(path)
        return volumes().firstOrNull { it.path == root }?.name ?: "التخزين"
    }

    /* ---------------- MediaStore queries ---------------- */

    private val filesUri get() = MediaStore.Files.getContentUri("external")
    private const val DATA = MediaStore.Files.FileColumns.DATA
    private const val SIZE = MediaStore.Files.FileColumns.SIZE
    private const val MOD = MediaStore.Files.FileColumns.DATE_MODIFIED
    private const val MEDIA_TYPE = MediaStore.Files.FileColumns.MEDIA_TYPE
    private const val MIME = MediaStore.Files.FileColumns.MIME_TYPE

    val DOC_QUERY_EXT = FileCategory.DOC_EXT + setOf("pdf", "txt", "md", "csv")

    private fun extSelection(exts: Collection<String>): Pair<String, Array<String>> =
        exts.joinToString(" OR ", "(", ")") { "$DATA LIKE ?" } to exts.map { "%.$it" }.toTypedArray()

    fun selectionFor(key: String): Pair<String?, Array<String>?> = when (key) {
        "images" -> "$MEDIA_TYPE=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}" to null
        "videos" -> "$MEDIA_TYPE=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}" to null
        "audio" -> "$MEDIA_TYPE=${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}" to null
        "documents" -> extSelection(DOC_QUERY_EXT)
        "apks" -> extSelection(listOf("apk"))
        "archives" -> extSelection(FileCategory.ARCHIVE_EXT)
        else -> "$MIME IS NOT NULL" to null
    }

    suspend fun query(key: String, limit: Int = 5000): List<FileEntry> = withContext(Dispatchers.IO) {
        val (sel, args) = selectionFor(key)
        val out = ArrayList<FileEntry>()
        runCatching {
            app.contentResolver.query(filesUri, arrayOf(DATA, SIZE, MOD), sel, args, "$MOD DESC")?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val path = c.getString(0) ?: continue
                    if (path.contains("/.")) continue
                    val f = File(path)
                    if (key == "recent" && !f.isFile) continue
                    out += FileEntry(Location.Local(path), f.name, false, c.getLong(1), c.getLong(2) * 1000L)
                }
            }
        }
        out
    }

    suspend fun count(key: String): Int = withContext(Dispatchers.IO) {
        val (sel, args) = selectionFor(key)
        runCatching {
            app.contentResolver.query(filesUri, arrayOf(MediaStore.Files.FileColumns._ID), sel, args, null)?.use { it.count } ?: 0
        }.getOrDefault(0)
    }

    suspend fun sizeOf(key: String): Long = withContext(Dispatchers.IO) {
        val (sel, args) = selectionFor(key)
        var total = 0L
        runCatching {
            app.contentResolver.query(filesUri, arrayOf(SIZE), sel, args, null)?.use { c ->
                while (c.moveToNext()) total += c.getLong(0)
            }
        }
        total
    }

    /* ---------------- Search ---------------- */

    fun search(root: File, query: String, filter: FileCategory?, showHidden: Boolean): Flow<List<FileEntry>> = flow {
        val q = query.trim().lowercase()
        val results = ArrayList<FileEntry>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        var last = 0L
        while (stack.isNotEmpty() && results.size < 1500) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (!showHidden && f.name.startsWith(".")) continue
                val isDir = f.isDirectory
                if (isDir) {
                    val p = f.path
                    if (!p.endsWith("/Android/data") && !p.endsWith("/Android/obb")) stack.add(f)
                }
                if (f.name.lowercase().contains(q)) {
                    val cat = FileCategory.of(f.name, isDir)
                    if (filter == null || (!isDir && cat.group == filter)) results += f.toEntry()
                }
            }
            val now = System.currentTimeMillis()
            if (now - last > 300) {
                emit(results.toList())
                last = now
            }
        }
        emit(results.toList())
    }.flowOn(Dispatchers.IO)

    fun quickFolders(): List<Pair<String, String>> {
        val root = primaryPath
        return listOf(
            "التنزيلات" to "$root/Download",
            "الكاميرا" to "$root/DCIM",
            "الصور" to "$root/Pictures",
            "المستندات" to "$root/Documents",
            "الأفلام" to "$root/Movies",
            "الموسيقى" to "$root/Music",
        ).filter { File(it.second).exists() }
    }
}
