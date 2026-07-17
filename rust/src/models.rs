use serde::{Deserialize, Serialize};

/// Account entity matching the Kotlin `Account` data class.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Account {
    pub id: i64,
    pub issuer: String,
    pub name: String,
    pub secret: String,
    pub digits: i32,
    pub period: i32,
    pub icon_color: i32,
    pub is_favorite: bool,
    pub category: String,
    pub note: String,
    pub custom_emoji: String,
    pub custom_color: i32,
    pub account_type: String,
    pub tags: String,
    pub hotp_counter: i64,
    pub is_trashed: bool,
    pub trashed_at: Option<i64>,
    pub created_at: i64,
}

impl Default for Account {
    fn default() -> Self {
        Self {
            id: 0,
            issuer: String::new(),
            name: String::new(),
            secret: String::new(),
            digits: 6,
            period: 30,
            icon_color: 0,
            is_favorite: false,
            category: String::new(),
            note: String::new(),
            custom_emoji: String::new(),
            custom_color: 0,
            account_type: "TOTP".to_string(),
            tags: String::new(),
            hotp_counter: 0,
            is_trashed: false,
            trashed_at: None,
            created_at: chrono::Utc::now().timestamp_millis(),
        }
    }
}

/// Category entity matching the Kotlin `Category` data class.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Category {
    pub id: i64,
    pub name: String,
    pub emoji: String,
    pub color: i32,
    pub sort_order: i32,
}

impl Default for Category {
    fn default() -> Self {
        Self {
            id: 0,
            name: String::new(),
            emoji: String::new(),
            color: 0,
            sort_order: 0,
        }
    }
}

/// A generated code entry for display.
#[derive(Debug, Clone)]
pub struct CodeEntry {
    pub account_id: i64,
    pub code: Option<String>,
    pub remaining_seconds: i32,
    pub progress: f32,
}

/// Sort mode for account listing.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum SortMode {
    Name,
    Recent,
    Type,
}

impl SortMode {
    pub fn from_str(s: &str) -> Self {
        match s {
            "RECENT" => SortMode::Recent,
            "TYPE" => SortMode::Type,
            _ => SortMode::Name,
        }
    }
}
