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
import com.auth2fa.app.data.CryptoUtils
import com.auth2fa.app.totp.HOTPGenerator
import com.auth2fa.app.totp.SteamTOTPGenerator
import com.auth2fa.app.totp.TOTPGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class CodeEntry(
    val accountId: Long,
    val code: String?,
    val remainingSeconds: Int,
    val progress: Float
)

enum class SortMode { NAME, RECENT }

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class AppUiState(
    val accounts: List<Account> = emptyList(),
    val trashedAccounts: List<Account> = emptyList(),
    val codes: Map<Long, CodeEntry> = emptyMap(),
    val searchQuery: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accountCount: Int = 0,
    val biometricEnabled: Boolean = false,
    val autoLockEnabled: Boolean = false,
    val pinEnabled: Boolean = false,
    val sortMode: SortMode = SortMode.NAME,
    val selectedCategory: String = "",
    val allCategories: List<String> = emptyList(),
    val isSelectMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val timeCorrection: Long = 0L,
    val isMaterialYou: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as App).repository

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null
    private val _searchQuery = MutableStateFlow("")
    private var searchCollectionJob: Job? = null
    private val _copiedAccountId = MutableStateFlow<Long?>(null)
    val copiedAccountId: StateFlow<Long?> = _copiedAccountId.asStateFlow()

    init {
        val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val themeOrdinal = prefs.getInt("theme_mode", ThemeMode.DARK.ordinal)
        val biometricEnabled = prefs.getBoolean("biometric_lock", false)
        val autoLockEnabled = prefs.getBoolean("auto_lock", false)
        val pinEnabled = prefs.getBoolean("pin_enabled", false)
        val timeCorrection = prefs.getLong("time_correction", 0L)
        val isMaterialYou = prefs.getBoolean("material_you", false)

        _uiState.update {
            it.copy(
                themeMode = ThemeMode.entries.getOrElse(themeOrdinal) { ThemeMode.DARK },
                biometricEnabled = biometricEnabled,
                autoLockEnabled = autoLockEnabled,
                pinEnabled = pinEnabled,
                timeCorrection = timeCorrection,
                isMaterialYou = isMaterialYou
            )
        }

        // Observe active accounts
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

        // Observe trashed accounts
        viewModelScope.launch {
            repository.trashedAccounts.collect { trashed ->
                _uiState.update { it.copy(trashedAccounts = trashed) }
            }
        }

        refreshCategories()
        startTicking()
    }

    // ---- TOTP/HOTP Code Generation ----

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
        val rawNow = System.currentTimeMillis() / 1000
        val now = rawNow + _uiState.value.timeCorrection

        for (account in accounts) {
            try {
                val (code, remaining, progress) = when (account.accountType) {
                    "STEAM" -> {
                        val c = SteamTOTPGenerator.generate(account.secret, now)
                        Triple(c, SteamTOTPGenerator.getTimeRemaining(now),
                            SteamTOTPGenerator.getProgress(now))
                    }
                    "HOTP" -> {
                        val c = HOTPGenerator.generate(account.secret, account.hotpCounter, account.digits)
                        // HOTP has no time remaining; show counter
                        Triple(c, 0, 0f)
                    }
                    else -> {
                        val c = TOTPGenerator.generate(account.secret, account.digits, account.period, now)
                        Triple(c, TOTPGenerator.getTimeRemaining(account.period, now),
                            TOTPGenerator.getProgress(account.period, now))
                    }
                }
                codes[account.id] = CodeEntry(account.id, code, remaining, progress)
            } catch (e: Exception) {
                codes[account.id] = CodeEntry(account.id, null, 0, 0f)
            }
        }

        _uiState.update { it.copy(codes = codes) }
    }

    fun incrementHotpCounter(accountId: Long) {
        viewModelScope.launch {
            val account = repository.getById(accountId) ?: return@launch
            if (account.accountType != "HOTP") return@launch
            val newCounter = account.hotpCounter + 1
            repository.updateHotpCounter(accountId, newCounter)
        }
    }

    // ---- Search ----

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }

    // ---- CRUD ----

    fun addAccount(
        issuer: String, name: String, secret: String,
        accountType: String = "TOTP", digits: Int = 6, period: Int = 30
    ) {
        viewModelScope.launch {
            repository.insert(
                Account(
                    issuer = issuer.trim(),
                    name = name.trim(),
                    secret = secret.uppercase().replace("[^A-Z2-7]".toRegex(), ""),
                    digits = digits,
                    period = period,
                    accountType = accountType,
                    hotpCounter = if (accountType == "HOTP") 0L else 0L
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

    // ---- Soft Delete (Trash) ----

    fun trashAccount(account: Account) {
        viewModelScope.launch {
            repository.softDelete(account.id)
            refreshCategories()
        }
    }

    fun trashSelectedAccounts() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds.toList()
            for (id in ids) repository.softDelete(id)
            _uiState.update { it.copy(isSelectMode = false, selectedIds = emptySet()) }
            refreshCategories()
        }
    }

    fun restoreAccount(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    fun permanentlyDelete(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun clearTrash() {
        viewModelScope.launch { repository.clearTrash() }
    }

    // ---- Hard Delete ----

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

    // ---- Export (with optional encryption) ----

    fun exportSelectedAccounts(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds
            val allAccounts = repository.getAllList()
            val selected = allAccounts.filter { it.id in ids }
            val json = buildExportJson(selected)
            onResult(json)
            _uiState.update { it.copy(isSelectMode = false, selectedIds = emptySet()) }
        }
    }

    fun getExportJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = try {
                val accounts = repository.getAllList()
                buildExportJson(accounts)
            } catch (e: Exception) { "[]" }
            onResult(json)
        }
    }

    fun getEncryptedExportJson(password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val accounts = repository.getAllList()
                val json = buildExportJson(accounts)
                val encrypted = CryptoUtils.encrypt(json, password)
                onResult(encrypted)
            } catch (e: Exception) { onResult(null) }
        }
    }

    fun importFromEncryptedJson(encrypted: String, password: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val json = CryptoUtils.decrypt(encrypted, password)
            if (json != null) {
                importFromJson(json, onResult)
            } else {
                onResult(-2) // -2 = wrong password
            }
        }
    }

    private fun buildExportJson(accounts: List<Account>): String {
        val jsonArr = JSONArray()
        for (a in accounts) {
            val obj = JSONObject()
            obj.put("issuer", a.issuer)
            obj.put("name", a.name)
            obj.put("secret", a.secret)
            obj.put("digits", a.digits)
            obj.put("period", a.period)
            obj.put("accountType", a.accountType)
            obj.put("hotpCounter", a.hotpCounter)
            obj.put("category", a.category)
            obj.put("note", a.note)
            obj.put("customEmoji", a.customEmoji)
            obj.put("customColor", a.customColor)
            jsonArr.put(obj)
        }
        return jsonArr.toString(2)
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
                            accountType = obj.optString("accountType", "TOTP"),
                            hotpCounter = obj.optLong("hotpCounter", 0),
                            category = obj.optString("category", ""),
                            note = obj.optString("note", ""),
                            customEmoji = obj.optString("customEmoji", ""),
                            customColor = obj.optInt("customColor", 0)
                        )
                    )
                }
                repository.insertAll(accounts)
                refreshCategories()
                accounts.size
            } catch (e: Exception) { -1 }
            onResult(count)
        }
    }

    // ---- Copy Code ----

    fun copyCode(accountId: Long) {
        val entry = _uiState.value.codes[accountId] ?: return
        val code = entry.code ?: return

        val app = getApplication<App>()
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("2FA Code", code))

        _copiedAccountId.value = accountId
        viewModelScope.launch {
            delay(1500)
            _copiedAccountId.value = null
        }
        viewModelScope.launch {
            delay(30_000)
            if (clipboard.hasPrimaryClip() &&
                clipboard.primaryClip?.getItemAt(0)?.text?.toString() == code
            ) {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    // ---- Favorites ----

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

    private fun refreshCategories() {
        viewModelScope.launch {
            val cats = repository.getAllCategories()
            _uiState.update { it.copy(allCategories = cats) }
        }
    }

    // ---- Select Mode ----

    fun toggleSelectMode() {
        _uiState.update {
            it.copy(isSelectMode = !it.isSelectMode, selectedIds = emptySet())
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
        _uiState.update { it.copy(selectedIds = it.accounts.map { a -> a.id }.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    // ---- Theme ----

    fun cycleTheme() {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val modes = ThemeMode.entries
        val current = _uiState.value.themeMode
        val next = modes[(current.ordinal + 1) % modes.size]
        prefs.edit().putInt("theme_mode", next.ordinal).apply()
        _uiState.update { it.copy(themeMode = next) }
    }

    fun toggleMaterialYou() {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val current = _uiState.value.isMaterialYou
        prefs.edit().putBoolean("material_you", !current).apply()
        _uiState.update { it.copy(isMaterialYou = !current) }
    }

    // ---- Biometric & PIN ----

    fun toggleBiometric(enabled: Boolean) {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("biometric_lock", enabled).apply()
        _uiState.update { it.copy(biometricEnabled = enabled) }
        if (!enabled && !_uiState.value.pinEnabled) {
            // No lock enabled
        }
    }

    fun toggleAutoLock(enabled: Boolean) {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_lock", enabled).apply()
        _uiState.update { it.copy(autoLockEnabled = enabled) }
    }

    // ---- PIN Management ----

    fun isPinSet(): Boolean {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.contains("pin_hash")
    }

    fun setPin(pin: String): Boolean {
        return try {
            val hash = hashPin(pin)
            val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
            prefs.edit().putString("pin_hash", hash).putBoolean("pin_enabled", true).apply()
            _uiState.update { it.copy(pinEnabled = true) }
            true
        } catch (e: Exception) { false }
    }

    fun verifyPin(pin: String): Boolean {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val storedHash = prefs.getString("pin_hash", null) ?: return false
        return hashPin(pin) == storedHash
    }

    fun disablePin() {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().remove("pin_hash").putBoolean("pin_enabled", false).apply()
        _uiState.update { it.copy(pinEnabled = false) }
    }

    private fun hashPin(pin: String): String {
        val salt = "2fa-native-pin-salt".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hash = digest.digest(pin.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    // ---- Time Correction ----

    fun setTimeCorrection(offsetSeconds: Long) {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putLong("time_correction", offsetSeconds).apply()
        _uiState.update { it.copy(timeCorrection = offsetSeconds) }
    }

    // ---- QR URI Parsing ----

    fun parseAndAddFromUri(uri: String): Boolean {
        return try {
            if (!uri.startsWith("otpauth://")) return false
            val parsed = java.net.URI(uri)
            val host = parsed.host

            val query = parsed.query ?: ""
            val params = query.split("&").associate {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
                else "" to ""
            }

            val secret = params["secret"] ?: return false
            val issuerFromParam = params["issuer"] ?: ""
            val digits = params["digits"]?.toIntOrNull() ?: 6
            val period = params["period"]?.toIntOrNull() ?: 30
            val counter = params["counter"]?.toLongOrNull() ?: 0L
            val path = parsed.path.removePrefix("/")

            val accountType = when (host) {
                "hotp" -> "HOTP"
                "totp" -> "TOTP"
                else -> return false
            }

            val (finalIssuer, finalName) = when {
                path.isEmpty() -> issuerFromParam to ""
                path.contains(':') -> {
                    val parts = path.split(':', limit = 2)
                    (issuerFromParam.ifEmpty { parts[0] }) to parts[1]
                }
                else -> issuerFromParam to path
            }

            if (finalIssuer.isBlank()) return false

            repository.insert(
                Account(
                    issuer = finalIssuer,
                    name = finalName,
                    secret = secret,
                    digits = digits,
                    period = period,
                    accountType = accountType,
                    hotpCounter = counter
                )
            )
            refreshCategories()
            true
        } catch (e: Exception) { false }
    }

    // ---- Lock Support ----

    fun isAutoLockEnabled(): Boolean = _uiState.value.autoLockEnabled
    fun isBiometricEnabled(): Boolean = _uiState.value.biometricEnabled
    fun isPinEnabled(): Boolean = _uiState.value.pinEnabled

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
        searchCollectionJob?.cancel()
    }
}
