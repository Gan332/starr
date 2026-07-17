package com.auth2fa.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auth2fa.app.ui.screens.AddAccountSheet
import com.auth2fa.app.ui.screens.HomeScreen
import com.auth2fa.app.ui.screens.SettingsSheet
import com.auth2fa.app.ui.theme.Auth2FATheme
import com.auth2fa.app.viewmodel.MainViewModel
import java.io.BufferedReader

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // File picker for import
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = BufferedReader(inputStream?.reader())
                val json = reader.readText()
                reader.close()

                val count = viewModel.importFromJson(json)
                if (count > 0) {
                    Toast.makeText(this, "成功导入 $count 个账户", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "未导入任何账户", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    HomeScreen(
                        uiState = uiState,
                        copiedAccountId = copiedId,
                        onSearch = { viewModel.updateSearch(it) },
                        onCopyCode = { viewModel.copyCode(it) },
                        onDeleteAccount = { viewModel.deleteAccount(it) },
                        onAddClick = { showAddSheet = true },
                        onSettingsClick = { showSettingsSheet = true }
                    )
                }
            }

            // Add account bottom sheet
            if (showAddSheet) {
                AddAccountSheet(
                    onDismiss = { showAddSheet = false },
                    onAdd = { issuer, name, secret ->
                        viewModel.addAccount(issuer, name, secret)
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
                    accountCount = uiState.accountCount,
                    onToggleTheme = { viewModel.toggleTheme() },
                    onExport = {
                        val json = viewModel.getExportJson()
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "application/json"
                            addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, "2fa-backup.json")
                        }
                        exportLauncher.launch(intent)
                    },
                    onImport = {
                        importLauncher.launch("application/json")
                    },
                    onDismiss = { showSettingsSheet = false }
                )
            }
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = viewModel.getExportJson()
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                Toast.makeText(this, "备份已导出", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
