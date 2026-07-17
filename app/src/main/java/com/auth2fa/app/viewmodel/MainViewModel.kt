package com.auth2fa.app.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.auth2fa.app.App
import com.auth2fa.app.data.Account
import com.auth2fa.app.data.Category
import com.auth2fa.app.notification.NotificationHelper
import com.auth2fa.app.totp.HOTPGenerator
import com.auth2fa.app.totp.SteamTOTPGenerator
import com.auth2fa.app.totp.TOTPGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class CodeEntry(
    val accountId: Long,
    val code: String?,
    val remainingSeconds: Int,
    val progress: Float
)

enum class SortMode { NAME, RECENT, TYPE }
enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class AppUiState(
    val accounts: List<Account> = emptyList(),
    val codes: Map<Long, CodeEntry> = emptyMap(),
    val searchQuery: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val useMaterialYou: Boolean = false,
    val accountCount: Int = 0,
    val biometricEnabled: Boolean = false,
    val pinEnabled: Boolean = false,
    val notificationEnabled: Boolean = false,
    val sortMode: SortMode = SortMode.NAME,
    val showFavoritesOnly: Boolean = false,
    val selectedCategory: String = "",
    val allCategories: List<String> = emptyList(),
    val isSelectMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val trashedAccounts: List<Account> = emptyList(),
    val allCategoryModels: List<Category> = emptyList()
) {
    val isDarkTheme: Boolean
        get() = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> false // Runtime will determine, but default to false
        }
}

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
        // Load theme preference
        val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val themeModeName = prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        val themeMode = try { ThemeMode.valueOf(themeModeName) } catch (e: Exception) { ThemeMode.DARK }
        val useMaterialYou = prefs.getBoolean("material_you", false)
        val biometricEnabled = prefs.getBoolean("biometric_lock", false)
        val pinEnabled = prefs.getBoolean("pin_enabled", false)
        val notificationEnabled = prefs.getBoolean("notification_enabled", false)
        _uiState.update { it.copy(themeMode = themeMode, useMaterialYou = useMaterialYou, biometricEnabled = biometricEnabled, pinEnabled = pinEnabled, notificationEnabled = notificationEnabled) }

        // Observe accounts with debounced search
        searchCollectionJob = viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .flatMapLatest { query ->
                    if (query.isBlank()) repository.allAccounts
                    else repository.search(query)
                }
                .collect { accounts ->
                    val categories = withContext(Dispatchers.IO) {
                        repository.getAllCategories()
                    }
                    val categoryModels = withContext(Dispatchers.IO) {
                        repository.getAllCategoriesList()
                    }
                    _uiState.update { it.copy(accounts = accounts, accountCount = accounts.size, allCategories = categories, allCategoryModels = categoryModels) }
                }
        }

        // Start ticking independently of search results
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
                val (code, remaining, progress) = when (account.accountType) {
                    "HOTP" -> {
                        val c = HOTPGenerator.generate(account.secret, account.hotpCounter, account.digits)
                        // HOTP has no time-based period; use remaining=0, progress=0f
                        Triple(c, 0, 0f)
                    }
                    "STEAM" -> {
                        val c = SteamTOTPGenerator.generate(account.secret, now)
                        val r = SteamTOTPGenerator.getTimeRemaining(now)
                        val p = SteamTOTPGenerator.getProgress(now)
                        Triple(c, r, p)
                    }
                    else -> {
                        val c = TOTPGenerator.generate(account.secret, account.digits, account.period, now)
                        val r = TOTPGenerator.getTimeRemaining(account.period, now)
                        val p = TOTPGenerator.getProgress(account.period, now)
                        Triple(c, r, p)
                    }
                }
                codes[account.id] = CodeEntry(account.id, code, remaining, progress)
            } catch (e: Exception) {
                codes[account.id] = CodeEntry(account.id, null, 0, 0f)
            }
        }

        _uiState.update { it.copy(codes = codes) }

        // Update notification if enabled
        if (_uiState.value.notificationEnabled && accounts.isNotEmpty()) {
            val context = getApplication<App>()
            NotificationHelper.show(context, accounts)
        }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }

    fun addAccount(issuer: String, name: String, secret: String, accountType: String = "TOTP", digits: Int = 6) {
        viewModelScope.launch {
            repository.insert(
                Account(
                    issuer = issuer.trim(),
                    name = name.trim(),
                    secret = secret.uppercase().replace("[^A-Z2-7]".toRegex(), ""),
                    accountType = accountType,
                    digits = if (accountType == "STEAM") 5 else digits
                )
            )
        }
    }

    fun trashAccount(account: Account) {
        viewModelScope.launch {
            repository.softDelete(account.id)
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch {
            repository.update(account)
        }
    }

    fun restoreAccount(account: Account) {
        viewModelScope.launch {
            repository.restore(account.id)
            refreshTrashed()
        }
    }

    fun restoreAccount(id: Long) {
        viewModelScope.launch {
            repository.restore(id)
            refreshTrashed()
        }
    }

    fun permanentlyDelete(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
            refreshTrashed()
        }
    }

    fun clearTrash() {
        viewModelScope.launch {
            repository.clearTrash()
            refreshTrashed()
        }
    }

    private fun refreshTrashed() {
        viewModelScope.launch {
            val trashed = withContext(Dispatchers.IO) { repository.getTrashedList() }
            _uiState.update { it.copy(trashedAccounts = trashed) }
        }
    }

    fun refreshTrashedAccounts() {
        refreshTrashed()
    }

    fun addCategory(name: String, emoji: String, color: Int) {
        viewModelScope.launch {
            repository.insertCategory(Category(name = name, emoji = emoji, color = color))
            refreshCategories()
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            refreshCategories()
        }
    }

    private fun refreshCategories() {
        viewModelScope.launch {
            val cats = withContext(Dispatchers.IO) { repository.getAllCategoriesList() }
            _uiState.update { it.copy(allCategoryModels = cats) }
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

    fun toggleTheme() {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val current = _uiState.value.themeMode
        val next = when (current) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
        }
        prefs.edit().putString("theme_mode", next.name).apply()
        _uiState.update { it.copy(themeMode = next) }
    }

    fun setThemeMode(mode: ThemeMode) {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun toggleMaterialYou(enabled: Boolean) {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("material_you", enabled).apply()
        _uiState.update { it.copy(useMaterialYou = enabled) }
    }

    fun toggleBiometric(enabled: Boolean) {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("biometric_lock", enabled).apply()
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun toggleNotification(enabled: Boolean) {
        val context = getApplication<App>()
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notification_enabled", enabled).apply()
        _uiState.update { it.copy(notificationEnabled = enabled) }
        if (!enabled) {
            NotificationHelper.cancel(context)
        }
    }

    fun hasNotificationPermission(): Boolean {
        val context = getApplication<App>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Below Android 13, no runtime permission needed
        }
    }

    // --- PIN lock methods ---

    /**
     * Hash a PIN using SHA-256.
     */
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Check if a PIN has been set.
     */
    fun isPinSet(): Boolean {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.contains("pin_hash")
    }

    /**
     * Set a new PIN (stores SHA-256 hash).
     */
    fun setPin(pin: String) {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putString("pin_hash", hashPin(pin)).apply()
    }

    /**
     * Verify the entered PIN against stored hash.
     */
    fun verifyPin(pin: String): Boolean {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val storedHash = prefs.getString("pin_hash", null) ?: return false
        return hashPin(pin) == storedHash
    }

    /**
     * Enable or disable PIN lock.
     * Returns true if the operation succeeded, false if PIN needs to be set first.
     */
    fun togglePinEnabled(enabled: Boolean): Boolean {
        if (enabled && !isPinSet()) return false
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pin_enabled", enabled).apply()
        _uiState.update { it.copy(pinEnabled = enabled) }
        return true
    }

    /**
     * Remove the stored PIN and disable PIN lock.
     */
    fun removePin() {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().remove("pin_hash").putBoolean("pin_enabled", false).apply()
        _uiState.update { it.copy(pinEnabled = false) }
    }

    // --- HomeScreen callback methods ---

    fun toggleFavorite(accountId: Long) {
        viewModelScope.launch {
            val account = withContext(Dispatchers.IO) { repository.getById(accountId) } ?: return@launch
            repository.update(account.copy(isFavorite = !account.isFavorite))
        }
    }

    fun setSortMode(mode: SortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    fun setSelectedCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleFavoritesOnly() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    fun toggleSelectMode() {
        _uiState.update {
            it.copy(
                isSelectMode = !it.isSelectMode,
                selectedIds = if (it.isSelectMode) emptySet() else it.selectedIds
            )
        }
    }

    fun toggleSelectId(id: Long) {
        _uiState.update {
            val newIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = newIds)
        }
    }

    fun selectAll() {
        _uiState.update {
            it.copy(selectedIds = it.accounts.map { a -> a.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectMode = false) }
    }

    fun trashSelectedAccounts() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds
            for (id in ids) {
                withContext(Dispatchers.IO) { repository.deleteById(id) }
            }
            _uiState.update { it.copy(selectedIds = emptySet(), isSelectMode = false) }
        }
    }

    fun exportSelectedAccounts(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = try {
                val jsonArr = JSONArray()
                val ids = _uiState.value.selectedIds
                val scopeAccounts = withContext(Dispatchers.IO) { repository.getAllList() }
                for (a in scopeAccounts.filter { it.id in ids }) {
                    val obj = JSONObject()
                    obj.put("issuer", a.issuer)
                    obj.put("name", a.name)
                    obj.put("secret", a.secret)
                    obj.put("digits", a.digits)
                    obj.put("period", a.period)
                    jsonArr.put(obj)
                }
                jsonArr.toString(2)
            } catch (e: Exception) { "[]" }
            onResult(json)
        }
    }

    fun incrementHotpCounter(accountId: Long) {
        viewModelScope.launch {
            val account = withContext(Dispatchers.IO) { repository.getById(accountId) } ?: return@launch
            if (account.accountType != "HOTP") return@launch
            val newCounter = account.hotpCounter + 1
            withContext(Dispatchers.IO) {
                repository.updateHotpCounter(accountId, newCounter)
                repository.update(account.copy(hotpCounter = newCounter))
            }
            // Regenerate codes immediately to show the new HOTP code
            generateAllCodes()
        }
    }

    fun setBatchCategory(category: String) {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds
            for (id in ids) {
                val account = withContext(Dispatchers.IO) { repository.getById(id) } ?: continue
                withContext(Dispatchers.IO) { repository.update(account.copy(category = category)) }
            }
            _uiState.update { it.copy(selectedIds = emptySet(), isSelectMode = false) }
        }
    }

    fun getExportJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = try {
                val jsonArr = JSONArray()
                val scopeAccounts = withContext(Dispatchers.IO) {
                    repository.getAllList()
                }
                for (a in scopeAccounts) {
                    val obj = JSONObject()
                    obj.put("issuer", a.issuer)
                    obj.put("name", a.name)
                    obj.put("secret", a.secret)
                    obj.put("digits", a.digits)
                    obj.put("period", a.period)
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
                            period = obj.optInt("period", 30)
                        )
                    )
                }
                repository.insertAll(accounts)
                accounts.size
            } catch (e: Exception) {
                -1
            }
            onResult(count)
        }
    }

    /**
     * Parse an otpauth:// URI and auto-add the account.
     */
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

            // Path format can be "issuer:name" or just "name"
            val (finalIssuer, finalName) = when {
                path.isEmpty() -> issuerFromParam to ""
                path.contains(':') -> {
                    val parts = path.split(':', limit = 2)
                    (issuerFromParam.ifEmpty { parts[0] }) to parts[1]
                }
                else -> {
                    // No colon: path is the name, issuer comes from query param
                    issuerFromParam to path
                }
            }

            if (finalIssuer.isBlank()) return false

            addAccount(finalIssuer, finalName, secret)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
        searchCollectionJob?.cancel()
    }
}
