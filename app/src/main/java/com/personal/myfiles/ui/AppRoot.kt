@file:OptIn(ExperimentalMaterial3Api::class)

package com.personal.myfiles.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personal.myfiles.ui.browse.BrowseScreen
import com.personal.myfiles.ui.browse.DialogHost
import com.personal.myfiles.ui.home.HomeScreen
import com.personal.myfiles.ui.screens.AnalyzerScreen
import com.personal.myfiles.ui.screens.CategoryScreen
import com.personal.myfiles.ui.screens.NetworkScreen
import com.personal.myfiles.ui.screens.SearchScreen
import com.personal.myfiles.ui.screens.SettingsScreen
import com.personal.myfiles.ui.screens.TrashScreen
import com.personal.myfiles.ui.viewer.ViewerScreen

private data class Dest(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val dests = listOf(
    Dest("home", "الرئيسية", Icons.Rounded.Home, Icons.Rounded.Home),
    Dest("browse", "التصفح", Icons.Rounded.FolderOpen, Icons.Rounded.Folder),
    Dest("network", "الشبكة", Icons.Rounded.Lan, Icons.Rounded.Lan),
    Dest("trash", "المهملات", Icons.Rounded.DeleteOutline, Icons.Rounded.Delete),
)

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val showNav = route in dests.map { it.route }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.c.ops.messages.collect { snackbar.showSnackbar(it) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var first = true
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                if (!first) vm.onResume()
                first = false
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (showNav && wide) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                header = {
                    Spacer(Modifier.height(8.dp))
                    FloatingActionButton(
                        onClick = { nav.navigate("search") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    ) { Icon(Icons.Rounded.Search, "بحث") }
                    Spacer(Modifier.height(12.dp))
                },
            ) {
                dests.forEach { d ->
                    NavigationRailItem(
                        selected = route == d.route,
                        onClick = { nav.goTab(d.route) },
                        icon = { Icon(if (route == d.route) d.selectedIcon else d.icon, null) },
                        label = { Text(d.label) },
                    )
                }
                Spacer(Modifier.weight(1f))
                NavigationRailItem(
                    selected = false,
                    onClick = { nav.navigate("settings") },
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text("الإعدادات") },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showNav && !wide) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        dests.forEach { d ->
                            NavigationBarItem(
                                selected = route == d.route,
                                onClick = { nav.goTab(d.route) },
                                icon = { Icon(if (route == d.route) d.selectedIcon else d.icon, null) },
                                label = { Text(d.label) },
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
                NavHost(nav, startDestination = "home") {
                    composable("home") { HomeScreen(vm, nav) }
                    composable("browse") { BrowseScreen(vm, nav) }
                    composable("network") { NetworkScreen(vm, nav) }
                    composable("trash") { TrashScreen(vm, nav) }
                    composable("settings") { SettingsScreen(vm, nav) }
                    composable("analyzer") { AnalyzerScreen(vm, nav) }
                    composable("search") { SearchScreen(vm, nav) }
                    composable("category/{key}") { b -> CategoryScreen(vm, nav, b.arguments?.getString("key") ?: "recent") }
                    composable("viewer") { ViewerScreen(vm, nav) }
                }
                OperationsPanel(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = if (route == "browse") 88.dp else 12.dp))
            }
        }
    }
    DialogHost(vm, nav)
}

@Composable
private fun OperationsPanel(vm: MainViewModel, modifier: Modifier) {
    val ops by vm.c.ops.ops.collectAsState()
    AnimatedVisibility(ops.isNotEmpty(), modifier = modifier.widthIn(max = 460.dp).padding(horizontal = 12.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ops.takeLast(3).forEach { op ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(op.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = when {
                                op.error != null -> op.error
                                op.finished -> "اكتملت"
                                op.total > 0 -> "${(op.fraction * 100).toInt()}% · ${op.current}"
                                else -> op.current.ifEmpty { "جارٍ التحضير…" }
                            }
                            Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            if (!op.finished) {
                                if (op.total > 0) LinearProgressIndicator(progress = { op.fraction }, modifier = Modifier.fillMaxWidth())
                                else LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                        if (!op.finished) {
                            IconButton(onClick = { vm.c.ops.cancel(op.id) }) { Icon(Icons.Rounded.Close, "إلغاء") }
                        } else {
                            Icon(if (op.error == null) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- Permission gate ---------------- */

private fun hasStorageAccess(ctx: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasStorageAccess(ctx)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = hasStorageAccess(ctx) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasStorageAccess(ctx)
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    if (granted) {
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) runCatching { notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        content()
        return
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(24.dp))
            Text("مرحبًا في ملفاتي", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Text(
                "ليعرض التطبيق ملفاتك ويديرها يحتاج إذن «الوصول إلى كل الملفات». يبقى كل شيء على جهازك.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 30) {
                        try {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
                            )
                        } catch (e: Exception) {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    } else {
                        legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }
                },
                modifier = Modifier.height(52.dp).fillMaxWidth(0.8f),
            ) { Text("منح الإذن", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
