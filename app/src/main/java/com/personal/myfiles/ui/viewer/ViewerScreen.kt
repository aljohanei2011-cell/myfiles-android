@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.personal.myfiles.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.personal.myfiles.core.FileCategory
import com.personal.myfiles.core.toEntry
import com.personal.myfiles.ui.DialogReq
import com.personal.myfiles.ui.MainViewModel
import com.personal.myfiles.ui.ViewerKind
import com.personal.myfiles.ui.common.formatSize
import com.personal.myfiles.ui.common.icon
import com.personal.myfiles.ui.common.openExternal
import com.personal.myfiles.ui.common.shareFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ViewerScreen(vm: MainViewModel, nav: NavController) {
    val req = vm.viewer
    if (req == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    when (req.kind) {
        ViewerKind.IMAGE -> ImageViewer(vm, nav, req.file, req.siblings)
        ViewerKind.MEDIA -> MediaViewer(vm, nav, req.file)
        ViewerKind.TEXT -> TextViewer(vm, nav, req.file)
        ViewerKind.PDF -> PdfViewer(vm, nav, req.file)
    }
}

@Composable
private fun ViewerBar(
    vm: MainViewModel, nav: NavController, file: File, dark: Boolean = false, subtitle: String? = null,
    extra: @Composable RowScope.() -> Unit = {},
) {
    val ctx = LocalContext.current
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    TopAppBar(
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", tint = fg) } },
        title = {
            Column {
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg, style = MaterialTheme.typography.titleMedium)
                Text(subtitle ?: formatSize(file.length()), style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f))
            }
        },
        actions = {
            extra()
            IconButton(onClick = { shareFiles(ctx, listOf(file)) }) { Icon(Icons.Rounded.Share, "مشاركة", tint = fg) }
            IconButton(onClick = { openExternal(ctx, file) }) { Icon(Icons.Rounded.OpenInNew, "فتح باستخدام", tint = fg) }
            IconButton(onClick = { vm.dialog = DialogReq.Details(file.toEntry()) }) { Icon(Icons.Rounded.Info, "التفاصيل", tint = fg) }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (dark) Color(0xCC000000) else MaterialTheme.colorScheme.surface,
        ),
    )
}

/* ---------------- Image ---------------- */

@Composable
private fun ZoomableImage(file: File, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f },
                )
            }
            .pointerInput(file) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.size > 1
                        if (multi || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = file, contentDescription = file.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            },
        )
    }
}

@Composable
private fun ImageViewer(vm: MainViewModel, nav: NavController, file: File, siblings: List<File>) {
    val list = siblings.ifEmpty { listOf(file) }
    val start = list.indexOfFirst { it.path == file.path }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { list.size }
    var chrome by remember { mutableStateOf(true) }
    val current = list.getOrElse(pager.currentPage) { file }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { list[it].path }) { page ->
            ZoomableImage(list[page]) { chrome = !chrome }
        }
        if (chrome) {
            Box(Modifier.align(Alignment.TopCenter)) {
                ViewerBar(vm, nav, current, dark = true, subtitle = "${pager.currentPage + 1} من ${list.size} · ${formatSize(current.length())}")
            }
        }
    }
}

/* ---------------- Media ---------------- */

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun MediaViewer(vm: MainViewModel, nav: NavController, file: File) {
    val ctx = LocalContext.current
    val isAudio = FileCategory.of(file.name, false) == FileCategory.AUDIO
    val player = remember(file) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isAudio) {
            Column(Modifier.align(Alignment.Center).padding(bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(180.dp).clip(RoundedCornerShape(48.dp)).background(Color(0xFF3A2E66)),
                    contentAlignment = Alignment.Center,
                ) { Icon(FileCategory.AUDIO.icon, null, Modifier.size(84.dp), tint = Color(0xFFD4C6FF)) }
                Spacer(Modifier.height(20.dp))
                Text(file.nameWithoutExtension, color = Color.White, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 32.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = player
                        if (isAudio) controllerShowTimeoutMs = 0
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
            )
        }
        Box(Modifier.align(Alignment.TopCenter)) { ViewerBar(vm, nav, file, dark = true) }
    }
}

/* ---------------- Text ---------------- */

@Composable
private fun TextViewer(vm: MainViewModel, nav: NavController, file: File) {
    val limit = 2 * 1024 * 1024
    var reloadKey by remember { mutableIntStateOf(0) }
    val content by produceState<String?>(null, file, reloadKey) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (file.length() > limit) file.inputStream().use { s ->
                    val b = ByteArray(limit); val n = s.read(b); String(b, 0, maxOf(n, 0))
                } else file.readText()
            }.getOrElse { "تعذّرت قراءة الملف: ${it.message}" }
        }
    }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var mono by remember { mutableStateOf(true) }
    val canEdit = file.canWrite() && file.length() <= 1024 * 1024
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            ViewerBar(vm, nav, file) {
                if (editing) {
                    IconButton(onClick = {
                        scope.launchSave(file, draft) { ok ->
                            vm.c.ops.messages.tryEmit(if (ok) "تم الحفظ" else "تعذّر الحفظ")
                            if (ok) { editing = false; reloadKey++ }
                        }
                    }) { Icon(Icons.Rounded.Save, "حفظ") }
                } else {
                    IconButton(onClick = { mono = !mono }) { Icon(Icons.Rounded.FontDownload, "الخط") }
                    if (canEdit && content != null) IconButton(onClick = { draft = content ?: ""; editing = true }) { Icon(Icons.Rounded.Edit, "تعديل") }
                }
            }
        },
    ) { pad ->
        val text = content
        if (text == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (editing) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                modifier = Modifier.padding(pad).fillMaxSize().padding(8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (mono) FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily),
            )
        } else {
            val lines = remember(text) { text.lines() }
            SelectionContainer(Modifier.padding(pad)) {
                LazyColumn(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(lines) { i, line ->
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                "${i + 1}", Modifier.width(44.dp), color = MaterialTheme.colorScheme.outline,
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            )
                            Text(
                                line.ifEmpty { " " },
                                fontFamily = if (mono) FontFamily.Monospace else null,
                                fontSize = 13.sp, softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchSave(file: File, text: String, done: (Boolean) -> Unit) {
    launch {
        val ok = withContext(Dispatchers.IO) { runCatching { file.writeText(text) }.isSuccess }
        done(ok)
    }
}

/* ---------------- PDF ---------------- */

private class PdfDoc(file: File) : AutoCloseable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val lock = Mutex()
    val pageCount: Int get() = renderer.pageCount

    suspend fun render(index: Int, width: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val h = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

@Composable
private fun PdfViewer(vm: MainViewModel, nav: NavController, file: File) {
    val doc = remember(file) { runCatching { PdfDoc(file) }.getOrNull() }
    DisposableEffect(doc) { onDispose { doc?.close() } }
    val widthPx = with(LocalDensity.current) { (LocalContext.current.resources.displayMetrics.widthPixels).coerceAtMost(2000) }
    var scale by remember { mutableFloatStateOf(1f) }
    Scaffold(
        topBar = {
            ViewerBar(vm, nav, file, subtitle = doc?.let { "${it.pageCount} صفحة" }) {
                IconButton(onClick = { scale = (scale - 0.25f).coerceAtLeast(1f) }) { Icon(Icons.Rounded.ZoomOut, "تصغير") }
                IconButton(onClick = { scale = (scale + 0.25f).coerceAtMost(3f) }) { Icon(Icons.Rounded.ZoomIn, "تكبير") }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { pad ->
        if (doc == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("تعذّر فتح ملف PDF") }
            return@Scaffold
        }
        val hScroll = rememberScrollState()
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().horizontalScroll(hScroll),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(doc.pageCount) { i ->
                val bmp by produceState<Bitmap?>(null, i) { value = runCatching { doc.render(i, (widthPx * 1.5f).toInt()) }.getOrNull() }
                val cfgWidth = LocalContext.current.resources.configuration.screenWidthDp
                Box(
                    Modifier.width((cfgWidth - 24).dp * scale).aspectRatio(bmp?.let { it.width.toFloat() / it.height } ?: 0.707f)
                        .clip(RoundedCornerShape(6.dp)).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    val b = bmp
                    if (b == null) CircularProgressIndicator(Modifier.size(28.dp))
                    else Image(b.asImageBitmap(), "صفحة ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}
