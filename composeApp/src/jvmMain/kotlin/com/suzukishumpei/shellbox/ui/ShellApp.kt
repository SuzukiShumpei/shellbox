package com.suzukishumpei.shellbox.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ripple
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.suzukishumpei.shellbox.domain.ScriptEntry
import com.suzukishumpei.shellbox.runtime.LogStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Window

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    parentWindow: Window?,
    onExitApplication: () -> Unit,
    viewModel: ShellViewModel,
) {
    // Release DMG では ProGuard により viewModel() 経由の Factory デフォルト実装が壊れるため、
    // Compose の viewModel() は使わず 1 ウィンドウ 1 インスタンスで保持する（[viewModel] は main で生成）。
    ShellApp(viewModel, parentWindow, onExitApplication)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellApp(
    vm: ShellViewModel,
    parentWindow: Window?,
    onExitApplication: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val scripts by vm.scripts.collectAsState()
    val scanError by vm.scanError.collectAsState()
    val route by vm.route.collectAsState()
    val runDialog by vm.runDialog.collectAsState()
    val scope = rememberCoroutineScope()
    val pick: suspend () -> String? = remember(parentWindow) {
        { pickDirectory(parentWindow) }
    }
    val pickScript: suspend () -> String? = remember(parentWindow) {
        { pickScriptFile(parentWindow) }
    }
    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Shell Box",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (settings.projectRootPath != null) {
                        TextButton(onClick = { vm.refreshScan() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "スクリプト一覧を再読み込み",
                            )
                        }
                    }
                    TextButton(
                        onClick = onExitApplication,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = "終了",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when (val r = route) {
                ShellRoute.List -> {
                    if (settings.projectRootPath == null) {
                        ProjectRootEmpty(onChoose = {
                            scope.launch {
                                val p = pick()
                                if (p != null) vm.setProjectRoot(p)
                            }
                        })
                    } else {
                        val projectRoot = checkNotNull(settings.projectRootPath)
                        val changeProject: () -> Unit = {
                            scope.launch {
                                val p = pick()
                                if (p != null) vm.setProjectRoot(p)
                            }
                        }
                        CompactProjectRootRow(
                            projectRootPath = projectRoot,
                            onChangeProject = changeProject,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        scanError?.let { err ->
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        val scriptsAfterCategory =
                            remember(scripts, settings.visibleScriptCategories) {
                                val f = settings.visibleScriptCategories
                                if (f.isNullOrEmpty()) {
                                    scripts
                                } else {
                                    val allow = f.toSet()
                                    scripts.filter { it.category in allow }
                                }
                            }
                        val allCategories = remember(scripts) {
                            scripts.map { it.category }.distinct().sorted()
                        }
                        ScriptListScreen(
                            projectHasScripts = scripts.isNotEmpty(),
                            scripts = scriptsAfterCategory,
                            allCategories = allCategories,
                            visibleCategoryFilter = settings.visibleScriptCategories,
                            cwdById = settings.workingDirectoryByScriptId,
                            importedPathById = settings.importedScriptPathById,
                            onToggleCategory = { vm.toggleVisibleScriptCategory(it) },
                            onClearCategoryFilter = { vm.clearVisibleScriptCategoryFilter() },
                            onRequestImport = { showImportDialog = true },
                            onDetail = { vm.navigateToDetail(it) },
                            onRun = { vm.runScript(it, pick) },
                            onPickCwd = { id ->
                                scope.launch {
                                    vm.pickAndSetWorkingDirectory(id, pick)
                                }
                            },
                            onUseProjectRoot = { id ->
                                vm.useProjectRootAsWorkingDirectory(id)
                            },
                        )
                    }
                }

                is ShellRoute.Detail -> {
                    DetailScreen(
                        entry = r.entry,
                        cwd = settings.workingDirectoryByScriptId[r.entry.id],
                        importedPath = settings.importedScriptPathById[r.entry.id],
                        onBack = { vm.navigateToList() },
                        onRun = { vm.runScript(r.entry, pick) },
                        onPickCwd = {
                            scope.launch {
                                vm.pickAndSetWorkingDirectory(r.entry.id, pick)
                            }
                        },
                        onUseProjectRoot = {
                            vm.useProjectRootAsWorkingDirectory(r.entry.id)
                        },
                        onPickImportScript = {
                            scope.launch {
                                val f = pickScript() ?: return@launch
                                vm.setImportedScriptPath(r.entry.id, f)
                            }
                        },
                        onClearImportScript = {
                            vm.clearImportedScriptPath(r.entry.id)
                        },
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        ImportScriptDialog(
            onDismiss = { showImportDialog = false },
            onRegister = { segment, file, title, cwdOpt ->
                vm.registerImportedScript(segment, file, title, cwdOpt).exceptionOrNull()?.message
            },
            pickFile = pickScript,
            pickCwd = pick,
        )
    }

    runDialog?.let { state ->
        RunDialog(
            state = state,
            onDismiss = { vm.dismissRunDialog() },
            onStop = { vm.stopRun() },
            onSendStdin = { vm.sendStdinLine(it) },
        )
    }
}

@Composable
private fun CompactProjectRootRow(
    projectRootPath: String,
    onChangeProject: () -> Unit,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "プロジェクト",
            style = MaterialTheme.typography.labelSmall,
            color = secondary,
        )
        Text(
            text = projectRootPath,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onChangeProject,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Text("変更", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProjectRootEmpty(onChoose: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "clone した shellbox リポジトリのルート（scripts フォルダを含むパス）を指定してください。",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onChoose) {
            Text("フォルダを選択")
        }
    }
}

@Composable
private fun CategoryFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
) {
    val shape = FilterChipDefaults.shape
    val border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected)
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (selected) scheme.secondaryContainer else Color.Transparent
    val contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .clip(shape)
            .selectable(
                selected = selected,
                enabled = true,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = FilterChipDefaults.Height)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = { label() },
        )
    }
}

@Composable
private fun SearchBarToggleIconButton(
    searchBarVisible: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = if (searchBarVisible) "検索欄を閉じる" else "検索欄を表示",
            tint = if (searchBarVisible) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun ScriptListScreen(
    projectHasScripts: Boolean,
    scripts: List<ScriptEntry>,
    allCategories: List<String>,
    visibleCategoryFilter: List<String>?,
    cwdById: Map<String, String>,
    importedPathById: Map<String, String>,
    onToggleCategory: (String) -> Unit,
    onClearCategoryFilter: () -> Unit,
    onRequestImport: () -> Unit,
    onDetail: (ScriptEntry) -> Unit,
    onRun: (ScriptEntry) -> Unit,
    onPickCwd: (String) -> Unit,
    onUseProjectRoot: (String) -> Unit,
) {
    if (!projectHasScripts) {
        Text(
            text = "スクリプトがありません。`scripts/<カテゴリ>/<スクリプトID>/README.md` のように、" +
                    "scripts 直下にカテゴリフォルダを置き、その下にスクリプトIDディレクトリを置いてください。" +
                    "（`scripts/foo/` のように直下だけの配置は除外されます）",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchBarVisible by remember { mutableStateOf(false) }
    val filteredScripts = remember(scripts, searchQuery, importedPathById) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            scripts
        } else {
            scripts.filter { entry ->
                val pathMatch = importedPathById[entry.id]?.lowercase()?.contains(q) == true
                pathMatch ||
                        entry.id.lowercase().contains(q) ||
                        entry.readmeFullText.lowercase().contains(q)
            }
        }
    }
    val filterSet = visibleCategoryFilter?.toSet()
    Column(modifier = Modifier.fillMaxSize()) {
        if (allCategories.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBarToggleIconButton(
                    searchBarVisible = searchBarVisible,
                    onToggle = { searchBarVisible = !searchBarVisible },
                )
                val allOn = filterSet == null
                CategoryFilterChip(
                    selected = allOn,
                    onClick = onClearCategoryFilter,
                    label = { Text("すべて", style = MaterialTheme.typography.labelMedium) },
                )
                allCategories.forEach { cat ->
                    val selected = filterSet == null || cat in filterSet
                    CategoryFilterChip(
                        selected = selected,
                        onClick = { onToggleCategory(cat) },
                        label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onRequestImport) {
                    Text("外部スクリプトを登録", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else if (allCategories.isEmpty() && scripts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBarToggleIconButton(
                    searchBarVisible = searchBarVisible,
                    onToggle = { searchBarVisible = !searchBarVisible },
                )
            }
        }
        if (scripts.isEmpty()) {
            Text(
                text = "カテゴリの絞り込みで表示中のスクリプトが 0 件です。チップを調整してください。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (searchBarVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = {
                            Text(
                                text = "スクリプトID・README・外部 path",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        TextButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.heightIn(max = 52.dp),
                        ) {
                            Text("消去", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (filteredScripts.isEmpty()) {
                Text(
                    text = "一致するスクリプトがありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val listState = rememberLazyListState()
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredScripts, key = { it.id }) { entry ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        if (entry.isImported) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        text = "外部",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                },
                                                enabled = true,
                                            )
                                        }
                                    }
                                    Text(
                                        text = "カテゴリ: ${entry.category}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "ID: ${entry.id}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (entry.isImported) {
                                        val ext = importedPathById[entry.id]
                                        if (ext != null) {
                                            Text(
                                                text = "外部スクリプト: $ext",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else {
                                            Text(
                                                text = "外部スクリプト: 未設定（詳細で path を指定）",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    cwdById[entry.id]?.let { cwd ->
                                        Text(
                                            text = "実行 path: $cwd",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    } ?: Text(
                                        text = "実行 path: 未設定（実行時に選択）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = { onUseProjectRoot(entry.id) },
                                        ) {
                                            Text(
                                                text = "実行 path 指定なし",
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { onPickCwd(entry.id) },
                                        ) {
                                            Text(
                                                text = "実行 path を選択",
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { onDetail(entry) },
                                        ) {
                                            Text(
                                                text = "詳細",
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Button(
                                            onClick = { onRun(entry) },
                                        ) {
                                            Text(
                                                text = "実行",
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .offset(x = 16.dp),
                        adapter = rememberScrollbarAdapter(listState),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    entry: ScriptEntry,
    cwd: String?,
    importedPath: String?,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onPickCwd: () -> Unit,
    onUseProjectRoot: () -> Unit,
    onPickImportScript: () -> Unit,
    onClearImportScript: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("戻る") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(entry.title, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "カテゴリ: ${entry.category}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (entry.isImported) {
            Spacer(modifier = Modifier.height(4.dp))
            if (importedPath != null) {
                Text(
                    text = "外部スクリプト path: $importedPath",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = "外部スクリプト path: 未設定",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickImportScript) { Text("外部スクリプト path を選択") }
                OutlinedButton(onClick = onClearImportScript) { Text("クリア") }
            }
        }
        cwd?.let {
            Text(
                text = "実行 path: $it",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        } ?: Text(
            text = "実行 path: 未設定",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onUseProjectRoot) { Text("実行 path 指定なし") }
            OutlinedButton(onClick = onPickCwd) { Text("実行 path を選択") }
            Button(onClick = onRun) { Text("実行") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SelectionContainer(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = entry.readmeFullText.ifBlank { "（README.md が空か存在しません）" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun ImportScriptDialog(
    onDismiss: () -> Unit,
    onRegister: (String, String, String, String?) -> String?,
    pickFile: suspend () -> String?,
    pickCwd: suspend () -> String?,
) {
    var segment by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("外部スクリプトを登録") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = segment,
                    onValueChange = { segment = it; error = null },
                    label = { Text("スクリプトID（例: my-script-1）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("スクリプト表示タイトル") },
                    singleLine = true,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        filePath.ifBlank { "（スクリプト未選択）" },
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                val f = pickFile() ?: return@launch
                                filePath = f
                                error = null
                            }
                        },
                    ) { Text("ファイル選択") }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (cwd.isNullOrBlank()) "実行 path:（任意・未指定）" else "実行 path: $cwd",
                        modifier = Modifier.weight(1f),
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                val d = pickCwd() ?: return@launch
                                cwd = d
                                error = null
                            }
                        },
                    ) { Text("フォルダ選択") }
                    TextButton(
                        onClick = { cwd = null; error = null },
                        enabled = cwd != null,
                    ) { Text("クリア") }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (segment.isBlank()) {
                        error = "スクリプトID を入力してください"
                        return@TextButton
                    }
                    if (filePath.isBlank()) {
                        error = "スクリプトファイルを選択してください"
                        return@TextButton
                    }
                    val err = onRegister(segment, filePath, title, cwd)
                    if (err == null) {
                        onDismiss()
                    } else {
                        error = err
                    }
                },
            ) { Text("登録") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

private const val RunDialogAutoDismissMs = 700L

@Composable
private fun RunDialog(
    state: RunDialogState,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onSendStdin: (String) -> Unit,
) {
    var stdinLine by remember(state.scriptId) { mutableStateOf("") }

    LaunchedEffect(state.scriptId, state.exitCode) {
        if (state.exitCode != null && !state.isRunning) {
            delay(RunDialogAutoDismissMs)
            onDismiss()
        }
    }

    val scheme = MaterialTheme.colorScheme
    val logAnnotated = remember(state.logs, scheme) {
        buildAnnotatedString {
            state.logs.forEachIndexed { index, line ->
                if (index > 0) append('\n')
                val color = when (line.stream) {
                    LogStream.Out -> scheme.onSurface
                    LogStream.Err -> scheme.error
                }
                withStyle(
                    SpanStyle(
                        color = color,
                        fontFamily = FontFamily.Monospace,
                    ),
                ) {
                    append(line.text)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!state.isRunning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "ID: ${state.scriptId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = logAnnotated,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = stdinLine,
                        onValueChange = { stdinLine = it },
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { event ->
                                if (!state.isRunning) return@onPreviewKeyEvent false
                                if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) {
                                    return@onPreviewKeyEvent false
                                }
                                onSendStdin(stdinLine)
                                stdinLine = ""
                                true
                            },
                        singleLine = true,
                        label = { Text("標準入力（行）") },
                        placeholder = {
                            if (state.isRunning) {
                                Text(
                                    text = "空のまま送信 / Enter で改行を送る",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        enabled = state.isRunning,
                    )
                    Button(
                        onClick = {
                            onSendStdin(stdinLine)
                            stdinLine = ""
                        },
                        enabled = state.isRunning,
                    ) {
                        Text("送信")
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isRunning) {
                        Button(onClick = onStop) { Text("停止") }
                    }
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.isRunning,
                    ) {
                        Text("閉じる")
                    }
                }
            }
        }
    }
}
