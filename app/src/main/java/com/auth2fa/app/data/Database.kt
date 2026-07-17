package com.auth2fa.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import androidx.room.*

/**
 * Room entity representing a 2FA account.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "issuer") val issuer: String,
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "secret") val secret: String,
    @ColumnInfo(name = "digits") val digits: Int = 6,
    @ColumnInfo(name = "period") val period: Int = 30,
    @ColumnInfo(name = "icon_color") val iconColor: Int = 0,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "category") val category: String = "",
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "custom_emoji") val customEmoji: String = "",
    @ColumnInfo(name = "custom_color") val customColor: Int = 0,
    @ColumnInfo(name = "account_type") val accountType: String = "TOTP",
    @ColumnInfo(name = "tags") val tags: String = "",
    @ColumnInfo(name = "hotp_counter") val hotpCounter: Long = 0,
    @ColumnInfo(name = "is_trashed") val isTrashed: Boolean = false,
    @ColumnInfo(name = "trashed_at") val trashedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "emoji") val emoji: String = "",
    @ColumnInfo(name = "color") val color: Int = 0,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0
)

@Dao
interface AccountDao {
    // Active accounts
    @Query("SELECT * FROM accounts WHERE is_trashed = 0 ORDER BY is_favorite DESC, issuer ASC")
    fun getAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE is_trashed = 0 ORDER BY is_favorite DESC, issuer ASC")
    suspend fun getAllList(): List<Account>

    @Query("SELECT * FROM accounts WHERE is_trashed = 0 ORDER BY is_favorite DESC, createdAt DESC")
    suspend fun getAllByRecent(): List<Account>

    @Query("""
        SELECT * FROM accounts WHERE is_trashed = 0 AND (
            issuer LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%'
            OR note LIKE '%' || :query || '%' OR secret LIKE '%' || :query || '%'
            OR tags LIKE '%' || :query || '%'
        ) ORDER BY is_favorite DESC, issuer ASC
    """)
    fun search(query: String): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE is_trashed = 0 AND is_favorite = 1 ORDER BY issuer ASC")
    fun getFavorites(): Flow<List<Account>>

    @Query("""
        SELECT * FROM accounts WHERE is_trashed = 0 AND (
            issuer LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%'
            OR note LIKE '%' || :query || '%' OR secret LIKE '%' || :query || '%'
            OR tags LIKE '%' || :query || '%'
        ) AND is_favorite = 1 ORDER BY is_favorite DESC, issuer ASC
    """)
    fun searchFavorites(query: String): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE is_trashed = 0 AND category = :category ORDER BY is_favorite DESC, issuer ASC")
    fun getByCategory(category: String): Flow<List<Account>>

    @Query("SELECT DISTINCT category FROM accounts WHERE is_trashed = 0 AND category != '' ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>

    @Query("SELECT category, COUNT(*) as cnt FROM accounts WHERE is_trashed = 0 AND category != '' GROUP BY category")
    suspend fun getCategoryCounts(): List<CategoryCount>

data class CategoryCount(
    val category: String,
    val cnt: Int
)

    @Query("SELECT * FROM accounts WHERE is_trashed = 0 AND id = :id")
    suspend fun getById(id: Long): Account?

    @Query("SELECT COUNT(*) FROM accounts WHERE is_trashed = 0")
    suspend fun count(): Int

    // Trash
    @Query("SELECT * FROM accounts WHERE is_trashed = 1 ORDER BY trashed_at DESC")
    fun getTrashed(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE is_trashed = 1 ORDER BY trashed_at DESC")
    suspend fun getTrashedList(): List<Account>

    @Query("UPDATE accounts SET is_trashed = 1, trashed_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE accounts SET is_trashed = 0, trashed_at = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM accounts WHERE is_trashed = 1")
    suspend fun clearTrash()

    @Query("DELETE FROM accounts WHERE is_trashed = 1 AND trashed_at < :before")
    suspend fun purgeOldTrash(before: Long)

    // Mutations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<Account>)

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM accounts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE accounts SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE accounts SET hotp_counter = :counter WHERE id = :id")
    suspend fun updateHotpCounter(id: Long, counter: Long)

    @Query("UPDATE accounts SET category = :category WHERE id IN (:ids)")
    suspend fun batchSetCategory(ids: List<Long>, category: String)

    @Query("SELECT * FROM accounts WHERE is_trashed = 0 ORDER BY is_favorite DESC, createdAt DESC")
    fun getAllRecentFlow(): Flow<List<Account>>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sort_order ASC, name ASC")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, name ASC")
    suspend fun getAllList(): List<Category>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [Account::class, Category::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "auth2fa_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

/**
 * Repository that wraps database operations.
 */
class AccountRepository(
    private val dao: AccountDao,
    private val categoryDao: CategoryDao
) {
    val allAccounts = dao.getAll()
    val trashedAccounts = dao.getTrashed()
    val allCategories = categoryDao.getAll()

    fun search(query: String) = dao.search(query)
    fun getFavorites() = dao.getFavorites()
    fun searchFavorites(query: String) = dao.searchFavorites(query)
    fun getByCategory(category: String) = dao.getByCategory(category)

    suspend fun insert(account: Account): Long = dao.insert(account)
    suspend fun insertAll(accounts: List<Account>) = dao.insertAll(accounts)
    suspend fun update(account: Account) = dao.update(account)

    // Soft delete (move to trash)
    suspend fun softDelete(id: Long) = dao.softDelete(id)
    suspend fun restore(id: Long) = dao.restore(id)
    suspend fun clearTrash() = dao.clearTrash()
    suspend fun getTrashedList(): List<Account> = dao.getTrashedList()
    suspend fun purgeOldTrash(before: Long) = dao.purgeOldTrash(before)

    // Hard delete
    suspend fun delete(account: Account) = dao.delete(account)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun getAllList(): List<Account> = dao.getAllList()
    suspend fun getAllByRecent(): List<Account> = dao.getAllByRecent()
    suspend fun getById(id: Long): Account? = dao.getById(id)
    suspend fun count(): Int = dao.count()
    suspend fun getAllCategories(): List<String> = dao.getAllCategories()
    suspend fun setFavorite(id: Long, isFavorite: Boolean) = dao.setFavorite(id, isFavorite)
    suspend fun updateHotpCounter(id: Long, counter: Long) = dao.updateHotpCounter(id, counter)
    suspend fun batchSetCategory(ids: List<Long>, category: String) = dao.batchSetCategory(ids, category)

    fun getAllRecentFlow() = dao.getAllRecentFlow()

    // Category operations
    suspend fun insertCategory(category: Category): Long = categoryDao.insert(category)
    suspend fun updateCategory(category: Category) = categoryDao.update(category)
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)
    suspend fun deleteCategoryById(id: Long) = categoryDao.deleteById(id)
    suspend fun getCategoryByName(name: String): Category? = categoryDao.getByName(name)
    suspend fun getAllCategoriesList(): List<Category> = categoryDao.getAllList()

    suspend fun getCategoryCounts(): List<CategoryCount> = dao.getCategoryCounts()

    companion object {
        @Volatile
        private var INSTANCE: AccountRepository? = null

        fun getInstance(context: Context): AccountRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AccountRepository(
                    AppDatabase.getInstance(context).accountDao(),
                    AppDatabase.getInstance(context).categoryDao()
                ).also { INSTANCE = it }
            }
        }
    }
}
