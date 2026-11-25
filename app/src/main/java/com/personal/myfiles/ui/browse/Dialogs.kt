@file:OptIn(ExperimentalMaterial3Api::class)

package com.personal.myfiles.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.personal.myfiles.core.FileEntry
import com.personal.myfiles.core.Location
import com.personal.myfiles.core.joinPath
import com.personal.myfiles.ui.DialogReq
import com.personal.myfiles.ui.MainViewModel
import com.personal.myfiles.ui.common.FileThumb
import com.personal.myfiles.ui.common.formatFullDate
import com.personal.myfiles.ui.common.formatSize
import com.personal.myfiles.ui.common.itemsLabel
import com.personal.myfiles.ui.common.openExternal
import com.personal.myfiles.ui.common.shareFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DialogHost(vm: MainViewModel, nav: NavController) {
    val req = vm.dialog ?: return
    val dismiss = { vm.dialog = null }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    when (req) {
        is DialogReq.Rename -> NameDialog(
            title = "إعادة التسمية",
            initial = req.entry.name,
            selectBase = !req.entry.isDir,
            confirm = "حفظ",
            onDismiss = dismiss,
        ) { name ->
            scope.launch {
                runCatching { vm.c.registry.fs(req.entry.location).rename(req.entry.location.path, name) }
                    .onFailure { vm.c.ops.messages.tryEmit(it.message ?: "تعذّرت إعادة التسمية") }
                req.pane?.clearSelection()
                req.pane?.load(silent = true)
                vm.c.ops.changes.tryEmit(Unit)
            }
            dismiss()
        }

        is DialogReq.NewFolder -> NameDialog("مجلد جديد", "مجلد جديد", false, "إنشاء", dismiss) { name ->
            scope.launch {
                val loc = req.pane.location
                runCatching { vm.c.registry.fs(loc).mkdir(joinPath(loc.path, name)) }
                    .onFailure { vm.c.ops.messages.tryEmit(it.message ?: "تعذّر الإنشاء") }
                req.pane.load(silent = true)
            }
            dismiss()
        }

        is DialogReq.NewFile -> NameDialog("ملف نصي جديد", "ملاحظة.txt", true, "إنشاء", dismiss) { name ->
            scope.launch {
                val loc = req.pane.location
                runCatching { vm.c.registry.fs(loc).createFile(joinPath(loc.path, name)) }
                    .onFailure { vm.c.ops.messages.tryEmit(it.message ?: "تعذّر الإنشاء") }
                req.pane.load(silent = true)
            }
            dismiss()
        }

        is DialogReq.Compress -> NameDialog(
            "ضغط إلى ZIP",
            (if (req.entries.size == 1) req.entries[0].name.substringBeforeLast('.') else "أرشيف") + ".zip",
            true, "ضغط", dismiss,
        ) { name ->
            vm.c.ops.compress(req.entries, req.destDir, name)
            req.pane?.clearSelection()
            dismiss()
        }

        is DialogReq.Delete -> {
            val canTrash = vm.c.settings.useTrash && req.entries.all { it.isLocal }
            AlertDialog(
                onDismissRequest = dismiss,
                icon = { Icon(Icons.Rounded.Delete, null) },
                title = { Text(if (canTrash) "نقل إلى المهملات؟" else "حذف نهائي؟") },
                text = {
                    Text(
                        (if (req.entries.size == 1) "«${req.entries[0].name}»" else itemsLabel(req.entries.size)) +
                            if (canTrash) "\nيمكنك استعادتها خلال ${vm.c.settings.trashDays} يومًا." else "\nلا يمكن التراجع عن هذا الإجراء."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { vm.c.ops.delete(req.entries, canTrash); req.pane?.clearSelection(); dismiss() },
                        colors = if (canTrash) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError
                        ),
                    ) { Text(if (canTrash) "نقل للمهملات" else "حذف") }
                },
                dismissButton = {
                    Row {
                        if (canTrash) TextButton(onClick = { vm.c.ops.delete(req.entries, false); req.pane?.clearSelection(); dismiss() }) {
                            Text("حذف نهائي", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = dismiss) { Text("إلغاء") }
                    }
                },
            )
        }

        is DialogReq.Extract -> AlertDialog(
            onDismissRequest = dismiss,
            icon = { Icon(Icons.Rounded.Unarchive, null) },
            title = { Text("فك الضغط") },
            text = { Text("«${req.file.name}»") },
            confirmButton = {
                Button(onClick = {
                    val dest = File(req.file.parentFile, req.file.nameWithoutExtension)
                    vm.c.ops.extract(req.file, dest); dismiss()
                }) { Text("إلى مجلد جديد") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.c.ops.extract(req.file, req.file.parentFile ?: req.file); dismiss() }) { Text("هنا") }
                    TextButton(onClick = { openExternal(ctx, req.file); dismiss() }) { Text("فتح بتطبيق آخر") }
                }
            },
        )

        is DialogReq.Details -> {
            val wide = LocalConfiguration.current.screenWidthDp >= 840
            if (wide && nav.currentDestination?.route == "browse") {
                LaunchedEffect(req) { vm.details = req.entry; vm.dialog = null }
            } else {
                ModalBottomSheet(onDismissRequest = dismiss) {
                    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) { DetailsContent(vm, req.entry, large = false) }
                }
            }
        }

        is DialogReq.Actions -> ActionsSheet(vm, nav, req.entry, req.pane, dismiss)
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    selectBase: Boolean,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val end = if (selectBase && initial.contains('.')) initial.lastIndexOf('.') else initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, end))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val invalid = value.text.isBlank() || value.text.contains('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, singleLine = true,
                isError = value.text.contains('/'),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(value.text.trim()) }, enabled = !invalid) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
fun DetailsContent(vm: MainViewModel, e: FileEntry, large: Boolean) {
    val folderSize by produceState<Long?>(null, e) {
        val f = e.file
        value = if (e.isDir && f != null) withContext(Dispatchers.IO) {
            f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else if (!e.isDir) e.size else null
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FileThumb(e, if (large) 180.dp else 96.dp, corner = 24.dp, iconScale = 0.4f)
        }
        Text(e.name, style = MaterialTheme.typography.titleMedium)
        Text(e.category.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        DetailRow("الحجم", folderSize?.let { formatSize(it) } ?: "جارٍ الحساب…")
        if (e.isDir && e.childCount >= 0) DetailRow("المحتوى", itemsLabel(e.childCount))
        DetailRow("آخر تعديل", formatFullDate(e.modified))
        val pathText = when (val l = e.location) {
            is Location.Local -> l.path
            is Location.Remote -> (vm.c.connections.get(l.connId)?.name ?: "") + ":" + l.path
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text("المسار", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pathText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionsSheet(vm: MainViewModel, nav: NavController, e: FileEntry, pane: com.personal.myfiles.ui.PaneState?, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val s = vm.c.settings
    ModalBottomSheet(onDismissRequest = dismiss) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumb(e, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(e.category.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SheetItem(Icons.Rounded.FileOpen, "فتح") { dismiss(); vm.openEntry(e, nav, ctx, pane) }
            val f = e.file
            if (f != null && !e.isDir) {
                SheetItem(Icons.Rounded.OpenInNew, "فتح باستخدام…") { dismiss(); openExternal(ctx, f) }
                SheetItem(Icons.Rounded.Share, "مشاركة") { dismiss(); shareFiles(ctx, listOf(f)) }
            }
            if (pane == null && f != null) {
                SheetItem(Icons.Rounded.FolderOpen, "فتح موقع الملف") {
                    dismiss(); f.parentFile?.let { vm.openInBrowser(Location.Local(it.path), nav) }
                }
            }
            SheetItem(Icons.Rounded.ContentCopy, "نسخ") { dismiss(); vm.copyToClipboard(listOf(e), false, ctx) }
            SheetItem(Icons.Rounded.ContentCut, "نقل") { dismiss(); vm.copyToClipboard(listOf(e), true, ctx) }
            SheetItem(Icons.Rounded.DriveFileRenameOutline, "إعادة التسمية") { vm.dialog = DialogReq.Rename(e, pane) }
            if (f != null) {
                SheetItem(Icons.Rounded.Archive, "ضغط") { vm.dialog = DialogReq.Compress(listOf(e), f.parent ?: f.path, pane) }
                if (e.ext == "zip") SheetItem(Icons.Rounded.Unarchive, "فك الضغط") { vm.dialog = DialogReq.Extract(f) }
                val fav = s.isFavorite(f.path)
                SheetItem(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) "إزالة من المفضلة" else "إضافة للمفضلة") {
                    dismiss(); s.toggleFavorite(f.path)
                }
            }
            SheetItem(Icons.Rounded.Info, "التفاصيل") { vm.dialog = DialogReq.Details(e) }
            SheetItem(Icons.Rounded.Delete, "حذف", danger = true) { dismiss(); vm.requestDelete(listOf(e), pane) }
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
