package com.auth2fa.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.auth2fa.app.biometric.BiometricHelper
import com.auth2fa.app.ui.screens.*
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

    private val encryptedExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingExportJson.toByteArray())
                }
                Toast.makeText(this, "加密备份已导出", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var pendingEncryptedImport: String = ""
    private var showEncryptImportTrigger by mutableStateOf(0)

    private val encryptedImportLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                pendingEncryptedImport = contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(inputStream.reader()).use { it.readText() }
                } ?: ""
                showEncryptImportTrigger++
            } catch (e: Exception) {
                Toast.makeText(this, "读取失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
            var showPinEntry by remember {
                mutableStateOf(
                    isLocked && !prefs.getBoolean("biometric_lock", false)
                        && prefs.getBoolean("pin_enabled", false)
                )
            }
            var pinEntryError by remember { mutableStateOf("") }

            var showCategoryAdmin by remember { mutableStateOf(false) }

            Auth2FATheme(
                themeMode = uiState.themeMode,
                useMaterialYou = uiState.isMaterialYou
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        showPinEntry -> {
                            PinEntryScreen(
                                onPinSubmit = { pin ->
                                    if (viewModel.verifyPin(pin)) {
                                        showPinEntry = false
                                        isLocked = false
                                    } else {
                                        pinEntryError = "PIN 错误"
                                    }
                                },
                                error = pinEntryError
                            )
                        }
                        isLocked -> { /* waiting for biometric */ }
                        showTrashScreen -> {
                            TrashScreen(
                                trashedAccounts = uiState.trashedAccounts,
                                onRestore = { viewModel.restoreAccount(it) },
                                onPermanentDelete = { viewModel.permanentlyDelete(it) },
                                onClearTrash = { viewModel.clearTrash() },
                                onDismiss = { showTrashScreen = false }
                            )
                        }
                        else -> {
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
                                onIncrementHotp = { viewModel.incrementHotpCounter(it) }
                            )
                        }
                    }
                }

            // Encrypted import dialog (triggered by file picker)
            if (showEncryptImportTrigger > 0 && this@MainActivity.pendingEncryptedImport.isNotEmpty()) {
                var showDialog by remember(showEncryptImportTrigger) { mutableStateOf(true) }
                var pw by remember { mutableStateOf("") }
                var pwError by remember { mutableStateOf("") }
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("导入加密备份") },
                        text = {
                            Column {
                                Text("输入解密密码")
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = pw,
                                    onValueChange = { pw = it; pwError = "" },
                                    label = { Text("密码") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    isError = pwError.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (pwError.isNotEmpty()) {
                                    Text(pwError, color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (pw.length < 4) {
                                    pwError = "密码至少 4 位"
                                } else {
                                    viewModel.importFromEncryptedJson(pendingEncryptedImport, pw) { count ->
                                        when (count) {
                                            -2 -> pwError = "密码错误"
                                            -1 -> Toast.makeText(this@MainActivity, "导入失败", Toast.LENGTH_SHORT).show()
                                            0 -> Toast.makeText(this@MainActivity, "未导入任何账户", Toast.LENGTH_SHORT).show()
                                            else -> {
                                                Toast.makeText(this@MainActivity, "成功导入 $count 个账户", Toast.LENGTH_SHORT).show()
                                                showDialog = false
                                            }
                                        }
                                    }
                                }
                            }) { Text("导入") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDialog = false }) { Text("取消") }
                        }
                    )
                }
            }
            }

            // Add account sheet
            if (showAddSheet) {
                AddAccountSheet(
                    onDismiss = { showAddSheet = false },
                    onAdd = { issuer, name, secret, accountType ->
                        viewModel.addAccount(issuer, name, secret, accountType)
                    },
                    onScannedUri = { uri ->
                        if (viewModel.parseAndAddFromUri(uri)) {
                            Toast.makeText(this, "扫描成功", Toast.LENGTH_SHORT).show()
                            showAddSheet = false
                        }
                    }
                )
            }

            if (showSettingsSheet) {
                SettingsSheet(
                    themeMode = uiState.themeMode,
                    biometricEnabled = uiState.biometricEnabled,
                    autoLockEnabled = uiState.autoLockEnabled,
                    pinEnabled = uiState.pinEnabled,
                    isMaterialYou = uiState.isMaterialYou,
                    accountCount = uiState.accountCount,
                    timeCorrection = uiState.timeCorrection,
                    showFavoritesOnly = uiState.showFavoritesOnly,
                    webdavUrl = uiState.webdavUrl,
                    webdavUser = uiState.webdavUser,
                    webdavPassword = uiState.webdavPassword,
                    onCycleTheme = { viewModel.cycleTheme() },
                    onToggleBiometric = { viewModel.toggleBiometric(it) },
                    onToggleAutoLock = { viewModel.toggleAutoLock(it) },
                    onToggleMaterialYou = { viewModel.toggleMaterialYou() },
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
                    onEncryptedExport = { password ->
                        viewModel.getEncryptedExportJson(password) { encrypted ->
                            if (encrypted != null) {
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    type = "application/octet-stream"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    putExtra(Intent.EXTRA_TITLE, "2fa-backup.enc")
                                }
                                pendingExportJson = encrypted
                                encryptedExportLauncher.launch(intent)
                            } else {
                                Toast.makeText(this, "加密失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onEncryptedImport = {
                        encryptedImportLauncher.launch("application/octet-stream")
                    },
                    onExportGoogleAuth = {
                        viewModel.exportToGoogleAuth { json ->
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                type = "text/plain"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "2fa-google-auth.txt")
                            }
                            pendingExportJson = json
                            exportLauncher.launch(intent)
                        }
                    },
                    onExportAegis = {
                        viewModel.exportToAegis { json ->
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                type = "application/json"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "2fa-aegis.json")
                            }
                            pendingExportJson = json
                            exportLauncher.launch(intent)
                        }
                    },
                    onCategoryAdmin = {
                        showSettingsSheet = false
                        showCategoryAdmin = true
                    },
                    onWebdavConfig = { url, user, password ->
                        viewModel.saveWebdavConfig(url, user, password)
                    },
                    onToggleFavoritesFilter = { viewModel.toggleFavoritesOnly() },
                    onTrashClick = { showSettingsSheet = false; showTrashScreen = true },
                    onSetPin = { pin -> viewModel.setPin(pin) },
                    onVerifyPin = { pin -> viewModel.verifyPin(pin) },
                    onDisablePin = { viewModel.disablePin() },
                    onSetTimeCorrection = { viewModel.setTimeCorrection(it) },
                    onDismiss = { showSettingsSheet = false }
                )
            }

            // Category admin screen
            if (showCategoryAdmin) {
                CategoryAdminScreen(
                    categories = uiState.categoryModels,
                    onAdd = { name, emoji, color -> viewModel.addCategory(name, emoji, color) },
                    onDelete = { category -> viewModel.deleteCategory(category) },
                    onSelect = { categoryName ->
                        viewModel.setSelectedCategory(categoryName)
                        showCategoryAdmin = false
                    },
                    onDismiss = { showCategoryAdmin = false }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.isAutoLockEnabled() && !isFinishing) {
            isLocked = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (isLocked && viewModel.isBiometricEnabled()) {
            biometricHelper.authenticate(
                onSuccess = { isLocked = false },
                onError = { _, _ -> /* stays locked */ }
            )
        }
    }
}
