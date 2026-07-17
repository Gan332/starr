use crate::models::{Account, Category};
use rusqlite::{params, Connection, Result as SqlResult};
use std::path::Path;
use std::sync::Mutex;

/// Database wrapper providing thread-safe access to SQLite.
pub struct Database {
    conn: Mutex<Connection>,
}

/// SQL for creating the accounts table.
const CREATE_ACCOUNTS: &str = r#"
CREATE TABLE IF NOT EXISTS accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    issuer TEXT NOT NULL,
    name TEXT NOT NULL DEFAULT '',
    secret TEXT NOT NULL,
    digits INTEGER NOT NULL DEFAULT 6,
    period INTEGER NOT NULL DEFAULT 30,
    icon_color INTEGER NOT NULL DEFAULT 0,
    is_favorite INTEGER NOT NULL DEFAULT 0,
    category TEXT NOT NULL DEFAULT '',
    note TEXT NOT NULL DEFAULT '',
    custom_emoji TEXT NOT NULL DEFAULT '',
    custom_color INTEGER NOT NULL DEFAULT 0,
    account_type TEXT NOT NULL DEFAULT 'TOTP',
    tags TEXT NOT NULL DEFAULT '',
    hotp_counter INTEGER NOT NULL DEFAULT 0,
    is_trashed INTEGER NOT NULL DEFAULT 0,
    trashed_at INTEGER,
    created_at INTEGER NOT NULL
)"#;

/// SQL for creating the categories table.
const CREATE_CATEGORIES: &str = r#"
CREATE TABLE IF NOT EXISTS categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    emoji TEXT NOT NULL DEFAULT '',
    color INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0
)"#;

impl Database {
    /// Open or create a database at the given path.
    pub fn open(path: &Path) -> SqlResult<Self> {
        let conn = Connection::open(path)?;

        // Enable WAL mode for better concurrency
        conn.execute_batch("PRAGMA journal_mode=WAL;")?;
        conn.execute_batch(CREATE_ACCOUNTS)?;
        conn.execute_batch(CREATE_CATEGORIES)?;

        Ok(Self {
            conn: Mutex::new(conn),
        })
    }

    // ──────────────────────── Accounts ────────────────────────

    /// Get all active (non-trashed) accounts, sorted by favorite then issuer.
    pub fn get_all_accounts(&self) -> SqlResult<Vec<Account>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT * FROM accounts WHERE is_trashed = 0 ORDER BY is_favorite DESC, issuer ASC",
        )?;
        let rows = stmt.query_map([], Self::row_to_account)?;
        rows.collect()
    }

    /// Get all active accounts sorted by favorite then creation time (newest first).
    pub fn get_all_accounts_by_recent(&self) -> SqlResult<Vec<Account>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT * FROM accounts WHERE is_trashed = 0 ORDER BY is_favorite DESC, created_at DESC",
        )?;
        let rows = stmt.query_map([], Self::row_to_account)?;
        rows.collect()
    }

    /// Search accounts by query string across multiple fields.
    pub fn search_accounts(&self, query: &str) -> SqlResult<Vec<Account>> {
        let conn = self.conn.lock().unwrap();
        let pattern = format!("%{}%", query);
        let mut stmt = conn.prepare(
            "SELECT * FROM accounts WHERE is_trashed = 0 AND (
                issuer LIKE ?1 OR name LIKE ?1 OR note LIKE ?1 OR
                secret LIKE ?1 OR tags LIKE ?1 OR category LIKE ?1
            ) ORDER BY is_favorite DESC, issuer ASC",
        )?;
        let rows = stmt.query_map(params![pattern], Self::row_to_account)?;
        rows.collect()
    }

    /// Get a single account by ID.
    pub fn get_account_by_id(&self, id: i64) -> SqlResult<Option<Account>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt =
            conn.prepare("SELECT * FROM accounts WHERE id = ?1 AND is_trashed = 0")?;
        let mut rows = stmt.query_map(params![id], Self::row_to_account)?;
        match rows.next() {
            Some(row) => Ok(Some(row?)),
            None => Ok(None),
        }
    }

    /// Insert a new account and return its ID.
    pub fn insert_account(&self, account: &Account) -> SqlResult<i64> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO accounts (
                issuer, name, secret, digits, period, icon_color, is_favorite,
                category, note, custom_emoji, custom_color, account_type, tags,
                hotp_counter, is_trashed, trashed_at, created_at
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17)",
            params![
                account.issuer,
                account.name,
                account.secret,
                account.digits,
                account.period,
                account.icon_color,
                account.is_favorite as i32,
                account.category,
                account.note,
                account.custom_emoji,
                account.custom_color,
                account.account_type,
                account.tags,
                account.hotp_counter,
                account.is_trashed as i32,
                account.trashed_at,
                account.created_at,
            ],
        )?;
        Ok(conn.last_insert_rowid())
    }

    /// Update an existing account.
    pub fn update_account(&self, account: &Account) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE accounts SET
                issuer = ?1, name = ?2, secret = ?3, digits = ?4, period = ?5,
                icon_color = ?6, is_favorite = ?7, category = ?8, note = ?9,
                custom_emoji = ?10, custom_color = ?11, account_type = ?12,
                tags = ?13, hotp_counter = ?14, is_trashed = ?15, trashed_at = ?16,
                created_at = ?17
            WHERE id = ?18",
            params![
                account.issuer,
                account.name,
                account.secret,
                account.digits,
                account.period,
                account.icon_color,
                account.is_favorite as i32,
                account.category,
                account.note,
                account.custom_emoji,
                account.custom_color,
                account.account_type,
                account.tags,
                account.hotp_counter,
                account.is_trashed as i32,
                account.trashed_at,
                account.created_at,
                account.id,
            ],
        )?;
        Ok(())
    }

    /// Soft-delete an account (move to trash).
    pub fn soft_delete(&self, id: i64) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        let now = chrono::Utc::now().timestamp_millis();
        conn.execute(
            "UPDATE accounts SET is_trashed = 1, trashed_at = ?1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    /// Restore an account from trash.
    pub fn restore(&self, id: i64) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE accounts SET is_trashed = 0, trashed_at = NULL WHERE id = ?1",
            params![id],
        )?;
        Ok(())
    }

    /// Permanently delete an account by ID.
    pub fn delete_account(&self, id: i64) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute("DELETE FROM accounts WHERE id = ?1", params![id])?;
        Ok(())
    }

    /// Permanently delete multiple accounts by IDs.
    pub fn delete_accounts(&self, ids: &[i64]) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        for id in ids {
            conn.execute("DELETE FROM accounts WHERE id = ?1", params![id])?;
        }
        Ok(())
    }

    /// Get all trashed accounts, newest first.
    pub fn get_trashed_accounts(&self) -> SqlResult<Vec<Account>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt =
            conn.prepare("SELECT * FROM accounts WHERE is_trashed = 1 ORDER BY trashed_at DESC")?;
        let rows = stmt.query_map([], Self::row_to_account)?;
        rows.collect()
    }

    /// Clear all trashed accounts permanently.
    pub fn clear_trash(&self) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute("DELETE FROM accounts WHERE is_trashed = 1", [])?;
        Ok(())
    }

    /// Purge trashed accounts older than `before` milliseconds.
    pub fn purge_old_trash(&self, before: i64) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "DELETE FROM accounts WHERE is_trashed = 1 AND trashed_at < ?1",
            params![before],
        )?;
        Ok(())
    }

    /// Toggle favorite status of an account.
    pub fn toggle_favorite(&self, id: i64) -> SqlResult<bool> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE accounts SET is_favorite = NOT is_favorite WHERE id = ?1",
            params![id],
        )?;
        let mut stmt = conn.prepare("SELECT is_favorite FROM accounts WHERE id = ?1")?;
        let result: bool = stmt.query_row(params![id], |row| {
            let v: i32 = row.get(0)?;
            Ok(v != 0)
        })?;
        Ok(result)
    }

    /// Update HOTP counter.
    pub fn update_hotp_counter(&self, id: i64, counter: i64) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE accounts SET hotp_counter = ?1 WHERE id = ?2",
            params![counter, id],
        )?;
        Ok(())
    }

    /// Batch set category for multiple accounts.
    pub fn batch_set_category(&self, ids: &[i64], category: &str) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        for id in ids {
            conn.execute(
                "UPDATE accounts SET category = ?1 WHERE id = ?2",
                params![category, id],
            )?;
        }
        Ok(())
    }

    /// Count all active (non-trashed) accounts.
    pub fn count_accounts(&self) -> SqlResult<i64> {
        let conn = self.conn.lock().unwrap();
        let result: i64 = conn.query_row(
            "SELECT COUNT(*) FROM accounts WHERE is_trashed = 0",
            [],
            |row| row.get(0),
        )?;
        Ok(result)
    }

    /// Get distinct category names from active accounts.
    pub fn get_all_categories_list(&self) -> SqlResult<Vec<String>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT DISTINCT category FROM accounts WHERE is_trashed = 0 AND category != '' ORDER BY category ASC",
        )?;
        let rows = stmt.query_map([], |row| row.get::<_, String>(0))?;
        rows.collect()
    }

    /// Insert multiple accounts at once (for import).
    pub fn insert_accounts(&self, accounts: &[Account]) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        for account in accounts {
            conn.execute(
                "INSERT INTO accounts (
                    issuer, name, secret, digits, period, icon_color, is_favorite,
                    category, note, custom_emoji, custom_color, account_type, tags,
                    hotp_counter, is_trashed, trashed_at, created_at
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17)",
                params![
                    account.issuer,
                    account.name,
                    account.secret,
                    account.digits,
                    account.period,
                    account.icon_color,
                    account.is_favorite as i32,
                    account.category,
                    account.note,
                    account.custom_emoji,
                    account.custom_color,
                    account.account_type,
                    account.tags,
                    account.hotp_counter,
                    account.is_trashed as i32,
                    account.trashed_at,
                    account.created_at,
                ],
            )?;
        }
        Ok(())
    }

    // ──────────────────────── Categories ────────────────────────

    /// Get all categories.
    pub fn get_categories(&self) -> SqlResult<Vec<Category>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt =
            conn.prepare("SELECT * FROM categories ORDER BY sort_order ASC, name ASC")?;
        let rows = stmt.query_map([], Self::row_to_category)?;
        rows.collect()
    }

    /// Get a category by name.
    pub fn get_category_by_name(&self, name: &str) -> SqlResult<Option<Category>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt =
            conn.prepare("SELECT * FROM categories WHERE name = ?1 LIMIT 1")?;
        let mut rows = stmt.query_map(params![name], Self::row_to_category)?;
        match rows.next() {
            Some(row) => Ok(Some(row?)),
            None => Ok(None),
        }
    }

    /// Insert a new category and return its ID.
    pub fn insert_category(&self, category: &Category) -> SqlResult<i64> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO categories (name, emoji, color, sort_order) VALUES (?1, ?2, ?3, ?4)",
            params![category.name, category.emoji, category.color, category.sort_order],
        )?;
        Ok(conn.last_insert_rowid())
    }

    /// Update an existing category.
    pub fn update_category(&self, category: &Category) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE categories SET name = ?1, emoji = ?2, color = ?3, sort_order = ?4 WHERE id = ?5",
            params![category.name, category.emoji, category.color, category.sort_order, category.id],
        )?;
        Ok(())
    }

    /// Delete a category by ID.
    pub fn delete_category(&self, id: i64) -> SqlResult<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute("DELETE FROM categories WHERE id = ?1", params![id])?;
        Ok(())
    }

    // ──────────────────────── Row Mappers ────────────────────────

    fn row_to_account(row: &rusqlite::Row) -> rusqlite::Result<Account> {
        Ok(Account {
            id: row.get("id")?,
            issuer: row.get("issuer")?,
            name: row.get("name")?,
            secret: row.get("secret")?,
            digits: row.get("digits")?,
            period: row.get("period")?,
            icon_color: row.get("icon_color")?,
            is_favorite: row.get::<_, i32>("is_favorite")? != 0,
            category: row.get("category")?,
            note: row.get("note")?,
            custom_emoji: row.get("custom_emoji")?,
            custom_color: row.get("custom_color")?,
            account_type: row.get("account_type")?,
            tags: row.get("tags")?,
            hotp_counter: row.get("hotp_counter")?,
            is_trashed: row.get::<_, i32>("is_trashed")? != 0,
            trashed_at: row.get("trashed_at")?,
            created_at: row.get("created_at")?,
        })
    }

    fn row_to_category(row: &rusqlite::Row) -> rusqlite::Result<Category> {
        Ok(Category {
            id: row.get("id")?,
            name: row.get("name")?,
            emoji: row.get("emoji")?,
            color: row.get("color")?,
            sort_order: row.get("sort_order")?,
        })
    }
}
