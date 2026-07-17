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
import com.auth2fa.app.data.Category
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
    val selectedCategory: String = "",
    val sortMode: SortMode = SortMode.NAME,
    val allCategories: List<String> = emptyList(),
    val showFavoritesOnly: Boolean = false,
    val categoryModels: List<Category> = emptyList(),
    val isSelectMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val timeCorrection: Long = 0L,
    val isMaterialYou: Boolean = false,
    // WebDAV config
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPassword: String = ""
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
        val webdavUrl = prefs.getString("webdav_url", "") ?: ""
        val webdavUser = prefs.getString("webdav_user", "") ?: ""
        val webdavPassword = prefs.getString("webdav_password", "") ?: ""

        _uiState.update {
            it.copy(
                themeMode = ThemeMode.entries.getOrElse(themeOrdinal) { ThemeMode.DARK },
                biometricEnabled = biometricEnabled,
                autoLockEnabled = autoLockEnabled,
                pinEnabled = pinEnabled,
                timeCorrection = timeCorrection,
                isMaterialYou = isMaterialYou,
                webdavUrl = webdavUrl,
                webdavUser = webdavUser,
                webdavPassword = webdavPassword
            )
        }

        // Observe active accounts
        searchCollectionJob = viewModelScope.launch {
            combine(
                _searchQuery.debounce(300),
                _uiState.map { it.selectedCategory }.distinctUntilChanged(),
                _uiState.map { it.showFavoritesOnly }.distinctUntilChanged()
            ) { query, category, favOnly -> Triple(query, category, favOnly) }
                .flatMapLatest { (query, category, favOnly) ->
                    val flow = when {
                        favOnly && query.isNotBlank() -> repository.searchFavorites(query)
                        favOnly -> repository.getFavorites()
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

        // Observe categories
        viewModelScope.launch {
            repository.allCategories.collect { cats ->
                _uiState.update { it.copy(categoryModels = cats) }
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

    fun toggleFavoritesOnly() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
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

    // ---- Category CRUD ----

    fun addCategory(name: String, emoji: String = "", color: Int = 0) {
        viewModelScope.launch {
            repository.insertCategory(Category(name = name, emoji = emoji, color = color))
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            // Clear category from accounts using this category
            val accounts = repository.getAllList()
            for (acc in accounts.filter { it.category == category.name }) {
                repository.update(acc.copy(category = ""))
            }
            repository.deleteCategory(category)
            refreshCategories()
        }
    }

    fun setAccountCategory(accountId: Long, categoryName: String) {
        viewModelScope.launch {
            val acc = repository.getById(accountId) ?: return@launch
            repository.update(acc.copy(category = categoryName))
            refreshCategories()
        }
    }

    // ---- Export Formats ----

    /** Export all accounts as Google Authenticator plaintext URI lines */
    fun exportToGoogleAuth(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val accounts = repository.getAllList()
            val lines = accounts.joinToString("\n") { a ->
                val type = when (a.accountType) {
                    "HOTP" -> "hotp"
                    else -> "totp"
                }
                val label = if (a.name.isNotEmpty()) "${a.issuer}:${a.name}" else a.issuer
                val encodedLabel = java.net.URLEncoder.encode(label, "UTF-8")
                val encodedIssuer = java.net.URLEncoder.encode(a.issuer, "UTF-8")
                "otpauth://$type/$encodedLabel?secret=${a.secret}&issuer=$encodedIssuer&digits=${a.digits}" +
                    if (a.accountType == "HOTP") "&counter=${a.hotpCounter}" else "&period=${a.period}"
            }
            onResult(lines)
        }
    }

    /** Export all accounts in Aegis JSON format */
    fun exportToAegis(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val accounts = repository.getAllList()
            val arr = JSONArray()
            for (a in accounts) {
                val entry = JSONObject()
                val type = when (a.accountType) {
                    "HOTP" -> "hotp"
                    "STEAM" -> "steam"
                    else -> "totp"
                }
                val info = JSONObject()
                info.put("secret", a.secret)
                info.put("algo", "SHA1")
                info.put("digits", a.digits)
                if (a.accountType == "HOTP") {
                    info.put("counter", a.hotpCounter)
                } else {
                    info.put("period", a.period)
                }
                entry.put("type", type)
                entry.put("uuid", java.util.UUID.randomUUID().toString())
                entry.put("name", if (a.name.isNotEmpty()) "${a.issuer} ($a.name)" else a.issuer)
                entry.put("issuer", a.issuer)
                entry.put("note", a.note)
                entry.put("icon", null as String?)
                entry.put("info", info)
                arr.put(entry)
            }
            val root = JSONObject()
            root.put("version", 1)
            root.put("entries", arr)
            onResult(root.toString(2))
        }
    }

    // ---- WebDAV Sync ----

    fun saveWebdavConfig(url: String, user: String, password: String) {
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("webdav_url", url)
            .putString("webdav_user", user)
            .putString("webdav_password", password)
            .apply()
        _uiState.update { it.copy(webdavUrl = url, webdavUser = user, webdavPassword = password) }
    }

    /** Upload encrypted backup to WebDAV. Returns error string or null on success. */
    fun uploadToWebdav(password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state.webdavUrl.isBlank()) { onResult("未配置 WebDAV"); return@launch }
                val accounts = repository.getAllList()
                val json = buildExportJson(accounts)
                val encrypted = CryptoUtils.encrypt(json, password) ?: run {
                    onResult("加密失败"); return@launch
                }

                val url = java.net.URL(state.webdavUrl.trimEnd('/') + "/2fa-backup.enc")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "PUT"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                val auth = state.webdavUser + ":" + state.webdavPassword
                val encoded = Base64.getEncoder().encodeToString(auth.toByteArray())
                conn.setRequestProperty("Authorization", "Basic $encoded")
                conn.outputStream.use { it.write(encrypted.toByteArray()) }
                val code = conn.responseCode
                conn.disconnect()
                onResult(if (code in 200..299) null else "上传失败: HTTP $code")
            } catch (e: Exception) {
                onResult("上传错误: ${e.message}")
            }
        }
    }

    /** Download encrypted backup from WebDAV. */
    fun downloadFromWebdav(password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state.webdavUrl.isBlank()) { onResult("未配置 WebDAV"); return@launch }

                val url = java.net.URL(state.webdavUrl.trimEnd('/') + "/2fa-backup.enc")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                val auth = state.webdavUser + ":" + state.webdavPassword
                val encoded = Base64.getEncoder().encodeToString(auth.toByteArray())
                conn.setRequestProperty("Authorization", "Basic $encoded")

                val code = conn.responseCode
                if (code !in 200..299) {
                    conn.disconnect()
                    onResult("下载失败: HTTP $code"); return@launch
                }
                val encrypted = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                conn.disconnect()

                val json = CryptoUtils.decrypt(encrypted, password)
                if (json == null) { onResult("密码错误"); return@launch }
                importFromJson(json) { count ->
                    onResult(if (count > 0) null else "未导入任何账户")
                }
            } catch (e: Exception) {
                onResult("下载错误: ${e.message}")
            }
        }
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

    // ---- Theme & Settings ----

    fun cycleTheme() {
        val current = _uiState.value.themeMode
        val next = when (current) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
        }
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("theme_mode", next.ordinal).apply()
        _uiState.update { it.copy(themeMode = next) }
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

    fun toggleMaterialYou() {
        val current = _uiState.value.isMaterialYou
        val prefs = getApplication<App>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("material_you", !current).apply()
        _uiState.update { it.copy(isMaterialYou = !current) }
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
