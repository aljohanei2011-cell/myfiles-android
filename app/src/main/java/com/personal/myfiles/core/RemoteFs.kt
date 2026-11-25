package com.personal.myfiles.core

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.Closeable
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import com.jcraft.jsch.Session as SshSession
import com.hierynomus.smbj.connection.Connection as SmbConnection

/* ----------------------------- SMB ----------------------------- */

class SmbFs(private val c: Connection) : VirtualFs, Closeable {
    private val lock = Mutex()
    private var client: SMBClient? = null
    private var connection: SmbConnection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    private suspend fun share(): DiskShare = lock.withLock {
        withContext(Dispatchers.IO) {
            val existing = share
            if (existing != null && existing.isConnected) return@withContext existing
            closeQuietly()
            if (c.share.isBlank()) throw IOException("أدخل اسم المجلد المشترك (Share)")
            val cfg = SmbConfig.builder()
                .withTimeout(30, TimeUnit.SECONDS)
                .withSecurityProvider(BCSecurityProvider())
                .build()
            val cl = SMBClient(cfg)
            val cn = cl.connect(c.host, c.port)
            val auth = if (c.anonymous || c.user.isBlank()) AuthenticationContext.guest()
            else AuthenticationContext(c.user, c.pass.toCharArray(), c.domain.ifBlank { null })
            val ses = cn.authenticate(auth)
            val ds = ses.connectShare(c.share) as? DiskShare ?: throw IOException("المجلد المشترك ليس مجلد ملفات")
            client = cl; connection = cn; session = ses; share = ds
            ds
        }
    }

    private fun p(path: String) = path.trim('/').replace('/', '\\')

    private fun entry(path: String, name: String, dir: Boolean, size: Long, mod: Long) =
        FileEntry(Location.Remote(c.id, path), name, dir, if (dir) 0 else size, mod)

    override suspend fun list(path: String): List<FileEntry> {
        val s = share()
        return withContext(Dispatchers.IO) {
            s.list(p(path)).filter { it.fileName != "." && it.fileName != ".." }.map {
                val dir = (it.fileAttributes and 0x10L) != 0L
                entry(joinPath(path, it.fileName), it.fileName, dir, it.endOfFile, it.lastWriteTime.toEpochMillis())
            }
        }
    }

    override suspend fun stat(path: String): FileEntry? {
        val s = share()
        return withContext(Dispatchers.IO) {
            if (path.trim('/').isEmpty()) return@withContext entry("/", c.share, true, 0, 0)
            if (!s.fileExists(p(path)) && !s.folderExists(p(path))) return@withContext null
            val info = s.getFileInformation(p(path))
            val name = path.trimEnd('/').substringAfterLast('/')
            entry(path, name, info.standardInformation.isDirectory, info.standardInformation.endOfFile,
                info.basicInformation.lastWriteTime.toEpochMillis())
        }
    }

    override suspend fun mkdir(path: String) {
        val s = share()
        withContext(Dispatchers.IO) { if (!s.folderExists(p(path))) s.mkdir(p(path)) }
    }

    override suspend fun createFile(path: String) {
        openOutput(path).close()
    }

    override suspend fun delete(path: String) {
        val s = share()
        withContext(Dispatchers.IO) {
            if (s.folderExists(p(path))) s.rmdir(p(path), true) else s.rm(p(path))
        }
    }

    override suspend fun rename(path: String, newName: String): String {
        val s = share()
        val target = joinPath(parentPath(path) ?: "/", newName)
        withContext(Dispatchers.IO) {
            val isDir = s.folderExists(p(path))
            val access = EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ)
            val entry = if (isDir) s.openDirectory(p(path), access, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
            else s.openFile(p(path), access, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
            entry.use { it.rename(p(target)) }
        }
        return target
    }

    override suspend fun openInput(path: String): InputStream {
        val s = share()
        return withContext(Dispatchers.IO) {
            val f = s.openFile(p(path), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
            object : FilterInputStream(f.inputStream) {
                override fun close() {
                    try { super.close() } finally { runCatching { f.close() } }
                }
            }
        }
    }

    override suspend fun openOutput(path: String): OutputStream {
        val s = share()
        return withContext(Dispatchers.IO) {
            val f = s.openFile(p(path), EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null)
            object : FilterOutputStream(f.outputStream) {
                override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
                override fun close() {
                    try { super.close() } finally { runCatching { f.close() } }
                }
            }
        }
    }

    private fun closeQuietly() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null; session = null; connection = null; client = null
    }

    override fun close() = closeQuietly()
}

/* ----------------------------- FTP ----------------------------- */

class FtpFs(private val c: Connection) : VirtualFs {

    private fun connect(): FTPClient {
        val f = FTPClient()
        f.connectTimeout = 20000
        f.controlEncoding = "UTF-8"
        f.connect(c.host, c.port)
        if (!FTPReply.isPositiveCompletion(f.replyCode)) {
            runCatching { f.disconnect() }
            throw IOException("رفض الخادم الاتصال")
        }
        val user = if (c.anonymous || c.user.isBlank()) "anonymous" else c.user
        val pass = if (c.anonymous || c.user.isBlank()) "guest@" else c.pass
        if (!f.login(user, pass)) {
            runCatching { f.disconnect() }
            throw IOException("اسم المستخدم أو كلمة المرور غير صحيحة")
        }
        f.enterLocalPassiveMode()
        f.setFileType(FTP.BINARY_FILE_TYPE)
        f.soTimeout = 60000
        return f
    }

    private fun close(f: FTPClient) {
        runCatching { f.logout() }
        runCatching { f.disconnect() }
    }

    private suspend fun <T> withClient(block: (FTPClient) -> T): T = withContext(Dispatchers.IO) {
        val f = connect()
        try { block(f) } finally { close(f) }
    }

    override suspend fun list(path: String): List<FileEntry> = withClient { f ->
        val files = f.listFiles(path) ?: throw IOException("تعذّرت قراءة المجلد")
        files.filter { it.name != "." && it.name != ".." }.map {
            FileEntry(Location.Remote(c.id, joinPath(path, it.name)), it.name, it.isDirectory,
                if (it.isDirectory) 0 else it.size, it.timestamp?.timeInMillis ?: 0L)
        }
    }

    override suspend fun stat(path: String): FileEntry? {
        if (path == "/" || path.isEmpty()) return FileEntry(Location.Remote(c.id, "/"), "/", true, 0, 0)
        val parent = parentPath(path) ?: "/"
        val name = path.trimEnd('/').substringAfterLast('/')
        return runCatching { list(parent).firstOrNull { it.name == name } }.getOrNull()
    }

    override suspend fun mkdir(path: String) {
        withClient { f -> if (!f.makeDirectory(path) && !f.changeWorkingDirectory(path)) throw IOException("تعذّر إنشاء المجلد") }
    }

    override suspend fun createFile(path: String) {
        withClient { f -> if (!f.storeFile(path, java.io.ByteArrayInputStream(ByteArray(0)))) throw IOException("تعذّر إنشاء الملف") }
    }

    private fun deleteRec(f: FTPClient, path: String) {
        val asDir = f.listFiles(path)
        if (f.changeWorkingDirectory(path)) {
            asDir?.filter { it.name != "." && it.name != ".." }?.forEach { deleteRec(f, joinPath(path, it.name)) }
            f.changeWorkingDirectory("/")
            if (!f.removeDirectory(path)) throw IOException("تعذّر حذف المجلد")
        } else if (!f.deleteFile(path)) throw IOException("تعذّر حذف الملف")
    }

    override suspend fun delete(path: String) = withClient { f -> deleteRec(f, path) }

    override suspend fun rename(path: String, newName: String): String {
        val target = joinPath(parentPath(path) ?: "/", newName)
        withClient { f -> if (!f.rename(path, target)) throw IOException("تعذّرت إعادة التسمية") }
        return target
    }

    override suspend fun openInput(path: String): InputStream = withContext(Dispatchers.IO) {
        val f = connect()
        val s = f.retrieveFileStream(path) ?: run { close(f); throw IOException("تعذّر فتح الملف") }
        object : FilterInputStream(s) {
            override fun close() {
                try { super.close(); runCatching { f.completePendingCommand() } } finally { close(f) }
            }
        }
    }

    override suspend fun openOutput(path: String): OutputStream = withContext(Dispatchers.IO) {
        val f = connect()
        val s = f.storeFileStream(path) ?: run { close(f); throw IOException("تعذّر الكتابة على الخادم") }
        object : FilterOutputStream(s) {
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
            override fun close() {
                try { super.close(); runCatching { f.completePendingCommand() } } finally { close(f) }
            }
        }
    }
}

/* ----------------------------- SFTP ----------------------------- */

class SftpFs(private val c: Connection) : VirtualFs, Closeable {
    private val lock = Mutex()
    private var session: SshSession? = null

    private suspend fun session(): SshSession = lock.withLock {
        withContext(Dispatchers.IO) {
            session?.let { if (it.isConnected) return@withContext it }
            val js = JSch()
            val s = js.getSession(c.user, c.host, c.port)
            s.setPassword(c.pass)
            s.setConfig("StrictHostKeyChecking", "no")
            s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
            s.connect(20000)
            session = s
            s
        }
    }

    private suspend fun <T> withChannel(block: (ChannelSftp) -> T): T {
        val s = session()
        return withContext(Dispatchers.IO) {
            val ch = s.openChannel("sftp") as ChannelSftp
            ch.connect(20000)
            try { block(ch) } finally { ch.disconnect() }
        }
    }

    private fun lsEntries(ch: ChannelSftp, path: String): List<ChannelSftp.LsEntry> =
        (ch.ls(path) as java.util.Vector<*>).filterIsInstance<ChannelSftp.LsEntry>()
            .filter { it.filename != "." && it.filename != ".." }

    override suspend fun list(path: String): List<FileEntry> = withChannel { ch ->
        lsEntries(ch, path).map {
            val a = it.attrs
            FileEntry(Location.Remote(c.id, joinPath(path, it.filename)), it.filename, a.isDir,
                if (a.isDir) 0 else a.size, a.mTime.toLong() * 1000L)
        }
    }

    override suspend fun stat(path: String): FileEntry? = runCatching {
        withChannel { ch ->
            val a = ch.stat(path)
            val name = path.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }
            FileEntry(Location.Remote(c.id, path), name, a.isDir, if (a.isDir) 0 else a.size, a.mTime.toLong() * 1000L)
        }
    }.getOrNull()

    override suspend fun mkdir(path: String) = withChannel { ch ->
        try { ch.mkdir(path) } catch (e: SftpException) { if (runCatching { ch.stat(path).isDir }.getOrDefault(false).not()) throw IOException(e.message) }
    }

    override suspend fun createFile(path: String) {
        openOutput(path).close()
    }

    private fun deleteRec(ch: ChannelSftp, path: String) {
        val a = ch.stat(path)
        if (a.isDir) {
            lsEntries(ch, path).forEach { deleteRec(ch, joinPath(path, it.filename)) }
            ch.rmdir(path)
        } else ch.rm(path)
    }

    override suspend fun delete(path: String) = withChannel { ch -> deleteRec(ch, path) }

    override suspend fun rename(path: String, newName: String): String {
        val target = joinPath(parentPath(path) ?: "/", newName)
        withChannel { ch -> ch.rename(path, target) }
        return target
    }

    override suspend fun openInput(path: String): InputStream {
        val s = session()
        return withContext(Dispatchers.IO) {
            val ch = s.openChannel("sftp") as ChannelSftp
            ch.connect(20000)
            val input = ch.get(path)
            object : FilterInputStream(input) {
                override fun close() {
                    try { super.close() } finally { ch.disconnect() }
                }
            }
        }
    }

    override suspend fun openOutput(path: String): OutputStream {
        val s = session()
        return withContext(Dispatchers.IO) {
            val ch = s.openChannel("sftp") as ChannelSftp
            ch.connect(20000)
            val out = ch.put(path)
            object : FilterOutputStream(out) {
                override fun write(b: ByteArray, off: Int, len: Int) { this.out.write(b, off, len) }
                override fun close() {
                    try { super.close() } finally { ch.disconnect() }
                }
            }
        }
    }

    override fun close() {
        runCatching { session?.disconnect() }
        session = null
    }
}
