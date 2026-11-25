package com.personal.myfiles.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.PriorityQueue

object Analyzer {
    data class Result(
        val totalBytes: Long,
        val fileCount: Int,
        val byCategory: Map<FileCategory, Long>,
        val largest: List<FileEntry>,
        val duplicates: List<List<FileEntry>>,
    )

    suspend fun scan(root: File, onProgress: (Int) -> Unit): Result = withContext(Dispatchers.IO) {
        val byCat = HashMap<FileCategory, Long>()
        val heap = PriorityQueue<FileEntry>(compareBy { it.size })
        val bySize = HashMap<Long, MutableList<FileEntry>>()
        var total = 0L
        var count = 0
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (f.isDirectory) {
                    val p = f.path
                    if (!p.endsWith("/Android/data") && !p.endsWith("/Android/obb") && !p.endsWith("/.MyFilesTrash")) stack.add(f)
                    continue
                }
                val size = f.length()
                val e = FileEntry(Location.Local(f.path), f.name, false, size, f.lastModified())
                total += size
                count++
                val cat = e.category.group
                byCat[cat] = (byCat[cat] ?: 0L) + size
                heap.add(e)
                if (heap.size > 60) heap.poll()
                if (size >= 256 * 1024) bySize.getOrPut(size) { mutableListOf() }.add(e)
                if (count % 500 == 0) onProgress(count)
            }
        }
        onProgress(count)
        val dups = ArrayList<List<FileEntry>>()
        for ((_, group) in bySize) {
            if (group.size < 2) continue
            currentCoroutineContext().ensureActive()
            group.groupBy { runCatching { quickHash(File(it.location.path), it.size) }.getOrNull() }
                .forEach { (hash, same) -> if (hash != null && same.size > 1) dups += same }
        }
        Result(
            totalBytes = total,
            fileCount = count,
            byCategory = byCat,
            largest = heap.toList().sortedByDescending { it.size },
            duplicates = dups.sortedByDescending { it.first().size * (it.size - 1) }.take(40),
        )
    }

    private fun quickHash(f: File, size: Long): String {
        val md = MessageDigest.getInstance("MD5")
        val chunk = 1024 * 1024
        RandomAccessFile(f, "r").use { raf ->
            val buf = ByteArray(chunk)
            val head = raf.read(buf, 0, minOf(chunk.toLong(), size).toInt())
            if (head > 0) md.update(buf, 0, head)
            if (size > 2L * chunk) {
                raf.seek(size - chunk)
                val tail = raf.read(buf, 0, chunk)
                if (tail > 0) md.update(buf, 0, tail)
            } else if (size > chunk) {
                val rest = raf.read(buf, 0, (size - chunk).toInt())
                if (rest > 0) md.update(buf, 0, rest)
            }
        }
        md.update(size.toString().toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
