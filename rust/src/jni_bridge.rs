use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, jstring};
use jni::JNIEnv;

use crate::crypto;
use crate::database::Database;
use crate::export;
use crate::hotp;
use crate::models::{Account, Category};
use crate::steam;
use crate::totp;

use std::sync::Once;

static INIT: Once = Once::new();
static mut DB: Option<Database> = None;

/// Initialize the database. Must be called once from Kotlin with the app's data directory path.
#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeInit(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
) {
    INIT.call_once(|| {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Info)
                .with_tag("auth2fa-rust"),
        );
    });

    let path: String = jstr_to_string(&mut env, &db_path);
    match Database::open(std::path::Path::new(&path)) {
        Ok(db) => unsafe {
            DB = Some(db);
            log::info!("Database initialized at {}", path);
        }
        Err(e) => {
            log::error!("Failed to open database: {}", e);
        }
    }
}

/// Helper to get a reference to the database.
fn db() -> &'static Database {
    unsafe { DB.as_ref().expect("Database not initialized. Call nativeInit first.") }
}

/// Convert a JString to a Rust String.
///
/// `env.get_string()` returns `Result<JavaStr, Error>`, and `JavaStr` does not
/// implement `Default`, so `.unwrap_or_default()` on the Result does not compile.
/// Map to String first (via the documented `Into<String>` conversion), then
/// default to empty on error.
fn jstr_to_string(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s)
        .map(|js| String::from(js))
        .unwrap_or_default()
}

// ═══════════════════════════════════════════════════
// TOTP / HOTP / Steam Generation
// ═══════════════════════════════════════════════════

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeGenerateTotp(
    mut env: JNIEnv,
    _class: JClass,
    secret: JString,
    digits: jint,
    period: jint,
    now_seconds: jlong,
) -> jstring {
    let secret: String = jstr_to_string(&mut env, &secret);
    match totp::generate(&secret, digits as u32, period as u64, now_seconds as u64) {
        Ok(code) => env.new_string(code).unwrap().into_raw(),
        Err(e) => env.new_string(format!("ERR:{}", e)).unwrap().into_raw(),
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeGenerateHotp(
    mut env: JNIEnv,
    _class: JClass,
    secret: JString,
    counter: jlong,
    digits: jint,
) -> jstring {
    let secret: String = jstr_to_string(&mut env, &secret);
    match hotp::generate(&secret, counter as u64, digits as u32) {
        Ok(code) => env.new_string(code).unwrap().into_raw(),
        Err(e) => env.new_string(format!("ERR:{}", e)).unwrap().into_raw(),
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeGenerateSteam(
    mut env: JNIEnv,
    _class: JClass,
    secret: JString,
    now_seconds: jlong,
) -> jstring {
    let secret: String = jstr_to_string(&mut env, &secret);
    match steam::generate(&secret, now_seconds as u64) {
        Ok(code) => env.new_string(code).unwrap().into_raw(),
        Err(e) => env.new_string(format!("ERR:{}", e)).unwrap().into_raw(),
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeGetTimeRemaining(
    _env: JNIEnv,
    _class: JClass,
    period: jint,
    now_seconds: jlong,
) -> jint {
    totp::get_time_remaining(period as u64, now_seconds as u64) as jint
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeGetProgress(
    _env: JNIEnv,
    _class: JClass,
    period: jint,
    now_seconds: jlong,
) -> jfloat {
    totp::get_progress(period as u64, now_seconds as u64)
}

// ═══════════════════════════════════════════════════
// Database Operations - Accounts
// ═══════════════════════════════════════════════════

fn account_to_json_string(a: &Account) -> String {
    serde_json::to_string(a).unwrap_or_default()
}

fn category_to_json_string(c: &Category) -> String {
    serde_json::to_string(c).unwrap_or_default()
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeGetAllAccounts(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let accounts = match db().get_all_accounts() {
        Ok(a) => a,
        Err(_e) => return env.new_string(format!("[]")).unwrap().into_raw(),
    };
    let json = serde_json::to_string(&accounts).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeSearchAccounts(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
) -> jstring {
    let query: String = jstr_to_string(&mut env, &query);
    let accounts = match db().search_accounts(&query) {
        Ok(a) => a,
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };
    let json = serde_json::to_string(&accounts).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeGetTrashedAccounts(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let accounts = match db().get_trashed_accounts() {
        Ok(a) => a,
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };
    let json = serde_json::to_string(&accounts).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeInsertAccount(
    mut env: JNIEnv,
    _class: JClass,
    issuer: JString,
    name: JString,
    secret: JString,
    digits: jint,
    period: jint,
    account_type: JString,
) -> jlong {
    let issuer: String = jstr_to_string(&mut env, &issuer);
    let name: String = jstr_to_string(&mut env, &name);
    let secret: String = jstr_to_string(&mut env, &secret);
    let account_type: String = jstr_to_string(&mut env, &account_type);

    let cleaned = Account {
        issuer: issuer.trim().to_string(),
        name: name.trim().to_string(),
        secret: secret
            .to_uppercase()
            .chars()
            .filter(|c| matches!(c, 'A'..='Z' | '2'..='7'))
            .collect(),
        digits,
        period,
        account_type,
        created_at: chrono::Utc::now().timestamp_millis(),
        ..Default::default()
    };

    match db().insert_account(&cleaned) {
        Ok(id) => id,
        Err(e) => {
            log::error!("Insert account failed: {}", e);
            -1
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeUpdateAccount(
    mut env: JNIEnv,
    _class: JClass,
    account_json: JString,
) -> jboolean {
    let json: String = jstr_to_string(&mut env, &account_json);
    match serde_json::from_str::<Account>(&json) {
        Ok(account) => match db().update_account(&account) {
            Ok(()) => true as jboolean,
            Err(e) => {
                log::error!("Update account failed: {}", e);
                false as jboolean
            }
        },
        Err(e) => {
            log::error!("JSON parse error: {}", e);
            false as jboolean
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeSoftDelete(
    _env: JNIEnv,
    _class: JClass,
    id: jlong,
) -> jboolean {
    match db().soft_delete(id) {
        Ok(()) => true as jboolean,
        Err(_) => false as jboolean,
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeRestore(
    _env: JNIEnv,
    _class: JClass,
    id: jlong,
) -> jboolean {
    match db().restore(id) {
        Ok(()) => true as jboolean,
        Err(_) => false as jboolean,
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeDeleteAccount(
    _env: JNIEnv,
    _class: JClass,
    id: jlong,
) -> jboolean {
    match db().delete_account(id) {
        Ok(()) => true as jboolean,
        Err(_) => false as jboolean,
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeClearTrash(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    match db().clear_trash() {
        Ok(()) => true as jboolean,
        Err(_) => false as jboolean,
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeToggleFavorite(
    _env: JNIEnv,
    _class: JClass,
    id: jlong,
) -> jboolean {
    match db().toggle_favorite(id) {
        Ok(_) => true as jboolean,
        Err(_) => false as jboolean,
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeUpdateHotpCounter(
    _env: JNIEnv,
    _class: JClass,
    id: jlong,
    counter: jlong,
) -> jboolean {
    match db().update_hotp_counter(id, counter) {
        Ok(()) => true as jboolean,
        Err(_) => false as jboolean,
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeBatchSetCategory(
    mut env: JNIEnv,
    _class: JClass,
    ids_json: JString,
    category: JString,
) -> jboolean {
    let ids_str: String = jstr_to_string(&mut env, &ids_json);
    let category: String = jstr_to_string(&mut env, &category);
    match serde_json::from_str::<Vec<i64>>(&ids_str) {
        Ok(ids) => match db().batch_set_category(&ids, &category) {
            Ok(()) => true as jboolean,
            Err(_) => false as jboolean,
        },
        Err(_) => false as jboolean,
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeCountAccounts(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    db().count_accounts().unwrap_or(0)
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeGetCategoryNames(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let categories = db().get_all_categories_list().unwrap_or_default();
    let json = serde_json::to_string(&categories).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeBatchDeleteAccounts(
    mut env: JNIEnv,
    _class: JClass,
    ids_json: JString,
) -> jboolean {
    let ids_str: String = jstr_to_string(&mut env, &ids_json);
    match serde_json::from_str::<Vec<i64>>(&ids_str) {
        Ok(ids) => match db().delete_accounts(&ids) {
            Ok(()) => true as jboolean,
            Err(_) => false as jboolean,
        },
        Err(_) => false as jboolean,
    }
}

// ═══════════════════════════════════════════════════
// Database Operations - Categories
// ═══════════════════════════════════════════════════

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeGetCategories(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let categories = match db().get_categories() {
        Ok(c) => c,
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };
    let json = serde_json::to_string(&categories).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeInsertCategory(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
    emoji: JString,
    color: jint,
) -> jlong {
    let name: String = jstr_to_string(&mut env, &name);
    let emoji: String = jstr_to_string(&mut env, &emoji);
    let category = Category {
        name,
        emoji,
        color,
        ..Default::default()
    };
    match db().insert_category(&category) {
        Ok(id) => id,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeDeleteCategory(
    _env: JNIEnv,
    _class: JClass,
    id: jlong,
) -> jboolean {
    match db().delete_category(id) {
        Ok(()) => true as jboolean,
        Err(_) => false as jboolean,
    }
}

// ═══════════════════════════════════════════════════
// Export / Import
// ═══════════════════════════════════════════════════

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeExportJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let accounts = db().get_all_accounts().unwrap_or_default();
    let json = export::export_accounts(&accounts);
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeExportSelectedJson(
    mut env: JNIEnv,
    _class: JClass,
    ids_json: JString,
) -> jstring {
    let ids_str: String = jstr_to_string(&mut env, &ids_json);
    let all = db().get_all_accounts().unwrap_or_default();
    if let Ok(ids) = serde_json::from_str::<Vec<i64>>(&ids_str) {
        let selected: Vec<Account> = all.into_iter().filter(|a| ids.contains(&a.id)).collect();
        let json = export::export_accounts(&selected);
        env.new_string(json).unwrap().into_raw()
    } else {
        env.new_string("[]").unwrap().into_raw()
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeImportJson(
    mut env: JNIEnv,
    _class: JClass,
    json_str: JString,
) -> jint {
    let json: String = jstr_to_string(&mut env, &json_str);
    match export::import_accounts(&json) {
        Ok(accounts) => {
            let count = accounts.len() as i32;
            match db().insert_accounts(&accounts) {
                Ok(()) => count,
                Err(e) => {
                    log::error!("Import failed: {}", e);
                    -1
                }
            }
        }
        Err(e) => {
            log::error!("Import parse error: {}", e);
            -1
        }
    }
}

// ═══════════════════════════════════════════════════
// Crypto (PIN)
// ═══════════════════════════════════════════════════

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeHashPin(
    mut env: JNIEnv,
    _class: JClass,
    pin: JString,
) -> jstring {
    let pin: String = jstr_to_string(&mut env, &pin);
    let hash = crypto::hash_pin(&pin);
    env.new_string(hash).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeVerifyPin(
    mut env: JNIEnv,
    _class: JClass,
    pin: JString,
    stored_hash: JString,
) -> jboolean {
    let pin: String = jstr_to_string(&mut env, &pin);
    let hash: String = jstr_to_string(&mut env, &stored_hash);
    crypto::verify_pin(&pin, &hash) as jboolean
}

// ═══════════════════════════════════════════════════
// OTP URI Parsing
// ═══════════════════════════════════════════════════

/// Parse an otpauth:// URI and insert the account. Returns the new account ID or -1 on failure.
#[no_mangle]
pub extern "C" fn Java_com_auth2fa_app_rust_RustBridge_nativeParseAndAddUri(
    mut env: JNIEnv,
    _class: JClass,
    uri: JString,
) -> jlong {
    let uri: String = jstr_to_string(&mut env, &uri);

    if !uri.starts_with("otpauth://") {
        return -1;
    }

    // Parse host (totp or hotp)
    let host_end = uri.find('?').unwrap_or(uri.len());
    let host_part = &uri[10..host_end]; // skip "otpauth://"

    let account_type = if host_part.starts_with("hotp") {
        "HOTP"
    } else if host_part.starts_with("totp") {
        "TOTP"
    } else {
        return -1;
    };

    // Parse query parameters
    let query_start = uri.find('?').unwrap_or(uri.len()) + 1;
    let query = &uri[query_start..];
    let params: std::collections::HashMap<String, String> = query
        .split('&')
        .filter_map(|pair| {
            let mut parts = pair.splitn(2, '=');
            let key = parts.next()?.to_string();
            let val = parts
                .next()
                .map(|v| {
                    urlencoding::decode(v)
                        .unwrap_or_default()
                        .to_string()
                })
                .unwrap_or_default();
            Some((key, val))
        })
        .collect();

    let secret = match params.get("secret") {
        Some(s) => s.clone(),
        None => return -1,
    };

    let issuer_from_param = params.get("issuer").cloned().unwrap_or_default();

    // Extract issuer and name from path
    let path_start = host_part.find(':').map(|i| i + 1).unwrap_or(0);
    let path = &host_part[path_start..];

    let (final_issuer, final_name) = if path.is_empty() {
        (issuer_from_param, String::new())
    } else if let Some(colon_pos) = path.find(':') {
        let issuer_part = &path[..colon_pos];
        let name_part = &path[colon_pos + 1..];
        (
            if issuer_from_param.is_empty() {
                issuer_part.to_string()
            } else {
                issuer_from_param
            },
            name_part.to_string(),
        )
    } else {
        (
            if issuer_from_param.is_empty() {
                path.to_string()
            } else {
                issuer_from_param
            },
            String::new(),
        )
    };

    let cleaned_secret: String = secret
        .to_uppercase()
        .chars()
        .filter(|c| matches!(c, 'A'..='Z' | '2'..='7'))
        .collect();

    let account = Account {
        issuer: final_issuer,
        name: final_name,
        secret: cleaned_secret,
        digits: params
            .get("digits")
            .and_then(|v| v.parse().ok())
            .unwrap_or(6),
        period: params
            .get("period")
            .and_then(|v| v.parse().ok())
            .unwrap_or(30),
        account_type: account_type.to_string(),
        created_at: chrono::Utc::now().timestamp_millis(),
        ..Default::default()
    };

    match db().insert_account(&account) {
        Ok(id) => id,
        Err(e) => {
            log::error!("Failed to insert from URI: {}", e);
            -1
        }
    }
}
