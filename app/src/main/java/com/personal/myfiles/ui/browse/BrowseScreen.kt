@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.personal.myfiles.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.personal.myfiles.core.FileCategory
import com.personal.myfiles.core.FileEntry
import com.personal.myfiles.core.Location
import com.personal.myfiles.core.Storage
import com.personal.myfiles.ui.MainViewModel
import com.personal.myfiles.ui.PaneState
import com.personal.myfiles.ui.common.FileThumb
import com.personal.myfiles.ui.common.formatSize
import com.personal.myfiles.ui.common.itemsLabel
import com.personal.myfiles.ui.common.shareFiles
import com.personal.myfiles.ui.common.subtitle
import com.personal.myfiles.ui.theme.cardColor

@Composable
fun BrowseScreen(vm: MainViewModel, nav: NavController) {
    val width = LocalConfiguration.current.screenWidthDp
    val expanded = width >= 840
    val canDual = width >= 600
    if (!canDual && vm.dualPane) {
        LaunchedEffect(Unit) { vm.dualPane = false; vm.activePane = 0 }
    }
    Row(Modifier.fillMaxSize()) {
        if (expanded) LocationsSidebar(vm, nav, Modifier.width(260.dp).fillMaxHeight())
        BrowserPane(vm, vm.paneA, 0, nav, Modifier.weight(1f))
        if (vm.dualPane && canDual) {
            VerticalDivider()
            BrowserPane(vm, vm.paneB, 1, nav, Modifier.weight(1f))
        } else if (expanded && vm.details != null) {
            DetailsPanel(vm, nav, Modifier.width(320.dp).fillMaxHeight())
        }
    }
}

private val filters = listOf(
    null to "الكل",
    FileCategory.DOCUMENT to "مستندات",
    FileCategory.IMAGE to "صور",
    FileCategory.VIDEO to "فيديو",
    FileCategory.AUDIO to "صوت",
    FileCategory.ARCHIVE to "مضغوطة",
    FileCategory.APK to "تطبيقات",
)

@Composable
fun BrowserPane(vm: MainViewModel, pane: PaneState, index: Int, nav: NavController, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val s = vm.c.settings
    val isActive = !vm.dualPane || vm.activePane == index
    val visible = remember(pane.items, pane.filter, s.sortBy, s.sortDesc, s.foldersFirst, s.showHidden) { pane.visible() }
    val wide = LocalConfiguration.current.screenWidthDp >= 840

    if (isActive) {
        BackHandler(enabled = pane.selectionMode || pane.canGoBack) {
            if (pane.selectionMode) pane.clearSelection() else pane.back()
        }
    }

    fun onItemClick(e: FileEntry) {
        vm.activePane = index
        if (pane.selectionMode) pane.toggle(e)
        else if (e.isDir) pane.open(e.location)
        else vm.openEntry(e, nav, ctx, pane)
    }

    Scaffold(
        modifier = modifier
            .then(if (vm.dualPane) Modifier.padding(4.dp).border(
                BorderStroke(2.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(24.dp)
            ).clip(RoundedCornerShape(24.dp)) else Modifier),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (pane.selectionMode) SelectionTopBar(vm, pane)
            else PaneTopBar(vm, pane, index, nav)
        },
        bottomBar = {
            when {
                pane.selectionMode -> SelectionActions(vm, pane)
                vm.clipboard != null && isActive -> PasteBar(vm, pane)
            }
        },
        floatingActionButton = {
            if (!pane.selectionMode && vm.clipboard == null) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { menu = true },
                        icon = { Icon(Icons.Rounded.Add, null) },
                        text = { Text("جديد") },
                        expanded = !vm.dualPane,
                    )
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("مجلد جديد") }, leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                            onClick = { menu = false; vm.dialog = com.personal.myfiles.ui.DialogReq.NewFolder(pane) })
                        DropdownMenuItem(text = { Text("ملف نصي جديد") }, leadingIcon = { Icon(Icons.Rounded.NoteAdd, null) },
                            onClick = { menu = false; vm.dialog = com.personal.myfiles.ui.DialogReq.NewFile(pane) })
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Breadcrumbs(pane) { vm.activePane = index }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEach { (cat, label) ->
                    FilterChip(
                        selected = pane.filter == cat,
                        onClick = { pane.filter = cat },
                        label = { Text(label) },
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    pane.loading && pane.items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    pane.error != null -> ErrorState(pane.error ?: "", Modifier.align(Alignment.Center)) { pane.load() }
                    visible.isEmpty() -> EmptyState(Modifier.align(Alignment.Center))
                    s.gridView -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (wide) 140.dp else 108.dp),
                        contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visible, key = { it.location.key }) { e ->
                            FileGridItem(
                                e, selected = e.location.key in pane.selected,
                                onClick = { onItemClick(e) },
                                onLongClick = { vm.activePane = index; pane.toggle(e) },
                            )
                        }
                    }
                    else -> LazyColumn(
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    ) {
                        items(visible, key = { it.location.key }) { e ->
                            FileRow(
                                e,
                                selected = e.location.key in pane.selected,
                                selectionMode = pane.selectionMode,
                                favorite = s.isFavorite(e.location.path),
                                onClick = { onItemClick(e) },
                                onLongClick = { vm.activePane = index; pane.toggle(e) },
                                onMore = { vm.activePane = index; vm.dialog = com.personal.myfiles.ui.DialogReq.Actions(e, pane) },
                            )
                        }
                    }
                }
                if (pane.loading && pane.items.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun PaneTopBar(vm: MainViewModel, pane: PaneState, index: Int, nav: NavController) {
    val s = vm.c.settings
    var menu by remember { mutableStateOf(false) }
    val canDual = LocalConfiguration.current.screenWidthDp >= 600
    TopAppBar(
        navigationIcon = {
            if (pane.parent() != null) IconButton(onClick = { vm.activePane = index; pane.up() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "المجلد الأعلى")
            }
        },
        title = {
            Column {
                Text(pane.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    itemsLabel(pane.visible().size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Rounded.Search, "بحث") }
            IconButton(onClick = { s.gridView = !s.gridView }) {
                Icon(if (s.gridView) Icons.Rounded.ViewList else Icons.Rounded.GridView, "طريقة العرض")
            }
            if (canDual) IconButton(onClick = { vm.dualPane = !vm.dualPane; vm.activePane = 0 }) {
                Icon(Icons.Rounded.VerticalSplit, "لوحتان", tint = if (vm.dualPane) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "المزيد") }
                DropdownMenu(menu, { menu = false }) {
                    Text("الترتيب حسب", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("الاسم", "التاريخ", "الحجم", "النوع").forEachIndexed { i, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { RadioButton(selected = s.sortBy == i, onClick = null) },
                            onClick = { s.sortBy = i },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("ترتيب تنازلي") },
                        leadingIcon = { Checkbox(checked = s.sortDesc, onCheckedChange = null) },
                        onClick = { s.sortDesc = !s.sortDesc },
                    )
                    DropdownMenuItem(
                        text = { Text("المجلدات أولًا") },
                        leadingIcon = { Checkbox(checked = s.foldersFirst, onCheckedChange = null) },
                        onClick = { s.foldersFirst = !s.foldersFirst },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("إظهار الملفات المخفية") },
                        leadingIcon = { Checkbox(checked = s.showHidden, onCheckedChange = null) },
                        onClick = { s.showHidden = !s.showHidden },
                    )
                    val loc = pane.location
                    if (loc is Location.Local) {
                        DropdownMenuItem(
                            text = { Text(if (s.isFavorite(loc.path)) "إزالة من المفضلة" else "إضافة المجلد للمفضلة") },
                            leadingIcon = { Icon(if (s.isFavorite(loc.path)) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
                            onClick = { menu = false; s.toggleFavorite(loc.path) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("تحديث") },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                        onClick = { menu = false; pane.load() },
                    )
                    DropdownMenuItem(
                        text = { Text("الإعدادات") },
                        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                        onClick = { menu = false; nav.navigate("settings") },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun SelectionTopBar(vm: MainViewModel, pane: PaneState) {
    val ctx = LocalContext.current
    val sel = pane.selectedEntries()
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = { IconButton(onClick = { pane.clearSelection() }) { Icon(Icons.Rounded.Close, "إلغاء التحديد") } },
        title = {
            Column {
                Text("${sel.size} محدد")
                val bytes = sel.filter { !it.isDir }.sumOf { it.size }
                if (bytes > 0) Text(formatSize(bytes), style = MaterialTheme.typography.bodySmall)
            }
        },
        actions = {
            IconButton(onClick = { pane.selectAll() }) { Icon(Icons.Rounded.SelectAll, "تحديد الكل") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "المزيد") }
                DropdownMenu(menu, { menu = false }) {
                    if (sel.size == 1) {
                        DropdownMenuItem(text = { Text("إعادة التسمية") }, leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                            onClick = { menu = false; vm.dialog = com.personal.myfiles.ui.DialogReq.Rename(sel[0], pane) })
                        DropdownMenuItem(text = { Text("التفاصيل") }, leadingIcon = { Icon(Icons.Rounded.Info, null) },
                            onClick = { menu = false; vm.dialog = com.personal.myfiles.ui.DialogReq.Details(sel[0]) })
                    }
                    if (vm.dualPane) {
                        DropdownMenuItem(text = { Text("نسخ إلى اللوحة الأخرى") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                            onClick = { menu = false; vm.c.ops.copy(sel, vm.other(pane).location, false); pane.clearSelection() })
                        DropdownMenuItem(text = { Text("نقل إلى اللوحة الأخرى") }, leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) },
                            onClick = { menu = false; vm.c.ops.copy(sel, vm.other(pane).location, true); pane.clearSelection() })
                    }
                    if (sel.all { it.isLocal }) {
                        DropdownMenuItem(text = { Text("إضافة للمفضلة") }, leadingIcon = { Icon(Icons.Rounded.StarBorder, null) },
                            onClick = {
                                menu = false
                                sel.forEach { if (!vm.c.settings.isFavorite(it.location.path)) vm.c.settings.toggleFavorite(it.location.path) }
                                pane.clearSelection()
                            })
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun SelectionActions(vm: MainViewModel, pane: PaneState) {
    val ctx = LocalContext.current
    val sel = pane.selectedEntries()
    val local = sel.all { it.isLocal }
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionButton(Icons.Rounded.ContentCopy, "نسخ") { vm.copyToClipboard(sel, false, ctx); pane.clearSelection() }
            ActionButton(Icons.Rounded.ContentCut, "نقل") { vm.copyToClipboard(sel, true, ctx); pane.clearSelection() }
            if (local) ActionButton(Icons.Rounded.Share, "مشاركة") {
                shareFiles(ctx, sel.filter { !it.isDir }.mapNotNull { it.file })
            }
            if (local) ActionButton(Icons.Rounded.Archive, "ضغط") {
                val dir = (pane.location as Location.Local).path
                vm.dialog = com.personal.myfiles.ui.DialogReq.Compress(sel, dir, pane)
            }
            ActionButton(Icons.Rounded.Delete, "حذف", danger = true) { vm.requestDelete(sel, pane) }
        }
    }
}

@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun PasteBar(vm: MainViewModel, pane: PaneState) {
    val clip = vm.clipboard ?: return
    Surface(color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (clip.cut) Icons.Rounded.ContentCut else Icons.Rounded.ContentCopy, null)
            Spacer(Modifier.width(12.dp))
            Text(
                (if (clip.cut) "نقل " else "نسخ ") + itemsLabel(clip.items.size),
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { vm.clipboard = null }) { Text("إلغاء", color = MaterialTheme.colorScheme.inversePrimary) }
            Button(onClick = { vm.paste(pane) }) {
                Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("لصق هنا")
            }
        }
    }
}

@Composable
private fun Breadcrumbs(pane: PaneState, onTouch: () -> Unit) {
    val crumbs = pane.crumbs()
    val state = rememberLazyListState()
    LaunchedEffect(crumbs.size) { if (crumbs.isNotEmpty()) state.scrollToItem(crumbs.lastIndex) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
        itemsIndexed(crumbs) { i, (label, loc) ->
            val last = i == crumbs.lastIndex
            if (i > 0) Icon(Icons.Rounded.ChevronLeft, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            Surface(
                onClick = { onTouch(); if (!last) pane.open(loc) },
                shape = RoundedCornerShape(16.dp),
                color = if (last) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (last) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = if (last) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun FileRow(
    e: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    favorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(12.dp))
        }
        FileThumb(e, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (favorite) Icon(Icons.Rounded.Star, null, Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
            Text(e.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (onMore != null && !selectionMode) {
            IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, "خيارات", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun FileGridItem(e: FileEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else cardColor(),
        border = border,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(8.dp)) {
            Box {
                BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.15f)) {
                    FileThumb(e, size = maxWidth, corner = 14.dp, iconScale = 0.38f)
                }
                if (selected) Box(
                    Modifier.padding(6.dp).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            }
            Spacer(Modifier.height(8.dp))
            Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                if (e.isDir) itemsLabel(e.childCount) else formatSize(e.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(16.dp))
        Text("المجلد فارغ", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ErrorState(msg: String, modifier: Modifier, retry: () -> Unit) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(msg, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = retry) { Text("إعادة المحاولة") }
    }
}

@Composable
private fun LocationsSidebar(vm: MainViewModel, nav: NavController, modifier: Modifier) {
    val volumes = remember(vm.tick) { Storage.volumes() }
    val conns by vm.c.connections.list.collectAsState()
    val current = vm.active.location
    LazyColumn(modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
        item { SideHeader("التخزين") }
        items(volumes) { v ->
            SideItem(
                icon = if (v.primary) Icons.Rounded.PhoneAndroid else if (v.isUsb) Icons.Rounded.Usb else Icons.Rounded.SdCard,
                label = v.name,
                sub = "${formatSize(v.free)} متاحة",
                progress = if (v.total > 0) v.used.toFloat() / v.total else null,
                selected = current is Location.Local && Storage.volumeRootOf(current.path) == v.path && current.path == v.path,
            ) { vm.active.open(Location.Local(v.path)) }
        }
        item { SideHeader("الوصول السريع") }
        items(Storage.quickFolders()) { (label, path) ->
            SideItem(Icons.Rounded.Folder, label, selected = current.path == path) { vm.active.open(Location.Local(path)) }
        }
        val favs = vm.c.settings.favorites.filter { java.io.File(it).isDirectory }
        if (favs.isNotEmpty()) {
            item { SideHeader("المفضلة") }
            items(favs) { p ->
                SideItem(Icons.Rounded.Star, java.io.File(p).name, selected = current.path == p) { vm.active.open(Location.Local(p)) }
            }
        }
        item { SideHeader("الشبكة") }
        items(conns) { c ->
            SideItem(Icons.Rounded.Dns, c.name, sub = c.type.label, selected = current is Location.Remote && current.connId == c.id) {
                vm.active.open(Location.Remote(c.id, c.rootPath))
            }
        }
        item {
            SideItem(Icons.Rounded.Add, "إضافة اتصال", selected = false) { nav.navigate("network") }
        }
    }
}

@Composable
private fun SideHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SideItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sub: String? = null,
    progress: Float? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress != null) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), drawStopIndicator = {})
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailsPanel(vm: MainViewModel, nav: NavController, modifier: Modifier) {
    val e = vm.details ?: return
    Surface(modifier.padding(8.dp), shape = RoundedCornerShape(24.dp), color = cardColor()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("التفاصيل", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.details = null }) { Icon(Icons.Rounded.Close, "إغلاق") }
            }
            DetailsContent(vm, e, large = true)
        }
    }
}
