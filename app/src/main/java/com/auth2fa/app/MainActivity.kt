package com.auth2fa.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.auth2fa.app.biometric.BiometricHelper
import com.auth2fa.app.ui.screens.AddAccountSheet
import com.auth2fa.app.ui.screens.CategoryAdminScreen
import com.auth2fa.app.ui.screens.HomeScreen
import com.auth2fa.app.ui.screens.SettingsSheet
import com.auth2fa.app.ui.screens.TrashScreen
import com.auth2fa.app.ui.theme.Auth2FATheme
import com.auth2fa.app.viewmodel.MainViewModel
import com.auth2fa.app.viewmodel.ThemeMode
import java.io.BufferedReader

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var biometricHelper: BiometricHelper
    private var pendingExportJson: String = ""
    private var isLocked = false

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val json = contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(inputStream.reader()).use { it.readText() }
                } ?: ""
                viewModel.importFromJson(json) { count ->
                    when (count) {
                        -1 -> Toast.makeText(this, "导入失败：文件格式无效", Toast.LENGTH_SHORT).show()
                        0 -> Toast.makeText(this, "未导入任何账户", Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(this, "成功导入 $count 个账户", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingExportJson.toByteArray())
                }
                Toast.makeText(this, "备份已导出", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleNotification(true)
        } else {
            Toast.makeText(this, "需要通知权限才能显示验证码", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        biometricHelper = BiometricHelper(this)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("biometric_lock", false)) {
            isLocked = true
            biometricHelper.authenticate(
                onSuccess = { isLocked = false },
                onError = { _, _ -> finish() }
            )
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val copiedId by viewModel.copiedAccountId.collectAsState()
            var showAddSheet by remember { mutableStateOf(false) }
            var showSettingsSheet by remember { mutableStateOf(false) }
            var showTrashScreen by remember { mutableStateOf(false) }
            var showCategoryAdmin by remember { mutableStateOf(false) }

            Auth2FATheme(
                themeMode = if (uiState.isDarkTheme) ThemeMode.DARK else ThemeMode.LIGHT
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLocked) {
                        // Blank screen waiting for biometric
                    } else {
                        HomeScreen(
                            uiState = uiState,
                            copiedAccountId = copiedId,
                            onSearch = { viewModel.updateSearch(it) },
                            onCopyCode = { viewModel.copyCode(it) },
                            onDeleteAccount = { viewModel.trashAccount(it) },
                            onEditAccount = { viewModel.updateAccount(it) },
                            onAddClick = { showAddSheet = true },
                            onSettingsClick = { showSettingsSheet = true },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onSetSortMode = { viewModel.setSortMode(it) },
                            onSelectCategory = { viewModel.setSelectedCategory(it) },
                            onToggleFavoritesFilter = { viewModel.toggleFavoritesOnly() },
                            onToggleSelectMode = { viewModel.toggleSelectMode() },
                            onToggleSelectId = { viewModel.toggleSelectId(it) },
                            onSelectAll = { viewModel.selectAll() },
                            onClearSelection = { viewModel.clearSelection() },
                            onDeleteSelected = { viewModel.trashSelectedAccounts() },
                            onExportSelected = {
                                viewModel.exportSelectedAccounts { json ->
                                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                        type = "application/json"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        putExtra(Intent.EXTRA_TITLE, "2fa-export-selected.json")
                                    }
                                    pendingExportJson = json
                                    exportLauncher.launch(intent)
                                }
                            },
                            onIncrementHotp = { viewModel.incrementHotpCounter(it) },
                            onBatchChangeCategory = { viewModel.setBatchCategory(it) }
                        )
                    }
                }
            }

            if (showAddSheet) {
                AddAccountSheet(
                    onDismiss = { showAddSheet = false },
                    onAdd = { issuer, name, secret, _ ->
                        viewModel.addAccount(issuer, name, secret)
                    },
                    onScannedUri = { uri ->
                        if (viewModel.parseAndAddFromUri(uri)) {
                            Toast.makeText(this, "扫描成功", Toast.LENGTH_SHORT).show()
                            showAddSheet = false
                        }
                    }
                )
            }

            // Edit account sheet is handled inside HomeScreen

            if (showSettingsSheet) {
                SettingsSheet(
                    isDarkTheme = uiState.isDarkTheme,
                    biometricEnabled = uiState.biometricEnabled,
                    notificationEnabled = uiState.notificationEnabled,
                    accountCount = uiState.accountCount,
                    onToggleTheme = { viewModel.toggleTheme() },
                    onToggleBiometric = { viewModel.toggleBiometric(it) },
                    onToggleNotification = {
                        if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.toggleNotification(it)
                        }
                    },
                    onExport = {
                        viewModel.getExportJson { json ->
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                type = "application/json"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "2fa-backup.json")
                            }
                            pendingExportJson = json
                            exportLauncher.launch(intent)
                        }
                    },
                    onImport = { importLauncher.launch("application/json") },
                    onTrashClick = {
                        showSettingsSheet = false
                        viewModel.refreshTrashedAccounts()
                        showTrashScreen = true
                    },
                    onCategoryAdminClick = {
                        showSettingsSheet = false
                        showCategoryAdmin = true
                    },
                    onDismiss = { showSettingsSheet = false }
                )
            }

            // Trash screen
            if (showTrashScreen) {
                TrashScreen(
                    trashedAccounts = uiState.trashedAccounts,
                    onRestore = { viewModel.restoreAccount(it) },
                    onPermanentDelete = { viewModel.permanentlyDelete(it) },
                    onClearTrash = { viewModel.clearTrash() },
                    onDismiss = { showTrashScreen = false }
                )
            }

            // Category admin screen
            if (showCategoryAdmin) {
                CategoryAdminScreen(
                    categories = uiState.allCategoryModels,
                    onAdd = { name, emoji, color -> viewModel.addCategory(name, emoji, color) },
                    onDelete = { viewModel.deleteCategory(it) },
                    onSelect = { },
                    onDismiss = { showCategoryAdmin = false }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isLocked && viewModel.uiState.value.biometricEnabled) {
            isLocked = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (isLocked && viewModel.uiState.value.biometricEnabled) {
            biometricHelper.authenticate(
                onSuccess = { isLocked = false },
                onError = { _, _ -> /* stays locked */ }
            )
        }
    }
}
