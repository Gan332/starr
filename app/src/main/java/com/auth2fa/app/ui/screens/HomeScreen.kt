package com.auth2fa.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auth2fa.app.data.Account
import com.auth2fa.app.ui.components.AccountCard
import com.auth2fa.app.ui.theme.*
import com.auth2fa.app.viewmodel.AppUiState
import com.auth2fa.app.viewmodel.SortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: AppUiState,
    copiedAccountId: Long?,
    onSearch: (String) -> Unit,
    onCopyCode: (Long) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onEditAccount: (Account) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onSetSortMode: (SortMode) -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onToggleSelectId: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExportSelected: () -> Unit,
    onIncrementHotp: (Long) -> Unit,
    onBatchChangeCategory: (String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var deleteConfirmAccount by remember { mutableStateOf<Account?>(null) }
    var editTargetAccount by remember { mutableStateOf<Account?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showBatchMenu by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showBatchCategoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (uiState.isSelectMode) {
                TopAppBar(
                    title = { Text("已选 ${uiState.selectedIds.size} 项", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    navigationIcon = {
                        IconButton(onClick = { onToggleSelectMode(); onClearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "退出选择",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            if (uiState.selectedIds.size == uiState.accounts.size) onClearSelection() else onSelectAll()
                        }) { Text(if (uiState.selectedIds.size == uiState.accounts.size) "取消全选" else "全选") }
                        Box {
                            IconButton(onClick = { showBatchMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "批量操作",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = showBatchMenu, onDismissRequest = { showBatchMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("导出所选") }, onClick = { showBatchMenu = false; onExportSelected() },
                                    leadingIcon = { Icon(Icons.Default.Upload, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("更改分类") }, onClick = { showBatchMenu = false; showBatchCategoryDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Category, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除所选") }, onClick = { showBatchMenu = false; showDeleteSelectedConfirm = true },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            } else {
                TopAppBar(
                    title = { Text("Authenticator", fontWeight = FontWeight.Bold, fontSize = 26.sp) },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "设置",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectMode) {
                ExtendedFloatingActionButton(
                    onClick = onAddClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.extraLarge,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("添加") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!uiState.isSelectMode) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it; onSearch(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    placeholder = { Text("搜索账户...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = ""; onSearch("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        AssistChip(
                            onClick = { showSortMenu = true },
                            label = { Text(when (uiState.sortMode) { SortMode.NAME -> "按名称" SortMode.RECENT -> "最近添加" }, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Sort, null, Modifier.size(16.dp)) },
                            shape = MaterialTheme.shapes.small
                        )
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("按名称") }, onClick = { onSetSortMode(SortMode.NAME); showSortMenu = false },
                                leadingIcon = { if (uiState.sortMode == SortMode.NAME) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) })
                            DropdownMenuItem(text = { Text("最近添加") }, onClick = { onSetSortMode(SortMode.RECENT); showSortMenu = false },
                                leadingIcon = { if (uiState.sortMode == SortMode.RECENT) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) })
                        }
                    }

                    Box {
                        AssistChip(
                            onClick = { showCategoryMenu = true },
                            label = { Text(if (uiState.selectedCategory.isEmpty()) "全部分类" else uiState.selectedCategory, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(16.dp)) },
                            shape = MaterialTheme.shapes.small
                        )
                        DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                            DropdownMenuItem(text = { Text("全部分类") }, onClick = { onSelectCategory(""); showCategoryMenu = false },
                                leadingIcon = { if (uiState.selectedCategory.isEmpty()) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) })
                            uiState.allCategories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat) }, onClick = { onSelectCategory(cat); showCategoryMenu = false },
                                    leadingIcon = { if (uiState.selectedCategory == cat) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) })
                            }
                        }
                    }

                    AssistChip(
                        onClick = onToggleFavoritesFilter,
                        label = { Text(if (uiState.showFavoritesOnly) "仅收藏" else "全部", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                if (uiState.showFavoritesOnly) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                null,
                                Modifier.size(16.dp)
                            )
                        },
                        shape = MaterialTheme.shapes.small
                    )

                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onToggleSelectMode, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Checklist, contentDescription = "选择模式",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (uiState.accounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("\uD83D\uDD10", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("还没有账户", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("点击下方按钮添加您的第一个双重验证账户",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            codeEntry = uiState.codes[account.id],
                            isCopied = copiedAccountId == account.id,
                            isSelected = account.id in uiState.selectedIds,
                            isSelectMode = uiState.isSelectMode,
                            onCopy = { onCopyCode(account.id) },
                            onDelete = { deleteConfirmAccount = account },
                            onEdit = { editTargetAccount = account },
                            onToggleFavorite = { onToggleFavorite(account.id) },
                            onToggleSelect = { onToggleSelectId(account.id) },
                            onIncrementHotp = { onIncrementHotp(account.id) }
                        )
                    }
                }
            }

            if (deleteConfirmAccount != null) {
                AlertDialog(
                    onDismissRequest = { deleteConfirmAccount = null },
                    title = { Text("删除账户") },
                    text = { Text("确定要删除 ${deleteConfirmAccount?.issuer} 吗？它将移入回收站。") },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteConfirmAccount?.let { onDeleteAccount(it) }
                            deleteConfirmAccount = null
                        }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
                    },
                    dismissButton = { TextButton(onClick = { deleteConfirmAccount = null }) { Text("取消") } }
                )
            }

            if (showDeleteSelectedConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteSelectedConfirm = false },
                    title = { Text("批量删除") },
                    text = { Text("确定要删除已选的 ${uiState.selectedIds.size} 个账户吗？它们将移入回收站。") },
                    confirmButton = {
                        TextButton(onClick = { onDeleteSelected(); showDeleteSelectedConfirm = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text("取消") } }
                )
            }

            if (showBatchCategoryDialog) {
                AlertDialog(
                    onDismissRequest = { showBatchCategoryDialog = false },
                    title = { Text("批量更改分类") },
                    text = {
                        Column {
                            Text("为 ${uiState.selectedIds.size} 个账户选择新分类：")
                            Spacer(Modifier.height(12.dp))
                            TextButton(
                                onClick = { onBatchChangeCategory(""); showBatchCategoryDialog = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("无分类", modifier = Modifier.fillMaxWidth()) }
                            Divider()
                            uiState.allCategories.forEach { cat ->
                                TextButton(
                                    onClick = { onBatchChangeCategory(cat); showBatchCategoryDialog = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(cat, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showBatchCategoryDialog = false }) { Text("取消") }
                    }
                )
            }
        }
    }

    if (editTargetAccount != null) {
        EditAccountSheet(
            account = editTargetAccount!!,
            onDismiss = { editTargetAccount = null },
            onSave = { updated -> onEditAccount(updated); editTargetAccount = null }
        )
    }
}
