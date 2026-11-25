@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.personal.myfiles.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.personal.myfiles.core.FileCategory
import com.personal.myfiles.core.FileEntry
import com.personal.myfiles.core.Location
import com.personal.myfiles.core.Storage
import com.personal.myfiles.ui.DialogReq
import com.personal.myfiles.ui.MainViewModel
import com.personal.myfiles.ui.browse.FileRow
import com.personal.myfiles.ui.common.formatSize
import com.personal.myfiles.ui.common.icon
import com.personal.myfiles.ui.theme.cardColor
import com.personal.myfiles.ui.theme.categoryColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private data class Cat(val key: String, val label: String, val cat: FileCategory, val icon: ImageVector? = null)

private val cats = listOf(
    Cat("images", "الصور", FileCategory.IMAGE),
    Cat("videos", "الفيديو", FileCategory.VIDEO),
    Cat("audio", "الصوت", FileCategory.AUDIO),
    Cat("documents", "المستندات", FileCategory.DOCUMENT),
    Cat("apks", "التطبيقات", FileCategory.APK),
    Cat("archives", "المضغوطة", FileCategory.ARCHIVE),
    Cat("downloads", "التنزيلات", FileCategory.FOLDER, Icons.Rounded.Download),
    Cat("favorites", "المفضلة", FileCategory.PDF, Icons.Rounded.Star),
)

private data class Breakdown(val images: Long, val videos: Long, val audio: Long, val docs: Long)

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val tick = vm.tick
    val volumes = remember(tick) { Storage.volumes() }
    val primary = volumes.firstOrNull { it.primary }
    val counts by produceState(emptyMap<String, Int>(), tick) {
        value = coroutineScope {
            cats.filter { it.key !in setOf("downloads", "favorites") }
                .map { c -> c.key to async { Storage.count(c.key) } }
                .associate { (k, d) -> k to d.await() }
        }
    }
    val breakdown by produceState<Breakdown?>(null, tick) {
        value = coroutineScope {
            val i = async { Storage.sizeOf("images") }
            val v = async { Storage.sizeOf("videos") }
            val a = async { Storage.sizeOf("audio") }
            val d = async { Storage.sizeOf("documents") }
            Breakdown(i.await(), v.await(), a.await(), d.await())
        }
    }
    val recent by produceState(emptyList<FileEntry>(), tick) { value = Storage.query("recent", 6) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Search pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { nav.navigate("search") },
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("ابحث في ملفاتك", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = { nav.navigate("settings") }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Settings, "الإعدادات")
                    }
                }

                // Storage card
                if (primary != null) StorageCard(primary, breakdown, volumes.filter { !it.primary }, onAnalyze = { nav.navigate("analyzer") }) {
                    vm.openInBrowser(Location.Local(it), nav)
                }

                // Categories
                Text("الفئات", style = MaterialTheme.typography.titleMedium)
                val columns = if (wide) 8 else 4
                cats.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { c ->
                            CategoryTile(c, counts[c.key], Modifier.weight(1f)) {
                                when (c.key) {
                                    "downloads" -> vm.openInBrowser(Location.Local(Storage.primaryPath + "/Download"), nav)
                                    else -> nav.navigate("category/${c.key}")
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                // Recent
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الأحدث", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("category/recent") }) { Text("عرض الكل") }
                }
                Surface(shape = RoundedCornerShape(24.dp), color = cardColor()) {
                    Column(Modifier.padding(vertical = 4.dp, horizontal = 4.dp)) {
                        if (recent.isEmpty()) Text(
                            "لا توجد ملفات حديثة", Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        recent.forEach { e ->
                            FileRow(
                                e, selected = false, selectionMode = false,
                                onClick = { vm.openEntry(e, nav, ctx) },
                                onLongClick = { vm.dialog = DialogReq.Actions(e, null) },
                                onMore = { vm.dialog = DialogReq.Actions(e, null) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun StorageCard(
    v: Storage.Volume,
    b: Breakdown?,
    others: List<Storage.Volume>,
    onAnalyze: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val pct = if (v.total > 0) (v.used * 100 / v.total).toInt() else 0
    val img = categoryColors(FileCategory.IMAGE).second
    val vid = categoryColors(FileCategory.VIDEO).second
    val aud = categoryColors(FileCategory.AUDIO).second
    val doc = categoryColors(FileCategory.DOCUMENT).second
    val other = MaterialTheme.colorScheme.outline
    Surface(shape = RoundedCornerShape(28.dp), color = cardColor(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.clickable { onOpen(v.path) }, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.name, style = MaterialTheme.typography.titleMedium)
                    Text("${formatSize(v.used)} مستخدمة من ${formatSize(v.total)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("$pct%", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            // segmented bar
            val total = v.total.coerceAtLeast(1).toFloat()
            val segs = if (b != null) {
                val known = b.images + b.videos + b.audio + b.docs
                listOf(
                    b.images to img, b.videos to vid, b.audio to aud, b.docs to doc,
                    (v.used - known).coerceAtLeast(0) to other,
                )
            } else listOf(v.used to MaterialTheme.colorScheme.primary)
            Row(
                Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                segs.forEach { (bytes, color) ->
                    val w = bytes / total
                    if (w > 0.004f) Box(Modifier.fillMaxHeight().weight(w).background(color))
                }
                val free = v.free / total
                if (free > 0.004f) Spacer(Modifier.weight(free))
            }
            if (b != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Legend(img, "صور ${formatSize(b.images)}")
                    Legend(vid, "فيديو ${formatSize(b.videos)}")
                    Legend(aud, "صوت ${formatSize(b.audio)}")
                    Legend(doc, "مستندات ${formatSize(b.docs)}")
                    Legend(other, "أخرى")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAnalyze, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CleaningServices, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("تحليل وإخلاء المساحة")
                }
            }
            if (others.isNotEmpty()) {
                HorizontalDivider()
                others.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(o.path) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (o.isUsb) Icons.Rounded.Usb else Icons.Rounded.SdCard, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${formatSize(o.free)} متاحة من ${formatSize(o.total)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryTile(c: Cat, count: Int?, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg) = if (c.key == "favorites") Color(0xFFFCE0E6).let { light ->
        if (com.personal.myfiles.ui.theme.LocalIsDark.current) Color(0xFF5A1F31) to Color(0xFFFFB1C4) else light to Color(0xFF92243F)
    } else categoryColors(c.cat)
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(c.icon ?: c.cat.icon, null, tint = fg, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(c.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            count?.let { "%,d".format(it) } ?: " ",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
