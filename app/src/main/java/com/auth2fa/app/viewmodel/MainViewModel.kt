package com.auth2fa.app.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.auth2fa.app.App
import com.auth2fa.app.data.Account
import com.auth2fa.app.data.AccountRepository
import com.auth2fa.app.totp.SteamTOTPGenerator
import com.auth2fa.app.totp.TOTPGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class CodeEntry(
    val accountId: Long,
    val code: String?,
    val remainingSeconds: Int,
    val progress: Float
)

enum class SortMode { NAME, RECENT }

data class AppUiState(
    val accounts: List<Account> = emptyList(),
    val codes: Map<Long, CodeEntry> = emptyMap(),
    val searchQuery: String = "",
    val isDarkTheme: Boolean = true,
    val accountCount: Int = 0,
    val biometricEnabled: Boolean = false,
    val autoLockEnabled: Boolean = false,
    val sortMode: SortMode = SortMode.NAME,
    val selectedCategory: String = "",
    val allCategories: List<String> = emptyList(),
    val isSelectMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as App).repository

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null

    // Search query flow with debounce
    private val _searchQuery = MutableStateFlow("")
    private var searchCollectionJob: Job? = null

    // Track copied account ID for animation
    private val _copiedAccountId = MutableStateFlow<Long?>(null)
    val copiedAccountId: StateFlow<Long?> = _copiedAccountId.asStateFlow()

    init {
        // Load preferences
        val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_theme", true)
        val biometricEnabled = prefs.getBoolean("biometric_lock", false)
        val autoLockEnabled = prefs.getBoolean("auto_lock", false)
        _uiState.update {
            it.copy(
                isDarkTheme = isDark,
                biometricEnabled = biometricEnabled,
                autoLockEnabled = autoLockEnabled
            )
        }

        // Observe accounts with debounced search + category filter
        searchCollectionJob = viewModelScope.launch {
            combine(
                _searchQuery.debounce(300),
                _uiState.map { it.selectedCategory }.distinctUntilChanged()
            ) { query, category -> Pair(query, category) }
                .flatMapLatest { (query, category) ->
                    val flow = when {
                        query.isNotBlank() -> repository.search(query)
                        category.isNotBlank() -> repository.getByCategory(category)
                        else -> repository.allAccounts
                    }
                    flow
                }
                .collect { accounts ->
                    val sorted = when (_uiState.value.sortMode) {
                        SortMode.RECENT -> accounts.sortedByDescending { it.createdAt }
                        SortMode.NAME -> accounts
                    }
                    _uiState.update {
                        it.copy(accounts = sorted, accountCount = sorted.size)
                    }
                }
        }

        // Load categories
        viewModelScope.launch {
            val cats = repository.getAllCategories()
            _uiState.update { it.copy(allCategories = cats) }
        }

        startTicking()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                generateAllCodes()
                delay(1000L)
            }
        }
    }

    private suspend fun generateAllCodes() {
        val accounts = _uiState.value.accounts
        val codes = mutableMapOf<Long, CodeEntry>()
        val now = System.currentTimeMillis() / 1000

        for (account in accounts) {
            try {
                val code = if (account.isSteam) {
                    SteamTOTPGenerator.generate(account.secret, now)
                } else {
                    TOTPGenerator.generate(account.secret, account.digits, account.period, now)
                }
                val remaining = if (account.isSteam) {
                    SteamTOTPGenerator.getTimeRemaining(now)
                } else {
                    TOTPGenerator.getTimeRemaining(account.period, now)
                }
                val progress = if (account.isSteam) {
                    SteamTOTPGenerator.getProgress(now)
                } else {
                    TOTPGenerator.getProgress(account.period, now)
                }
                codes[account.id] = CodeEntry(account.id, code, remaining, progress)
            } catch (e: Exception) {
                codes[account.id] = CodeEntry(account.id, null, 0, 0f)
            }
        }

        _uiState.update { it.copy(codes = codes) }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }

    fun addAccount(issuer: String, name: String, secret: String, isSteam: Boolean = false) {
        viewModelScope.launch {
            repository.insert(
                Account(
                    issuer = issuer.trim(),
                    name = name.trim(),
                    secret = secret.uppercase().replace("[^A-Z2-7]".toRegex(), ""),
                    isSteam = isSteam
                )
            )
            refreshCategories()
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch {
            repository.update(account)
            refreshCategories()
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            repository.delete(account)
            refreshCategories()
        }
    }

    fun deleteSelectedAccounts() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds.toList()
            repository.deleteByIds(ids)
            _uiState.update { it.copy(isSelectMode = false, selectedIds = emptySet()) }
            refreshCategories()
        }
    }

    fun exportSelectedAccounts(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds
            val allAccounts = repository.getAllList()
            val selected = allAccounts.filter { it.id in ids }
            val jsonArr = JSONArray()
            for (a in selected) {
                val obj = JSONObject()
                obj.put("issuer", a.issuer)
                obj.put("name", a.name)
                obj.put("secret", a.secret)
                obj.put("digits", a.digits)
                obj.put("period", a.period)
                obj.put("isSteam", a.isSteam)
                jsonArr.put(obj)
            }
            onResult(jsonArr.toString(2))
            _uiState.update { it.copy(isSelectMode = false, selectedIds = emptySet()) }
        }
    }

    fun copyCode(accountId: Long) {
        val entry = _uiState.value.codes[accountId] ?: return
        val code = entry.code ?: return

        val clipboard = getApplication<App>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("2FA Code", code))

        _copiedAccountId.value = accountId
        viewModelScope.launch {
            delay(1500)
            _copiedAccountId.value = null
        }
        // Clear clipboard after 30 seconds for security
        viewModelScope.launch {
            delay(30_000)
            if (clipboard.hasPrimaryClip() &&
                clipboard.primaryClip?.getItemAt(0)?.text?.toString() == code
            ) {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    fun toggleFavorite(accountId: Long) {
        viewModelScope.launch {
            val account = repository.getById(accountId) ?: return@launch
            repository.setFavorite(accountId, !account.isFavorite)
        }
    }

    // ---- Sort & Filter ----

    fun setSortMode(mode: SortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    fun setSelectedCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    private suspend fun refreshCategories() {
        val cats = repository.getAllCategories()
        _uiState.update { it.copy(allCategories = cats) }
    }

    // ---- Select Mode ----

    fun toggleSelectMode() {
        _uiState.update {
            it.copy(
                isSelectMode = !it.isSelectMode,
                selectedIds = emptySet()
            )
        }
    }

    fun toggleSelectId(id: Long) {
        _uiState.update {
            val newSet = it.selectedIds.toMutableSet()
            if (newSet.contains(id)) newSet.remove(id) else newSet.add(id)
            it.copy(selectedIds = newSet)
        }
    }

    fun selectAll() {
        _uiState.update {
            it.copy(selectedIds = it.accounts.map { a -> a.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    // ---- Theme ----

    fun toggleTheme() {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val current = _uiState.value.isDarkTheme
        prefs.edit().putBoolean("dark_theme", !current).apply()
        _uiState.update { it.copy(isDarkTheme = !current) }
    }

    fun toggleBiometric(enabled: Boolean) {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("biometric_lock", enabled).apply()
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun toggleAutoLock(enabled: Boolean) {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_lock", enabled).apply()
        _uiState.update { it.copy(autoLockEnabled = enabled) }
    }

    // ---- Export / Import ----

    fun getExportJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = try {
                val jsonArr = JSONArray()
                val scopeAccounts = repository.getAllList()
                for (a in scopeAccounts) {
                    val obj = JSONObject()
                    obj.put("issuer", a.issuer)
                    obj.put("name", a.name)
                    obj.put("secret", a.secret)
                    obj.put("digits", a.digits)
                    obj.put("period", a.period)
                    obj.put("isSteam", a.isSteam)
                    jsonArr.put(obj)
                }
                jsonArr.toString(2)
            } catch (e: Exception) {
                "[]"
            }
            onResult(json)
        }
    }

    fun importFromJson(json: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = try {
                val jsonArr = JSONArray(json)
                val accounts = mutableListOf<Account>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    accounts.add(
                        Account(
                            issuer = obj.getString("issuer"),
                            name = obj.optString("name", ""),
                            secret = obj.getString("secret"),
                            digits = obj.optInt("digits", 6),
                            period = obj.optInt("period", 30),
                            isSteam = obj.optBoolean("isSteam", false)
                        )
                    )
                }
                repository.insertAll(accounts)
                refreshCategories()
                accounts.size
            } catch (e: Exception) {
                -1
            }
            onResult(count)
        }
    }

    fun parseAndAddFromUri(uri: String): Boolean {
        return try {
            if (!uri.startsWith("otpauth://")) return false

            val parsed = java.net.URI(uri)
            if (parsed.host != "totp") return false

            val query = parsed.query ?: ""
            val params = query.split("&").associate {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
                else "" to ""
            }

            val secret = params["secret"] ?: return false
            val issuerFromParam = params["issuer"] ?: ""
            val path = parsed.path.removePrefix("/")

            val (finalIssuer, finalName) = when {
                path.isEmpty() -> issuerFromParam to ""
                path.contains(':') -> {
                    val parts = path.split(':', limit = 2)
                    (issuerFromParam.ifEmpty { parts[0] }) to parts[1]
                }
                else -> issuerFromParam to path
            }

            if (finalIssuer.isBlank()) return false

            addAccount(finalIssuer, finalName, secret)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- Auto-lock support ----
    fun isAutoLockEnabled(): Boolean = _uiState.value.autoLockEnabled

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
        searchCollectionJob?.cancel()
    }
}
