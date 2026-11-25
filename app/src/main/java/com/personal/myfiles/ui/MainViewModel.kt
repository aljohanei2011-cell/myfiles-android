package com.personal.myfiles.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.personal.myfiles.MyFilesApp
import com.personal.myfiles.core.AppContainer
import com.personal.myfiles.core.Connection
import com.personal.myfiles.core.FileCategory
import com.personal.myfiles.core.FileEntry
import com.personal.myfiles.core.Location
import com.personal.myfiles.core.Storage
import com.personal.myfiles.core.parentPath
import com.personal.myfiles.core.sortEntries
import com.personal.myfiles.core.withPath
import com.personal.myfiles.ui.common.openExternal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class PaneState(private val scope: CoroutineScope, private val c: AppContainer) {
    var location: Location by mutableStateOf(Location.Local(Storage.primaryPath))
        private set
    var items by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
    var filter by mutableStateOf<FileCategory?>(null)

    private val history = ArrayDeque<Location>()
    private var job: Job? = null

    val selectionMode: Boolean get() = selected.isNotEmpty()
    val canGoBack: Boolean get() = history.isNotEmpty()

    fun visible(): List<FileEntry> = sortEntries(
        items, c.settings.sortBy, c.settings.sortDesc, c.settings.foldersFirst, c.settings.showHidden, filter
    )

    fun open(loc: Location, addHistory: Boolean = true) {
        if (addHistory && loc != location) {
            history.addLast(location)
            if (history.size > 60) history.removeFirst()
        }
        location = loc
        selected = emptySet()
        filter = null
        load()
    }

    fun load(silent: Boolean = false) {
        job?.cancel()
        val loc = location
        job = scope.launch {
            if (!silent) loading = true
            error = null
            try {
                items = c.registry.fs(loc).list(loc.path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "تعذّر فتح المجلد"
                if (!silent) items = emptyList()
            } finally {
                if (location == loc) loading = false
            }
        }
    }

    fun back(): Boolean {
        val prev = history.removeLastOrNull() ?: return false
        location = prev
        selected = emptySet()
        filter = null
        load()
        return true
    }

    fun parent(): Location? {
        val loc = location
        return when (loc) {
            is Location.Local -> if (Storage.isVolumeRoot(loc.path)) null else parentPath(loc.path)?.let { Location.Local(it) }
            is Location.Remote -> {
                val root = c.connections.get(loc.connId)?.rootPath ?: "/"
                if (loc.path.trimEnd('/') == root.trimEnd('/') || loc.path == "/") null
                else parentPath(loc.path)?.let { loc.withPath(it) }
            }
        }
    }

    fun up() {
        parent()?.let { open(it) }
    }

    fun toggle(e: FileEntry) {
        val k = e.location.key
        selected = if (k in selected) selected - k else selected + k
    }

    fun selectAll() {
        selected = visible().map { it.location.key }.toSet()
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun selectedEntries(): List<FileEntry> = items.filter { it.location.key in selected }

    /** Breadcrumb segments: label to location. */
    fun crumbs(): List<Pair<String, Location>> {
        val loc = location
        val out = ArrayList<Pair<String, Location>>()
        when (loc) {
            is Location.Local -> {
                val root = Storage.volumeRootOf(loc.path)
                out += Storage.volumeNameOf(loc.path) to Location.Local(root)
                var acc = root
                loc.path.removePrefix(root).split('/').filter { it.isNotEmpty() }.forEach {
                    acc = "$acc/$it"
                    out += it to Location.Local(acc)
                }
            }
            is Location.Remote -> {
                val conn = c.connections.get(loc.connId)
                val root = conn?.rootPath ?: "/"
                out += (conn?.name ?: "الخادم") to loc.withPath(root)
                var acc = root.trimEnd('/')
                loc.path.removePrefix(root.trimEnd('/')).split('/').filter { it.isNotEmpty() }.forEach {
                    acc = "$acc/$it"
                    out += it to loc.withPath(acc)
                }
            }
        }
        return out
    }

    fun title(): String = crumbs().lastOrNull()?.first ?: ""
}

data class Clipboard(val items: List<FileEntry>, val cut: Boolean)

enum class ViewerKind { IMAGE, MEDIA, TEXT, PDF }

data class ViewerRequest(val file: File, val kind: ViewerKind, val siblings: List<File> = emptyList())

sealed interface DialogReq {
    data class Rename(val entry: FileEntry, val pane: PaneState?) : DialogReq
    data class NewFolder(val pane: PaneState) : DialogReq
    data class NewFile(val pane: PaneState) : DialogReq
    data class Delete(val entries: List<FileEntry>, val pane: PaneState?) : DialogReq
    data class Details(val entry: FileEntry) : DialogReq
    data class Extract(val file: File) : DialogReq
    data class Compress(val entries: List<FileEntry>, val destDir: String, val pane: PaneState?) : DialogReq
    data class Actions(val entry: FileEntry, val pane: PaneState?) : DialogReq
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val c: AppContainer = (app as MyFilesApp).container
    val paneA = PaneState(viewModelScope, c)
    val paneB = PaneState(viewModelScope, c)

    var activePane by mutableIntStateOf(0)
    var dualPane by mutableStateOf(false)
    var clipboard by mutableStateOf<Clipboard?>(null)
    var viewer by mutableStateOf<ViewerRequest?>(null)
    var dialog by mutableStateOf<DialogReq?>(null)
    var details by mutableStateOf<FileEntry?>(null)
    var tick by mutableIntStateOf(0)
        private set

    val active: PaneState get() = if (dualPane && activePane == 1) paneB else paneA

    fun other(p: PaneState): PaneState = if (p === paneA) paneB else paneA

    init {
        paneA.load()
        paneB.load()
        viewModelScope.launch {
            c.ops.changes.collect {
                paneA.load(silent = true)
                if (dualPane) paneB.load(silent = true)
                tick++
            }
        }
    }

    fun onResume() {
        paneA.load(silent = true)
        if (dualPane) paneB.load(silent = true)
        tick++
    }

    fun openInBrowser(loc: Location, nav: NavController) {
        active.open(loc)
        if (nav.currentDestination?.route != "browse") {
            nav.navigate("browse") {
                launchSingleTop = true
                popUpTo("home")
            }
        }
    }

    fun openRemote(conn: Connection, nav: NavController) {
        openInBrowser(Location.Remote(conn.id, conn.rootPath), nav)
    }

    fun openEntry(entry: FileEntry, nav: NavController, ctx: Context, pane: PaneState? = null) {
        if (entry.isDir) {
            if (pane != null) pane.open(entry.location) else openInBrowser(entry.location, nav)
            return
        }
        when (val loc = entry.location) {
            is Location.Local -> openLocal(File(loc.path), nav, ctx)
            is Location.Remote -> c.ops.fetch(entry) { f -> openLocal(f, nav, ctx) }
        }
    }

    fun openLocal(file: File, nav: NavController, ctx: Context) {
        val cat = FileCategory.of(file.name, false)
        val kind = when (cat) {
            FileCategory.IMAGE -> ViewerKind.IMAGE
            FileCategory.VIDEO, FileCategory.AUDIO -> ViewerKind.MEDIA
            FileCategory.TEXT -> ViewerKind.TEXT
            FileCategory.PDF -> ViewerKind.PDF
            FileCategory.ARCHIVE -> {
                if (file.extension.equals("zip", true)) dialog = DialogReq.Extract(file)
                else openExternal(ctx, file)
                return
            }
            FileCategory.APK -> { openExternal(ctx, file, chooser = false); return }
            else -> { openExternal(ctx, file); return }
        }
        val siblings = if (kind == ViewerKind.IMAGE) {
            file.parentFile?.listFiles()?.filter { FileCategory.of(it.name, false) == FileCategory.IMAGE }
                ?.sortedWith { a, b -> com.personal.myfiles.core.NaturalOrder.compare(a.name, b.name) } ?: listOf(file)
        } else emptyList()
        viewer = ViewerRequest(file, kind, siblings)
        nav.navigate("viewer")
    }

    fun copyToClipboard(entries: List<FileEntry>, cut: Boolean, ctx: Context) {
        if (entries.isEmpty()) return
        clipboard = Clipboard(entries, cut)
        Toast.makeText(ctx, if (cut) "جاهز للنقل — افتح الوجهة واضغط لصق" else "جاهز للنسخ — افتح الوجهة واضغط لصق", Toast.LENGTH_SHORT).show()
    }

    fun paste(pane: PaneState) {
        val clip = clipboard ?: return
        c.ops.copy(clip.items, pane.location, clip.cut)
        if (clip.cut) clipboard = null
    }

    fun requestDelete(entries: List<FileEntry>, pane: PaneState?) {
        if (entries.isEmpty()) return
        val canTrash = c.settings.useTrash && entries.all { it.isLocal }
        if (c.settings.confirmDelete || !canTrash) dialog = DialogReq.Delete(entries, pane)
        else {
            c.ops.delete(entries, toTrash = true)
            pane?.clearSelection()
        }
    }
}
