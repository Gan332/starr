package com.auth2fa.app.data

import android.content.Context
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
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * Data class for exporting/importing accounts as JSON.
 */
data class AccountExport(
    val issuer: String,
    val name: String,
    val secret: String,
    val digits: Int = 6,
    val period: Int = 30
)

/**
 * Room DAO for account operations.
 */
@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY issuer ASC")
    fun getAll(): kotlinx.coroutines.flow.Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY issuer ASC")
    suspend fun getAllList(): List<Account>

    @Query("SELECT * FROM accounts WHERE issuer LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' ORDER BY issuer ASC")
    fun search(query: String): kotlinx.coroutines.flow.Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<Account>)

    @Delete
    suspend fun delete(account: Account)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}

/**
 * Room database for the app.
 */
@Database(entities = [Account::class], version = 1, exportSchema = false)
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

    suspend fun insert(account: Account): Long = dao.insert(account)

    suspend fun insertAll(accounts: List<Account>) = dao.insertAll(accounts)

    suspend fun delete(account: Account) = dao.delete(account)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun getAllList(): List<Account> = dao.getAllList()

    suspend fun count(): Int = dao.count()

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
