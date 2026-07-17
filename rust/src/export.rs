use crate::models::Account;
use serde_json::{json, Value};

/// Serialize accounts to a JSON export string.
pub fn export_accounts(accounts: &[Account]) -> String {
    let json_arr: Vec<Value> = accounts
        .iter()
        .map(|a| {
            json!({
                "issuer": a.issuer,
                "name": a.name,
                "secret": a.secret,
                "digits": a.digits,
                "period": a.period,
                "category": a.category,
                "note": a.note,
                "custom_emoji": a.custom_emoji,
                "custom_color": a.custom_color,
                "account_type": a.account_type,
                "tags": a.tags,
            })
        })
        .collect();

    serde_json::to_string_pretty(&json_arr).unwrap_or_else(|_| "[]".to_string())
}

/// Parse a JSON import string into a list of accounts.
pub fn import_accounts(json_str: &str) -> Result<Vec<Account>, String> {
    let arr: Vec<Value> =
        serde_json::from_str(json_str).map_err(|e| format!("JSON parse error: {}", e))?;

    let mut accounts = Vec::with_capacity(arr.len());
    for (i, obj) in arr.iter().enumerate() {
        let issuer = obj
            .get("issuer")
            .and_then(|v| v.as_str())
            .ok_or_else(|| format!("Account #{} missing 'issuer'", i))?
            .to_string();
        let name = obj
            .get("name")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        let secret = obj
            .get("secret")
            .and_then(|v| v.as_str())
            .ok_or_else(|| format!("Account #{} missing 'secret'", i))?
            .to_string();
        let digits = obj.get("digits").and_then(|v| v.as_i64()).unwrap_or(6) as i32;
        let period = obj.get("period").and_then(|v| v.as_i64()).unwrap_or(30) as i32;
        let category = obj
            .get("category")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        let note = obj
            .get("note")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        let custom_emoji = obj
            .get("custom_emoji")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        let custom_color = obj.get("custom_color").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
        let account_type = obj
            .get("account_type")
            .and_then(|v| v.as_str())
            .unwrap_or("TOTP")
            .to_string();
        let tags = obj
            .get("tags")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();

        let cleaned_secret = secret
            .to_uppercase()
            .chars()
            .filter(|c| matches!(c, 'A'..='Z' | '2'..='7'))
            .collect();

        accounts.push(Account {
            id: 0,
            issuer,
            name,
            secret: cleaned_secret,
            digits,
            period,
            icon_color: 0,
            is_favorite: false,
            category,
            note,
            custom_emoji,
            custom_color,
            account_type,
            tags,
            hotp_counter: 0,
            is_trashed: false,
            trashed_at: None,
            created_at: chrono::Utc::now().timestamp_millis(),
        });
    }

    Ok(accounts)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_export_import_roundtrip() {
        let accounts = vec![Account {
            id: 0,
            issuer: "GitHub".to_string(),
            name: "user@email.com".to_string(),
            secret: "JBSWY3DPEHPK3PXP".to_string(),
            digits: 6,
            period: 30,
            ..Default::default()
        }];

        let json = export_accounts(&accounts);
        let imported = import_accounts(&json);
        assert!(imported.is_ok());
        let imported = imported.unwrap();
        assert_eq!(imported.len(), 1);
        assert_eq!(imported[0].issuer, "GitHub");
    }
}
