@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.personal.myfiles.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.personal.myfiles.core.FileCategory
import com.personal.myfiles.core.FileEntry
import com.personal.myfiles.core.Location
import com.personal.myfiles.core.Storage
import com.personal.myfiles.core.TrashManager
import com.personal.myfiles.core.toEntry
import com.personal.myfiles.ui.DialogReq
import com.personal.myfiles.ui.MainViewModel
import com.personal.myfiles.ui.browse.FileGridItem
import com.personal.myfiles.ui.browse.FileRow
import com.personal.myfiles.ui.common.FileThumb
import com.personal.myfiles.ui.common.formatDate
import com.personal.myfiles.ui.common.formatSize
import com.personal.myfiles.ui.common.itemsLabel
import com.personal.myfiles.ui.theme.cardColor
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun BackTopBar(title: String, nav: NavController, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

private val categoryTitles = mapOf(
    "images" to "الصور", "videos" to "الفيديو", "audio" to "الصوت", "documents" to "المستندات",
    "apks" to "التطبيقات (APK)", "archives" to "الملفات المضغوطة", "recent" to "الأحدث", "favorites" to "المفضلة",
)

@Composable
fun CategoryScreen(vm: MainViewModel, nav: NavController, key: String) {
    val ctx = LocalContext.current
    val s = vm.c.settings
    val favs = s.favorites
    var loading by remember { mutableStateOf(true) }
    val list by produceState(emptyList<FileEntry>(), key, vm.tick, favs) {
        loading = true
        value = if (key == "favorites") favs.map { File(it) }.filter { it.exists() }.map { it.toEntry() }
        else Storage.query(key, if (key == "recent") 200 else 5000)
        loading = false
    }
    var grid by remember { mutableStateOf(key == "images" || key == "videos") }
    Scaffold(
        topBar = {
            BackTopBar(categoryTitles[key] ?: key, nav, if (loading) null else itemsLabel(list.size)) {
                IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, "العرض") }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else if (list.isEmpty()) Text("لا توجد عناصر", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (grid) LazyVerticalGrid(
                GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.location.key }) { e ->
                    FileGridItem(e, false, onClick = { vm.openEntry(e, nav, ctx) }, onLongClick = { vm.dialog = DialogReq.Actions(e, null) })
                }
            } else LazyColumn(contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, 24.dp)) {
                items(list, key = { it.location.key }) { e ->
                    FileRow(
                        e, false, false,
                        onClick = { vm.openEntry(e, nav, ctx) },
                        onLongClick = { vm.dialog = DialogReq.Actions(e, null) },
                        onMore = { vm.dialog = DialogReq.Actions(e, null) },
                    )
                }
            }
        }
    }
}

/* ---------------- Search ---------------- */

@Composable
fun SearchScreen(vm: MainViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val s = vm.c.settings
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<FileCategory?>(null) }
    val paneLoc = vm.active.location
    val canScopeFolder = paneLoc is Location.Local
    var inFolder by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<FileEntry>()) }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query, filter, inFolder) {
        if (query.trim().length < 2) { results = emptyList(); searching = false; return@LaunchedEffect }
        delay(350)
        searching = true
        val root = if (inFolder && canScopeFolder) File(paneLoc.path) else File(Storage.primaryPath)
        Storage.search(root, query, filter, s.showHidden).collect { results = it }
        searching = false
    }
    Scaffold(
        topBar = {
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") }
                TextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("ابحث بالاسم…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "مسح") } },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { kb?.hide() }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canScopeFolder) FilterChip(
                    selected = inFolder, onClick = { inFolder = !inFolder },
                    label = { Text(if (inFolder) "داخل: ${File(paneLoc.path).name}" else "كل الذاكرة") },
                    leadingIcon = { Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)) },
                )
                listOf(null to "الكل", FileCategory.DOCUMENT to "مستندات", FileCategory.IMAGE to "صور", FileCategory.VIDEO to "فيديو",
                    FileCategory.AUDIO to "صوت", FileCategory.ARCHIVE to "مضغوطة", FileCategory.APK to "تطبيقات").forEach { (c, l) ->
                    FilterChip(selected = filter == c, onClick = { filter = c }, label = { Text(l) })
                }
            }
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!searching && query.trim().length >= 2 && results.isEmpty()) {
                Text("لا توجد نتائج", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, 24.dp)) {
                items(results, key = { it.location.key }) { e ->
                    FileRow(
                        e, false, false,
                        onClick = { vm.openEntry(e, nav, ctx) },
                        onLongClick = { vm.dialog = DialogReq.Actions(e, null) },
                        onMore = { vm.dialog = DialogReq.Actions(e, null) },
                    )
                }
            }
        }
    }
}

/* ---------------- Trash ---------------- */

@Composable
fun TrashScreen(vm: MainViewModel, nav: NavController) {
    val trashItems by vm.c.trash.items.collectAsState()
    val days = vm.c.settings.trashDays
    var confirmEmpty by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TrashManager.Item?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("المهملات")
                        Text(
                            if (trashItems.isEmpty()) "فارغة" else "${itemsLabel(trashItems.size)} · ${formatSize(trashItems.sumOf { it.size })}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (trashItems.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) {
                        Text("إفراغ", color = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { pad ->
        if (trashItems.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize(),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(16.dp))
                Text("المهملات فارغة", style = MaterialTheme.typography.titleMedium)
                Text("تُحذف العناصر تلقائيًا بعد $days يومًا", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, 24.dp)) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null)
                        Spacer(Modifier.width(10.dp))
                        Text("تبقى العناصر هنا $days يومًا ثم تُحذف نهائيًا.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(trashItems.sortedByDescending { it.deletedAt }, key = { it.id }) { t ->
                val left = (days - ((System.currentTimeMillis() - t.deletedAt) / 86_400_000L)).coerceAtLeast(0)
                val e = FileEntry(Location.Local(t.trashPath), t.name, t.isDir, t.size, t.deletedAt)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .combinedClickable(onClick = { selected = t }).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FileThumb(e, 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatSize(t.size)} · حُذف ${formatDate(t.deletedAt)} · يتبقى $left يومًا",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                        Text(t.original.removePrefix(Storage.primaryPath).ifEmpty { "/" }, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        runCatching { vm.c.trash.restore(t) }
                            .onSuccess { vm.c.ops.messages.tryEmit("استُعيد «${t.name}»"); vm.c.ops.changes.tryEmit(Unit) }
                            .onFailure { vm.c.ops.messages.tryEmit(it.message ?: "تعذّرت الاستعادة") }
                    }) { Icon(Icons.Rounded.Restore, "استعادة", tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text("إفراغ المهملات؟") },
        text = { Text("سيُحذف ${itemsLabel(trashItems.size)} نهائيًا.") },
        confirmButton = {
            Button(
                onClick = { vm.c.trash.empty(); confirmEmpty = false },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text("حذف الكل") }
        },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("إلغاء") } },
    )
    selected?.let { t ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(t.name) },
            text = { Text("الموقع الأصلي:\n${t.original}") },
            confirmButton = {
                Button(onClick = {
                    runCatching { vm.c.trash.restore(t) }.onFailure { vm.c.ops.messages.tryEmit(it.message ?: "تعذّرت الاستعادة") }
                    vm.c.ops.changes.tryEmit(Unit)
                    selected = null
                }) { Text("استعادة") }
            },
            dismissButton = {
                TextButton(onClick = { vm.c.trash.deleteForever(t); selected = null }) {
                    Text("حذف نهائي", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

/* ---------------- Analyzer ---------------- */

@Composable
fun AnalyzerScreen(vm: MainViewModel, nav: NavController) {
    val ctx = LocalContext.current
    var scanned by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<com.personal.myfiles.core.Analyzer.Result?>(null) }
    var run by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(run) {
        result = null
        chosen = emptySet()
        result = com.personal.myfiles.core.Analyzer.scan(File(Storage.primaryPath)) { scanned = it }
    }
    val r = result
    Scaffold(
        topBar = {
            BackTopBar("تحليل المساحة", nav, r?.let { "${"%,d".format(it.fileCount)} ملف · ${formatSize(it.totalBytes)}" }) {
                IconButton(onClick = { run++ }) { Icon(Icons.Rounded.Refresh, "إعادة الفحص") }
            }
        },
        bottomBar = {
            if (chosen.isNotEmpty() && r != null) {
                val all = (r.largest + r.duplicates.flatten()).distinctBy { it.location.key }.filter { it.location.key in chosen }
                BottomAppBar {
                    Text(
                        "${itemsLabel(all.size)} · ${formatSize(all.sumOf { it.size })}",
                        Modifier.weight(1f).padding(horizontal = 16.dp),
                    )
                    Button(
                        onClick = { vm.c.ops.delete(all, vm.c.settings.useTrash); chosen = emptySet(); run++ },
                        modifier = Modifier.padding(end = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("حذف المحدد")
                    }
                }
            }
        },
    ) { pad ->
        if (r == null) {
            Column(Modifier.padding(pad).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("جارٍ فحص الملفات… ${"%,d".format(scanned)}")
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = cardColor()) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("حسب النوع", style = MaterialTheme.typography.titleMedium)
                        val max = (r.byCategory.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
                        r.byCategory.entries.sortedByDescending { it.value }.forEach { (cat, bytes) ->
                            val (bg, fg) = com.personal.myfiles.ui.theme.categoryColors(cat)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cat.label, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(bg)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(bytes.toFloat() / max).background(fg))
                                }
                                Text(formatSize(bytes), Modifier.width(80.dp).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item { Text("أكبر الملفات", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(r.largest.take(30), key = { "L" + it.location.key }) { e ->
                SelectableRow(e, e.location.key in chosen, { chosen = if (e.location.key in chosen) chosen - e.location.key else chosen + e.location.key }) {
                    vm.openEntry(e, nav, ctx)
                }
            }
            if (r.duplicates.isNotEmpty()) {
                item {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("ملفات مكررة", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            chosen = chosen + r.duplicates.flatMap { g -> g.sortedBy { it.modified }.drop(1).map { it.location.key } }
                        }) { Text("تحديد النسخ الزائدة") }
                    }
                    val waste = r.duplicates.sumOf { it.first().size * (it.size - 1) }
                    Text("يمكن توفير ${formatSize(waste)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                r.duplicates.forEachIndexed { gi, group ->
                    item(key = "G$gi") {
                        Surface(shape = RoundedCornerShape(20.dp), color = cardColor()) {
                            Column(Modifier.padding(6.dp)) {
                                Text("${itemsLabel(group.size)} متطابقة · ${formatSize(group.first().size)} لكل نسخة",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp))
                                group.forEach { e ->
                                    SelectableRow(e, e.location.key in chosen, {
                                        chosen = if (e.location.key in chosen) chosen - e.location.key else chosen + e.location.key
                                    }, showPath = true) { vm.openEntry(e, nav, ctx) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableRow(e: FileEntry, checked: Boolean, onToggle: () -> Unit, showPath: Boolean = false, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onOpen, onLongClick = onToggle).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        FileThumb(e, 40.dp, corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (showPath) (File(e.location.path).parent ?: "").removePrefix(Storage.primaryPath).ifEmpty { "/" }
                else "${formatSize(e.size)} · ${formatDate(e.modified)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (!showPath) Text(formatSize(e.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
