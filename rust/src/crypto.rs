use sha2::{Digest, Sha256};

/// Hash a PIN using SHA-256 and return hex string.
pub fn hash_pin(pin: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(pin.as_bytes());
    let result = hasher.finalize();
    hex::encode(result)
}

/// Verify a PIN against a stored SHA-256 hash.
pub fn verify_pin(pin: &str, stored_hash: &str) -> bool {
    hash_pin(pin) == stored_hash
}

/// Parse a base32 secret for display (uppercase, cleaned).
pub fn clean_secret(secret: &str) -> String {
    secret
        .chars()
        .filter(|c| matches!(c, 'A'..='Z' | 'a'..='z' | '2'..='7'))
        .map(|c| c.to_ascii_uppercase())
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hash_pin_deterministic() {
        let h1 = hash_pin("1234");
        let h2 = hash_pin("1234");
        assert_eq!(h1, h2);
        assert_eq!(h1.len(), 64); // SHA-256 hex = 64 chars
    }

    #[test]
    fn test_verify_pin() {
        let h = hash_pin("5678");
        assert!(verify_pin("5678", &h));
        assert!(!verify_pin("0000", &h));
    }
}
