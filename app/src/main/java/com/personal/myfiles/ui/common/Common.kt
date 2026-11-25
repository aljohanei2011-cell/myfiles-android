package com.personal.myfiles.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import com.personal.myfiles.core.FileCategory
import com.personal.myfiles.core.FileEntry
import com.personal.myfiles.ui.theme.categoryColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (v >= 100) "${v.toInt()} ${units[i]}" else String.format(Locale.US, "%.1f %s", v, units[i])
}

private val arLocale: Locale = Locale.forLanguageTag("ar-u-nu-latn")

fun formatDate(ms: Long): String {
    if (ms <= 0) return ""
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val time = SimpleDateFormat("h:mm a", arLocale).format(Date(ms))
    return when {
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) -> "اليوم $time"
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) == 1 -> "أمس"
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> SimpleDateFormat("d MMMM", arLocale).format(Date(ms))
        else -> SimpleDateFormat("d MMMM yyyy", arLocale).format(Date(ms))
    }
}

fun formatFullDate(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("d MMMM yyyy، h:mm a", arLocale).format(Date(ms))

fun itemsLabel(n: Int): String = when {
    n <= 0 -> "فارغ"
    n == 1 -> "عنصر واحد"
    n == 2 -> "عنصران"
    n in 3..10 -> "$n عناصر"
    else -> "$n عنصرًا"
}

fun FileEntry.subtitle(): String = if (isDir) {
    listOfNotNull(if (childCount >= 0) itemsLabel(childCount) else null, formatDate(modified).ifEmpty { null }).joinToString(" · ")
} else listOf(formatSize(size), formatDate(modified)).filter { it.isNotEmpty() }.joinToString(" · ")

val FileCategory.icon: ImageVector
    get() = when (this) {
        FileCategory.FOLDER -> Icons.Rounded.Folder
        FileCategory.IMAGE -> Icons.Rounded.Image
        FileCategory.VIDEO -> Icons.Rounded.Movie
        FileCategory.AUDIO -> Icons.Rounded.MusicNote
        FileCategory.PDF -> Icons.Rounded.PictureAsPdf
        FileCategory.DOCUMENT -> Icons.Rounded.Description
        FileCategory.TEXT -> Icons.Rounded.TextSnippet
        FileCategory.ARCHIVE -> Icons.Rounded.FolderZip
        FileCategory.APK -> Icons.Rounded.Android
        FileCategory.OTHER -> Icons.Rounded.InsertDriveFile
    }

@Composable
fun ApkIcon(path: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = ctx.packageManager
                val info = pm.getPackageArchiveInfo(path, 0) ?: return@runCatching null
                val ai = info.applicationInfo ?: return@runCatching null
                ai.sourceDir = path
                ai.publicSourceDir = path
                ai.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
            }.getOrNull()
        }
    }
    bmp?.let { Image(it, null, modifier) }
}

@Composable
fun FileThumb(entry: FileEntry, size: Dp = 44.dp, corner: Dp = 14.dp, iconScale: Float = 0.5f) {
    val (bg, fg) = categoryColors(entry.category)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(entry.category.icon, null, tint = fg, modifier = Modifier.size(size * iconScale))
        val f = entry.file
        if (f != null) when (entry.category) {
            FileCategory.IMAGE, FileCategory.VIDEO -> AsyncImage(
                model = f, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            FileCategory.APK -> ApkIcon(f.path, Modifier.size(size * 0.72f))
            else -> {}
        }
        if (entry.category == FileCategory.VIDEO && f != null) {
            Box(
                Modifier.align(Alignment.Center).size(size * 0.42f).clip(CircleShape).background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(2.dp)) }
        }
    }
}

/* ---------------- intents ---------------- */

fun uriFor(ctx: Context, file: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)

fun mimeOf(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

fun openExternal(ctx: Context, file: File, chooser: Boolean = true) {
    val mime = if (file.extension.equals("apk", true)) "application/vnd.android.package-archive" else mimeOf(file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uriFor(ctx, file), mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        ctx.startActivity(if (chooser) Intent.createChooser(intent, "فتح باستخدام").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "لا يوجد تطبيق يفتح هذا النوع", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, e.message ?: "تعذّر الفتح", Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(ctx: Context, files: List<File>) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { uriFor(ctx, it) })
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
        type = mimeOf(files[0])
        putExtra(Intent.EXTRA_STREAM, uris[0])
    } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(Intent.createChooser(intent, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "تعذّرت المشاركة", Toast.LENGTH_SHORT).show()
    }
}
