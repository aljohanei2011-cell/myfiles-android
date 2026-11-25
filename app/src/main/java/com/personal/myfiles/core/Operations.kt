package com.personal.myfiles.core

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class Op(
    val id: Long,
    val title: String,
    val done: Long = 0,
    val total: Long = 0,
    val current: String = "",
    val finished: Boolean = false,
    val error: String? = null,
) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

class OperationManager(
    private val ctx: Context,
    private val reg: FsRegistry,
    private val trash: TrashManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ops = MutableStateFlow<List<Op>>(emptyList())
    val ops: StateFlow<List<Op>> = _ops.asStateFlow()
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val ids = AtomicLong(1)

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    private fun update(id: Long, f: (Op) -> Op) = _ops.update { l -> l.map { if (it.id == id) f(it) else it } }

    inner class Reporter(private val id: Long) {
        var done = 0L
            private set
        private var last = 0L
        val scanned = mutableListOf<String>()

        fun total(t: Long) = update(id) { it.copy(total = t) }

        fun add(bytes: Long, name: String) {
            done += bytes
            val now = SystemClock.uptimeMillis()
            if (now - last > 200) {
                last = now
                val d = done
                update(id) { it.copy(done = d, current = name) }
            }
        }
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(ctx, Intent(ctx, OperationService::class.java))
        }
    }

    private fun launchOp(title: String, block: suspend (Reporter) -> String?): Long {
        val id = ids.getAndIncrement()
        _ops.update { it + Op(id, title) }
        startService()
        val job = scope.launch {
            val r = Reporter(id)
            try {
                val msg = block(r)
                update(id) { it.copy(finished = true, done = maxOf(it.done, it.total)) }
                msg?.let { messages.tryEmit(it) }
            } catch (e: CancellationException) {
                update(id) { it.copy(finished = true, error = "أُلغيت") }
                messages.tryEmit("أُلغيت العملية")
            } catch (e: Throwable) {
                update(id) { it.copy(finished = true, error = e.message ?: e.javaClass.simpleName) }
                messages.tryEmit("تعذّر: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                jobs.remove(id)
                if (r.scanned.isNotEmpty()) runCatching {
                    MediaScannerConnection.scanFile(ctx, r.scanned.take(500).toTypedArray(), null, null)
                }
                changes.tryEmit(Unit)
                scope.launch {
                    delay(3500)
                    _ops.update { l -> l.filterNot { it.id == id } }
                }
            }
        }
        jobs[id] = job
        return id
    }

    fun cancel(id: Long) {
        jobs[id]?.cancel()
    }

    /* ---------------- helpers ---------------- */

    private suspend fun totalSize(fs: VirtualFs, e: FileEntry): Long {
        val f = e.file
        if (f != null) return if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
        if (!e.isDir) return e.size
        return fs.list(e.location.path).sumOf { totalSize(fs, it) }
    }

    private suspend fun uniqueTarget(fs: VirtualFs, dir: String, name: String): String {
        var target = joinPath(dir, name)
        if (!fs.exists(target)) return target
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var n = 1
        while (true) {
            val candidate = if (ext.isEmpty() || base.isEmpty()) "$name ($n)" else "$base ($n).$ext"
            target = joinPath(dir, candidate)
            if (!fs.exists(target)) return target
            n++
        }
    }

    private suspend fun copyStream(sfs: VirtualFs, sp: String, dfs: VirtualFs, dp: String, name: String, r: Reporter) {
        try {
            sfs.openInput(sp).use { input ->
                dfs.openOutput(dp).use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        r.add(n.toLong(), name)
                    }
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { runCatching { dfs.delete(dp) } }
            throw e
        }
    }

    private suspend fun copyEntry(sfs: VirtualFs, src: FileEntry, dfs: VirtualFs, dp: String, r: Reporter) {
        currentCoroutineContext().ensureActive()
        if (src.isDir) {
            dfs.mkdir(dp)
            for (ch in sfs.list(src.location.path)) copyEntry(sfs, ch, dfs, joinPath(dp, ch.name), r)
        } else {
            copyStream(sfs, src.location.path, dfs, dp, src.name, r)
            if (src.isLocal && dfs is LocalFs) runCatching { File(dp).setLastModified(src.modified) }
        }
    }

    /* ---------------- public operations ---------------- */

    fun copy(sources: List<FileEntry>, dest: Location, move: Boolean): Long {
        val title = (if (move) "نقل " else "نسخ ") + if (sources.size == 1) sources[0].name else "${sources.size} عناصر"
        return launchOp(title) { r ->
            val dfs = reg.fs(dest)
            for (s in sources) {
                val sl = s.location
                if (sl is Location.Local && dest is Location.Local &&
                    (dest.path == sl.path || dest.path.startsWith(sl.path + "/"))
                ) throw IOException("لا يمكن نسخ مجلد داخل نفسه")
            }
            var total = 0L
            for (s in sources) total += totalSize(reg.fs(s.location), s)
            r.total(total)
            for (s in sources) {
                currentCoroutineContext().ensureActive()
                val sfs = reg.fs(s.location)
                val sl = s.location
                if (move && sl is Location.Local && dest is Location.Local && parentPath(sl.path) == dest.path) continue
                val target = uniqueTarget(dfs, dest.path, s.name)
                if (move && sl is Location.Local && dest is Location.Local &&
                    Storage.volumeRootOf(sl.path) == Storage.volumeRootOf(dest.path) &&
                    File(sl.path).renameTo(File(target))
                ) {
                    r.add(totalSize(dfs, s.copy(location = Location.Local(target))), s.name)
                    r.scanned += sl.path
                    r.scanned += target
                    continue
                }
                copyEntry(sfs, s, dfs, target, r)
                if (dest is Location.Local) r.scanned += target
                if (move) {
                    sfs.delete(sl.path)
                    if (sl is Location.Local) r.scanned += sl.path
                }
            }
            if (move) "تم النقل" else "تم النسخ"
        }
    }

    fun delete(entries: List<FileEntry>, toTrash: Boolean): Long {
        val title = "حذف " + if (entries.size == 1) entries[0].name else "${entries.size} عناصر"
        return launchOp(title) { r ->
            r.total(entries.size.toLong())
            for (e in entries) {
                currentCoroutineContext().ensureActive()
                val f = e.file
                if (toTrash && f != null) trash.moveToTrash(f) else reg.fs(e.location).delete(e.location.path)
                if (f != null) r.scanned += f.path
                r.add(1, e.name)
            }
            if (toTrash) "نُقلت إلى المهملات" else "تم الحذف نهائيًا"
        }
    }

    fun compress(entries: List<FileEntry>, destDir: String, zipName: String): Long =
        launchOp("ضغط $zipName") { r ->
            val files = entries.mapNotNull { it.file }
            var target = File(destDir, if (zipName.endsWith(".zip", true)) zipName else "$zipName.zip")
            var n = 1
            while (target.exists()) {
                target = File(destDir, "${zipName.removeSuffix(".zip")} ($n).zip"); n++
            }
            r.total(files.sumOf { f -> if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length() })
            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zos ->
                    val buf = ByteArray(128 * 1024)
                    for (root in files) {
                        val base = root.parentFile ?: root
                        for (f in root.walkTopDown()) {
                            currentCoroutineContext().ensureActive()
                            val rel = f.relativeTo(base).path.replace('\\', '/')
                            if (f.isDirectory) {
                                zos.putNextEntry(ZipEntry("$rel/")); zos.closeEntry()
                            } else {
                                val ze = ZipEntry(rel); ze.time = f.lastModified()
                                zos.putNextEntry(ze)
                                BufferedInputStream(f.inputStream()).use { inp ->
                                    while (true) {
                                        val k = inp.read(buf)
                                        if (k < 0) break
                                        zos.write(buf, 0, k)
                                        r.add(k.toLong(), f.name)
                                    }
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                withContext(NonCancellable) { target.delete() }
                throw e
            }
            r.scanned += target.path
            "تم إنشاء ${target.name}"
        }

    private fun openZip(file: File): ZipFile {
        val zf = ZipFile(file, Charsets.UTF_8)
        return try {
            zf.entries().toList().size
            zf
        } catch (e: Exception) {
            runCatching { zf.close() }
            ZipFile(file, Charset.forName("IBM437"))
        }
    }

    fun extract(zip: File, destDir: File): Long = launchOp("فك ضغط ${zip.name}") { r ->
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath
        openZip(zip).use { zf ->
            val entries = zf.entries().toList()
            r.total(entries.sumOf { maxOf(0L, it.size) })
            val buf = ByteArray(128 * 1024)
            for (ze in entries) {
                currentCoroutineContext().ensureActive()
                val out = File(destDir, ze.name)
                if (!out.canonicalPath.startsWith(canonicalDest)) continue
                if (ze.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                zf.getInputStream(ze).use { inp ->
                    BufferedOutputStream(FileOutputStream(out)).use { os ->
                        while (true) {
                            val k = inp.read(buf)
                            if (k < 0) break
                            os.write(buf, 0, k)
                            r.add(k.toLong(), out.name)
                        }
                    }
                }
                if (ze.time > 0) out.setLastModified(ze.time)
            }
        }
        r.scanned += destDir.walkTopDown().take(500).map { it.path }.toList()
        "تم فك الضغط إلى ${destDir.name}"
    }

    /** Download a remote file to the cache so it can be opened locally. */
    fun fetch(entry: FileEntry, onDone: (File) -> Unit): Long = launchOp("تنزيل ${entry.name}") { r ->
        val dir = File(ctx.cacheDir, "remote").apply { mkdirs() }
        val out = File(dir, entry.name)
        r.total(entry.size)
        copyStream(reg.fs(entry.location), entry.location.path, reg.local, out.path, entry.name, r)
        withContext(Dispatchers.Main) { onDone(out) }
        null
    }
}

class AppContainer(context: Context) {
    val settings = AppSettings(context)
    val connections = ConnectionStore(context)
    val registry = FsRegistry(connections)
    val trash = TrashManager(context)
    val ops = OperationManager(context, registry, trash)
}
