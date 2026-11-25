package com.personal.myfiles.core

import java.io.File

sealed interface Location {
    val path: String
    val key: String
    val name: String

    data class Local(override val path: String) : Location {
        override val key: String get() = "local:$path"
        override val name: String get() = File(path).name.ifEmpty { path }
    }

    data class Remote(val connId: String, override val path: String) : Location {
        override val key: String get() = "remote:$connId:$path"
        override val name: String get() = path.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }
    }
}

fun joinPath(dir: String, name: String): String =
    if (dir.endsWith("/")) dir + name else "$dir/$name"

fun Location.child(name: String): Location = when (this) {
    is Location.Local -> Location.Local(joinPath(path, name))
    is Location.Remote -> Location.Remote(connId, joinPath(path, name))
}

fun Location.withPath(newPath: String): Location = when (this) {
    is Location.Local -> Location.Local(newPath)
    is Location.Remote -> Location.Remote(connId, newPath)
}

fun parentPath(path: String): String? {
    val p = path.trimEnd('/')
    if (p.isEmpty()) return null
    val idx = p.lastIndexOf('/')
    return when {
        idx < 0 -> null
        idx == 0 -> "/"
        else -> p.substring(0, idx)
    }
}

data class FileEntry(
    val location: Location,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val hidden: Boolean = name.startsWith("."),
    val childCount: Int = -1,
) {
    val ext: String get() = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
    val category: FileCategory get() = FileCategory.of(name, isDir)
    val isLocal: Boolean get() = location is Location.Local
    val file: File? get() = (location as? Location.Local)?.let { File(it.path) }
}

fun File.toEntry(): FileEntry {
    val dir = isDirectory
    return FileEntry(
        location = Location.Local(path),
        name = name,
        isDir = dir,
        size = if (dir) 0L else length(),
        modified = lastModified(),
        hidden = name.startsWith(".") || isHidden,
        childCount = if (dir) (list()?.size ?: 0) else -1,
    )
}

enum class FileCategory(val label: String) {
    FOLDER("مجلد"),
    IMAGE("صورة"),
    VIDEO("فيديو"),
    AUDIO("صوت"),
    PDF("مستند PDF"),
    DOCUMENT("مستند"),
    TEXT("ملف نصي"),
    ARCHIVE("ملف مضغوط"),
    APK("تطبيق"),
    OTHER("ملف");

    /** Groups used by filter chips. */
    val group: FileCategory
        get() = when (this) {
            PDF, TEXT -> DOCUMENT
            else -> this
        }

    companion object {
        val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
        val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "flv", "wmv")
        val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "wma", "mid")
        val DOC_EXT = setOf("doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "epub")
        val TEXT_EXT = setOf(
            "txt", "md", "log", "json", "xml", "csv", "html", "htm", "css", "js", "ts", "kt", "kts",
            "java", "py", "c", "cpp", "h", "sh", "ini", "conf", "cfg", "yml", "yaml", "properties",
            "gradle", "svg", "sql", "srt", "vtt", "toml", "env"
        )
        val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

        fun of(name: String, isDir: Boolean): FileCategory {
            if (isDir) return FOLDER
            return when (name.substringAfterLast('.', "").lowercase()) {
                in IMAGE_EXT -> IMAGE
                in VIDEO_EXT -> VIDEO
                in AUDIO_EXT -> AUDIO
                "pdf" -> PDF
                in DOC_EXT -> DOCUMENT
                in TEXT_EXT -> TEXT
                in ARCHIVE_EXT -> ARCHIVE
                "apk" -> APK
                else -> OTHER
            }
        }
    }
}

/** Natural ordering: "IMG_2" before "IMG_10". */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

fun sortEntries(
    list: List<FileEntry>,
    sortBy: Int,
    desc: Boolean,
    foldersFirst: Boolean,
    showHidden: Boolean,
    filter: FileCategory?,
): List<FileEntry> {
    var l = if (showHidden) list else list.filterNot { it.hidden }
    if (filter != null) l = l.filter { !it.isDir && it.category.group == filter }
    val base: Comparator<FileEntry> = when (sortBy) {
        1 -> compareBy<FileEntry> { it.modified }
        2 -> compareBy<FileEntry> { if (it.isDir) it.childCount.toLong() else it.size }
        3 -> compareBy<FileEntry> { it.ext }.then(Comparator { x, y -> NaturalOrder.compare(x.name, y.name) })
        else -> Comparator { x, y -> NaturalOrder.compare(x.name, y.name) }
    }
    val ordered = if (desc) base.reversed() else base
    val cmp = if (foldersFirst) compareBy<FileEntry> { !it.isDir }.then(ordered) else ordered
    return l.sortedWith(cmp)
}
