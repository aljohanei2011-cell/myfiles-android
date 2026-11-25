@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.personal.myfiles.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.personal.myfiles.BuildConfig
import com.personal.myfiles.core.ConnType
import com.personal.myfiles.core.Connection
import com.personal.myfiles.core.FsRegistry
import com.personal.myfiles.ui.MainViewModel
import com.personal.myfiles.ui.theme.Accents
import com.personal.myfiles.ui.theme.cardColor
import kotlinx.coroutines.launch

/* ---------------- Network ---------------- */

@Composable
fun NetworkScreen(vm: MainViewModel, nav: NavController) {
    val conns by vm.c.connections.list.collectAsState()
    var editing by remember { mutableStateOf<Connection?>(null) }
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الشبكة") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("اتصال جديد") },
            )
        },
    ) { pad ->
        if (conns.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lan, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Text("لا توجد اتصالات بعد", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "أضف جهاز NAS أو كمبيوتر على شبكتك عبر SMB أو FTP أو SFTP، وتصفّح ملفاته كأنها على جوالك.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(conns, key = { it.id }) { c ->
                Surface(
                    onClick = { vm.openRemote(c, nav) },
                    shape = RoundedCornerShape(22.dp),
                    color = cardColor(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (c.type == ConnType.SMB) Icons.Rounded.Dns else Icons.Rounded.Cloud, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${c.type.label} · ${c.host}" + if (c.type == ConnType.SMB && c.share.isNotBlank()) " / ${c.share}" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuFor = c.id }) { Icon(Icons.Rounded.MoreVert, "خيارات") }
                            DropdownMenu(menuFor == c.id, { menuFor = null }) {
                                DropdownMenuItem(text = { Text("تعديل") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = { menuFor = null; editing = c })
                                DropdownMenuItem(text = { Text("حذف") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                    onClick = { menuFor = null; vm.c.registry.invalidate(c.id); vm.c.connections.remove(c.id) })
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        ConnectionDialog(editing, onDismiss = { creating = false; editing = null }) { c ->
            vm.c.registry.invalidate(c.id)
            vm.c.connections.upsert(c)
            creating = false; editing = null
        }
    }
}

@Composable
private fun ConnectionDialog(initial: Connection?, onDismiss: () -> Unit, onSave: (Connection) -> Unit) {
    var type by remember { mutableStateOf(initial?.type ?: ConnType.SMB) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: ConnType.SMB.defaultPort).toString()) }
    var share by remember { mutableStateOf(initial?.share ?: "") }
    var path by remember { mutableStateOf(initial?.path ?: "") }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var pass by remember { mutableStateOf(initial?.pass ?: "") }
    var domain by remember { mutableStateOf(initial?.domain ?: "") }
    var anon by remember { mutableStateOf(initial?.anonymous ?: false) }
    var showPass by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun build() = Connection(
        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
        name = name.ifBlank { host },
        type = type, host = host.trim(), port = port.toIntOrNull() ?: type.defaultPort,
        user = user.trim(), pass = pass, share = share.trim().trim('/'), path = path.trim(),
        domain = domain.trim(), anonymous = anon,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "اتصال جديد" else "تعديل الاتصال") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = {
                            if (port == type.defaultPort.toString()) port = t.defaultPort.toString()
                            type = t
                        }, label = { Text(t.label) })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(host, { host = it }, label = { Text("العنوان (IP)") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("المنفذ") }, singleLine = true,
                        modifier = Modifier.width(96.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (type == ConnType.SMB) {
                    OutlinedTextField(share, { share = it }, label = { Text("اسم المجلد المشترك (Share)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(path, { path = it }, label = { Text("مجلد البداية (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("/") })
                }
                if (type != ConnType.SFTP) {
                    Row(Modifier.clickable { anon = !anon }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(anon, { anon = it })
                        Text(if (type == ConnType.SMB) "دخول كضيف" else "دخول مجهول (anonymous)")
                    }
                }
                if (!anon || type == ConnType.SFTP) {
                    OutlinedTextField(user, { user = it }, label = { Text("اسم المستخدم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("كلمة المرور") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                            }
                        },
                    )
                    if (type == ConnType.SMB) OutlinedTextField(domain, { domain = it }, label = { Text("النطاق (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = host.isNotBlank() && !testing, onClick = {
                        testing = true; testResult = null
                        scope.launch {
                            val r = FsRegistry.test(build())
                            testResult = r.fold({ "✓ نجح الاتصال (${it} عنصر)" }, { "✗ ${it.message ?: it.javaClass.simpleName}" })
                            testing = false
                        }
                    }) { Text("اختبار الاتصال") }
                    Spacer(Modifier.width(10.dp))
                    if (testing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                testResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(enabled = host.isNotBlank(), onClick = { onSave(build()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/* ---------------- Settings ---------------- */

@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavController) {
    val s = vm.c.settings
    Scaffold(topBar = { BackTopBar("الإعدادات", nav) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup("المظهر") {
                Text("السمة", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOption(Icons.Rounded.BrightnessAuto, "تلقائي", s.themeMode == 0, Modifier.weight(1f)) { s.themeMode = 0 }
                    ThemeOption(Icons.Rounded.LightMode, "فاتح", s.themeMode == 1, Modifier.weight(1f)) { s.themeMode = 1 }
                    ThemeOption(Icons.Rounded.DarkMode, "داكن", s.themeMode == 2, Modifier.weight(1f)) { s.themeMode = 2 }
                }
                Text("لون التمييز", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Accents.forEachIndexed { i, a ->
                        val sel = s.accent == i && !s.dynamicColor
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(a.swatch)
                                    .border(3.dp, if (sel) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                    .clickable { s.accent = i; s.dynamicColor = false },
                                contentAlignment = Alignment.Center,
                            ) { if (sel) Icon(Icons.Rounded.Check, null, tint = androidx.compose.ui.graphics.Color.White) }
                            Spacer(Modifier.height(4.dp))
                            Text(a.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (Build.VERSION.SDK_INT >= 31) SwitchRow("ألوان النظام (Material You)", "تأخذ الألوان من خلفية جهازك", s.dynamicColor) { s.dynamicColor = it }
                SwitchRow("أسود حقيقي في الوضع الداكن", "مناسب لشاشات OLED ويوفّر البطارية", s.amoled) { s.amoled = it }
            }
            SettingsGroup("التصفح") {
                SwitchRow("إظهار الملفات المخفية", null, s.showHidden) { s.showHidden = it }
                SwitchRow("المجلدات أولًا", null, s.foldersFirst) { s.foldersFirst = it }
                SwitchRow("عرض شبكي افتراضيًا", null, s.gridView) { s.gridView = it }
            }
            SettingsGroup("الحذف") {
                SwitchRow("استخدام المهملات", "نقل الملفات المحذوفة للمهملات بدل حذفها مباشرة", s.useTrash) { s.useTrash = it }
                SwitchRow("تأكيد قبل الحذف", null, s.confirmDelete) { s.confirmDelete = it }
                Text("مدة الاحتفاظ في المهملات", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { d ->
                        FilterChip(selected = s.trashDays == d, onClick = { s.trashDays = d }, label = { Text("$d يومًا") })
                    }
                }
            }
            SettingsGroup("حول") {
                ListItem(
                    headlineContent = { Text("ملفاتي") },
                    supportingContent = { Text("الإصدار ${BuildConfig.VERSION_NAME} · للاستخدام الشخصي\nالخط: Readex Pro (رخصة OFL)") },
                    leadingContent = { Icon(Icons.Rounded.Info, null) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = cardColor()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = sub?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun ThemeOption(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
