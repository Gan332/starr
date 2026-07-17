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
    @ColumnInfo(name = "is_steam") val isSteam: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * Room DAO for account operations.
 */
@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY is_favorite DESC, issuer ASC")
    fun getAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY is_favorite DESC, issuer ASC")
    suspend fun getAllList(): List<Account>

    @Query("SELECT * FROM accounts ORDER BY is_favorite DESC, createdAt DESC")
    suspend fun getAllByRecent(): List<Account>

    @Query("SELECT * FROM accounts WHERE issuer LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' ORDER BY is_favorite DESC, issuer ASC")
    fun search(query: String): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE category = :category ORDER BY is_favorite DESC, issuer ASC")
    fun getByCategory(category: String): Flow<List<Account>>

    @Query("SELECT DISTINCT category FROM accounts WHERE category != '' ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>

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

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("UPDATE accounts SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("SELECT * FROM accounts ORDER BY is_favorite DESC, createdAt DESC")
    fun getAllRecentFlow(): Flow<List<Account>>
}

/**
 * Room database for the app.
 */
@Database(entities = [Account::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

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
class AccountRepository(private val dao: AccountDao) {

    val allAccounts = dao.getAll()

    fun search(query: String) = dao.search(query)

    fun getByCategory(category: String) = dao.getByCategory(category)

    suspend fun insert(account: Account): Long = dao.insert(account)

    suspend fun insertAll(accounts: List<Account>) = dao.insertAll(accounts)

    suspend fun update(account: Account) = dao.update(account)

    suspend fun delete(account: Account) = dao.delete(account)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun getAllList(): List<Account> = dao.getAllList()

    suspend fun getAllByRecent(): List<Account> = dao.getAllByRecent()

    suspend fun getById(id: Long): Account? = dao.getById(id)

    suspend fun count(): Int = dao.count()

    suspend fun getAllCategories(): List<String> = dao.getAllCategories()

    suspend fun setFavorite(id: Long, isFavorite: Boolean) = dao.setFavorite(id, isFavorite)

    fun getAllRecentFlow() = dao.getAllRecentFlow()

    companion object {
        @Volatile
        private var INSTANCE: AccountRepository? = null

        fun getInstance(context: Context): AccountRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AccountRepository(
                    AppDatabase.getInstance(context).accountDao()
                ).also { INSTANCE = it }
            }
        }
    }
}
