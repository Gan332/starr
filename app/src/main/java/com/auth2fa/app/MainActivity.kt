package com.auth2fa.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.auth2fa.app.biometric.BiometricHelper
import com.auth2fa.app.ui.screens.AddAccountSheet
import com.auth2fa.app.ui.screens.HomeScreen
import com.auth2fa.app.ui.screens.SettingsSheet
import com.auth2fa.app.ui.theme.Auth2FATheme
import com.auth2fa.app.viewmodel.MainViewModel
import com.auth2fa.app.viewmodel.SortMode
import java.io.BufferedReader

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var biometricHelper: BiometricHelper
    private var pendingExportJson: String = ""
    private var isLocked = false

    // File picker for import
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val json = contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(inputStream.reader()).use { it.readText() }
                } ?: ""

                viewModel.importFromJson(json) { count ->
                    if (count > 0) {
                        Toast.makeText(this, "成功导入 $count 个账户", Toast.LENGTH_SHORT).show()
                    } else if (count == 0) {
                        Toast.makeText(this, "未导入任何账户", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "导入失败：文件格式无效", Toast.LENGTH_SHORT).show()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        biometricHelper = BiometricHelper(this)

        // Check if we need to authenticate on start
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

            Auth2FATheme(darkTheme = uiState.isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isLocked) {
                        HomeScreen(
                            uiState = uiState,
                            copiedAccountId = copiedId,
                            onSearch = { viewModel.updateSearch(it) },
                            onCopyCode = { viewModel.copyCode(it) },
                            onDeleteAccount = { viewModel.deleteAccount(it) },
                            onEditAccount = { viewModel.updateAccount(it) },
                            onAddClick = { showAddSheet = true },
                            onSettingsClick = { showSettingsSheet = true },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onSetSortMode = { viewModel.setSortMode(it) },
                            onSelectCategory = { viewModel.setSelectedCategory(it) },
                            onToggleSelectMode = { viewModel.toggleSelectMode() },
                            onToggleSelectId = { viewModel.toggleSelectId(it) },
                            onSelectAll = { viewModel.selectAll() },
                            onClearSelection = { viewModel.clearSelection() },
                            onDeleteSelected = { viewModel.deleteSelectedAccounts() },
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
                            }
                        )
                    }
                }
            }

            // Add account bottom sheet
            if (showAddSheet) {
                AddAccountSheet(
                    onDismiss = { showAddSheet = false },
                    onAdd = { issuer, name, secret, isSteam ->
                        viewModel.addAccount(issuer, name, secret, isSteam)
                    },
                    onScannedUri = { uri ->
                        val success = viewModel.parseAndAddFromUri(uri)
                        if (success) {
                            Toast.makeText(this, "扫描成功", Toast.LENGTH_SHORT).show()
                            showAddSheet = false
                        }
                    }
                )
            }

            // Settings bottom sheet
            if (showSettingsSheet) {
                SettingsSheet(
                    isDarkTheme = uiState.isDarkTheme,
                    biometricEnabled = uiState.biometricEnabled,
                    autoLockEnabled = uiState.autoLockEnabled,
                    accountCount = uiState.accountCount,
                    onToggleTheme = { viewModel.toggleTheme() },
                    onToggleBiometric = { viewModel.toggleBiometric(it) },
                    onToggleAutoLock = { viewModel.toggleAutoLock(it) },
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
                    onImport = {
                        importLauncher.launch("application/json")
                    },
                    onDismiss = { showSettingsSheet = false }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Lock when app goes to background if auto-lock is enabled
        if (viewModel.isAutoLockEnabled() && !isFinishing) {
            isLocked = true
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-authenticate if locked
        if (isLocked && viewModel.isAutoLockEnabled()) {
            biometricHelper.authenticate(
                onSuccess = { isLocked = false },
                onError = { _, _ -> /* stays locked */ }
            )
        } else if (isLocked) {
            // If we just launched and biometric lock is on, handled in onCreate
        }
    }
}
