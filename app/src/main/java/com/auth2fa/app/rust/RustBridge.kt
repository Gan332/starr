package com.auth2fa.app.rust

import org.json.JSONArray
import org.json.JSONObject

/**
 * JNI bridge to the Rust core library.
 * All core logic (TOTP generation, database, crypto) is handled in Rust.
 */
object RustBridge {

    init {
        System.loadLibrary("auth2fa_core")
    }

    // ─── Native Init ───
    private external fun nativeInit(dbPath: String)

    fun init(dbPath: String) {
        nativeInit(dbPath)
    }

    // ─── OTP Generation ───
    private external fun nativeGenerateTotp(secret: String, digits: Int, period: Int, nowSeconds: Long): String
    private external fun nativeGenerateHotp(secret: String, counter: Long, digits: Int): String
    private external fun nativeGenerateSteam(secret: String, nowSeconds: Long): String
    private external fun nativeGetTimeRemaining(period: Int, nowSeconds: Long): Int
    private external fun nativeGetProgress(period: Int, nowSeconds: Long): Float

    fun generateTotp(secret: String, digits: Int = 6, period: Int = 30, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        return nativeGenerateTotp(secret, digits, period, nowSeconds)
    }

    fun generateHotp(secret: String, counter: Long, digits: Int = 6): String {
        return nativeGenerateHotp(secret, counter, digits)
    }

    fun generateSteam(secret: String, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        return nativeGenerateSteam(secret, nowSeconds)
    }

    fun getTimeRemaining(period: Int = 30, nowSeconds: Long = System.currentTimeMillis() / 1000): Int {
        return nativeGetTimeRemaining(period, nowSeconds)
    }

    fun getProgress(period: Int = 30, nowSeconds: Long = System.currentTimeMillis() / 1000): Float {
        return nativeGetProgress(period, nowSeconds)
    }

    // ─── Account Operations ───
    private external fun nativeGetAllAccounts(): String
    private external fun nativeSearchAccounts(query: String): String
    private external fun nativeGetTrashedAccounts(): String
    private external fun nativeInsertAccount(issuer: String, name: String, secret: String, digits: Int, period: Int, accountType: String): Long
    private external fun nativeUpdateAccount(accountJson: String): Boolean
    private external fun nativeSoftDelete(id: Long): Boolean
    private external fun nativeRestore(id: Long): Boolean
    private external fun nativeDeleteAccount(id: Long): Boolean
    private external fun nativeClearTrash(): Boolean
    private external fun nativeToggleFavorite(id: Long): Boolean
    private external fun nativeUpdateHotpCounter(id: Long, counter: Long): Boolean
    private external fun nativeBatchSetCategory(idsJson: String, category: String): Boolean
    private external fun nativeBatchDeleteAccounts(idsJson: String): Boolean
    private external fun nativeCountAccounts(): Long
    private external fun nativeGetCategoryNames(): String
    private external fun nativeParseAndAddUri(uri: String): Long

    // Public wrappers that return parsed objects
    fun getAllAccounts(): List<Map<String, Any?>> {
        val json = nativeGetAllAccounts()
        return parseAccountList(json)
    }

    fun searchAccounts(query: String): List<Map<String, Any?>> {
        val json = nativeSearchAccounts(query)
        return parseAccountList(json)
    }

    fun getTrashedAccounts(): List<Map<String, Any?>> {
        val json = nativeGetTrashedAccounts()
        return parseAccountList(json)
    }

    fun insertAccount(issuer: String, name: String, secret: String, accountType: String = "TOTP", digits: Int = 6, period: Int = 30): Long {
        return nativeInsertAccount(issuer, name, secret, digits, period, accountType)
    }

    fun updateAccount(accountJson: String): Boolean = nativeUpdateAccount(accountJson)
    fun softDelete(id: Long): Boolean = nativeSoftDelete(id)
    fun restore(id: Long): Boolean = nativeRestore(id)
    fun deleteAccount(id: Long): Boolean = nativeDeleteAccount(id)
    fun clearTrash(): Boolean = nativeClearTrash()
    fun toggleFavorite(id: Long): Boolean = nativeToggleFavorite(id)
    fun updateHotpCounter(id: Long, counter: Long): Boolean = nativeUpdateHotpCounter(id, counter)
    fun countAccounts(): Long = nativeCountAccounts()
    fun parseAndAddUri(uri: String): Long = nativeParseAndAddUri(uri)

    fun batchSetCategory(ids: List<Long>, category: String): Boolean {
        val json = JSONArray(ids).toString()
        return nativeBatchSetCategory(json, category)
    }

    fun batchDeleteAccounts(ids: List<Long>): Boolean {
        val json = JSONArray(ids).toString()
        return nativeBatchDeleteAccounts(json)
    }

    fun getCategoryNames(): List<String> {
        val json = nativeGetCategoryNames()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─── Category Operations ───
    private external fun nativeGetCategories(): String
    private external fun nativeInsertCategory(name: String, emoji: String, color: Int): Long
    private external fun nativeDeleteCategory(id: Long): Boolean

    fun getCategories(): List<Map<String, Any?>> {
        val json = nativeGetCategories()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                mapOf(
                    "id" to obj.getLong("id"),
                    "name" to obj.getString("name"),
                    "emoji" to obj.getString("emoji"),
                    "color" to obj.getInt("color"),
                    "sort_order" to obj.getInt("sort_order"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun insertCategory(name: String, emoji: String, color: Int): Long {
        return nativeInsertCategory(name, emoji, color)
    }

    fun deleteCategory(id: Long): Boolean = nativeDeleteCategory(id)

    // ─── Export / Import ───
    private external fun nativeExportJson(): String
    private external fun nativeExportSelectedJson(idsJson: String): String
    private external fun nativeImportJson(jsonStr: String): Int

    fun exportJson(): String = nativeExportJson()

    fun exportSelectedJson(ids: List<Long>): String {
        val json = JSONArray(ids).toString()
        return nativeExportSelectedJson(json)
    }

    fun importJson(json: String): Int = nativeImportJson(json)

    // ─── Crypto ───
    private external fun nativeHashPin(pin: String): String
    private external fun nativeVerifyPin(pin: String, storedHash: String): Boolean

    fun hashPin(pin: String): String = nativeHashPin(pin)
    fun verifyPin(pin: String, storedHash: String): Boolean = nativeVerifyPin(pin, storedHash)

    // ─── Helpers ───

    private fun parseAccountList(json: String): List<Map<String, Any?>> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                mapOf(
                    "id" to obj.getLong("id"),
                    "issuer" to obj.getString("issuer"),
                    "name" to obj.getString("name"),
                    "secret" to obj.getString("secret"),
                    "digits" to obj.getInt("digits"),
                    "period" to obj.getInt("period"),
                    "icon_color" to obj.getInt("icon_color"),
                    "is_favorite" to obj.getBoolean("is_favorite"),
                    "category" to obj.getString("category"),
                    "note" to obj.getString("note"),
                    "custom_emoji" to obj.getString("custom_emoji"),
                    "custom_color" to obj.getInt("custom_color"),
                    "account_type" to obj.getString("account_type"),
                    "tags" to obj.getString("tags"),
                    "hotp_counter" to obj.getLong("hotp_counter"),
                    "is_trashed" to obj.getBoolean("is_trashed"),
                    "trashed_at" to if (obj.isNull("trashed_at")) null else obj.getLong("trashed_at"),
                    "created_at" to obj.getLong("created_at"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
