package com.personal.myfiles.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

interface VirtualFs {
    suspend fun list(path: String): List<FileEntry>
    suspend fun stat(path: String): FileEntry?
    suspend fun exists(path: String): Boolean = stat(path) != null
    suspend fun mkdir(path: String)
    suspend fun createFile(path: String)
    suspend fun delete(path: String)
    suspend fun rename(path: String, newName: String): String
    suspend fun openInput(path: String): InputStream
    suspend fun openOutput(path: String): OutputStream
}

class LocalFs : VirtualFs {
    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists()) throw IOException("المجلد غير موجود")
        val files = dir.listFiles() ?: throw IOException("لا توجد صلاحية لقراءة هذا المجلد")
        files.map { it.toEntry() }
    }

    override suspend fun stat(path: String): FileEntry? = withContext(Dispatchers.IO) {
        val f = File(path)
        if (f.exists()) f.toEntry() else null
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        val f = File(path)
        if (!f.exists() && !f.mkdirs()) throw IOException("تعذّر إنشاء المجلد")
    }

    override suspend fun createFile(path: String) = withContext(Dispatchers.IO) {
        if (!File(path).createNewFile()) throw IOException("تعذّر إنشاء الملف")
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val f = File(path)
        if (f.exists() && !f.deleteRecursively()) throw IOException("تعذّر حذف ${f.name}")
    }

    override suspend fun rename(path: String, newName: String): String = withContext(Dispatchers.IO) {
        val f = File(path)
        val target = File(f.parentFile, newName)
        if (target.exists()) throw IOException("يوجد عنصر بالاسم نفسه")
        if (!f.renameTo(target)) throw IOException("تعذّرت إعادة التسمية")
        target.path
    }

    override suspend fun openInput(path: String): InputStream = withContext(Dispatchers.IO) { FileInputStream(path) }

    override suspend fun openOutput(path: String): OutputStream = withContext(Dispatchers.IO) {
        File(path).parentFile?.mkdirs()
        FileOutputStream(path)
    }
}

class FsRegistry(private val connections: ConnectionStore) {
    val local = LocalFs()
    private val remotes = ConcurrentHashMap<String, VirtualFs>()

    fun fs(loc: Location): VirtualFs = when (loc) {
        is Location.Local -> local
        is Location.Remote -> remotes.getOrPut(loc.connId) {
            val c = connections.get(loc.connId) ?: throw IOException("الاتصال غير موجود")
            create(c)
        }
    }

    fun invalidate(connId: String) {
        (remotes.remove(connId) as? Closeable)?.let { runCatching { it.close() } }
    }

    companion object {
        fun create(c: Connection): VirtualFs = when (c.type) {
            ConnType.SMB -> SmbFs(c)
            ConnType.FTP -> FtpFs(c)
            ConnType.SFTP -> SftpFs(c)
        }

        suspend fun test(c: Connection): Result<Int> = runCatching {
            val fs = create(c)
            try {
                fs.list(c.rootPath).size
            } finally {
                (fs as? Closeable)?.let { runCatching { it.close() } }
            }
        }
    }
}
