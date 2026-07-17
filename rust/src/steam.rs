use crate::totp::base32_decode;
use hmac::{Hmac, Mac};
use sha1::Sha1;

type HmacSha1 = Hmac<Sha1>;

const STEAM_CHARS: &[u8] = b"23456789BCDFGHJKMNPQRTVWXY";
const STEAM_PERIOD: u64 = 30;

/// Generate a Steam TOTP code (5-char alphanumeric).
pub fn generate(secret: &str, current_time_seconds: u64) -> Result<String, &'static str> {
    let counter = current_time_seconds / STEAM_PERIOD;

    // Build 8-byte big-endian counter
    let counter_bytes = counter.to_be_bytes();

    // HMAC-SHA1
    let key_bytes = base32_decode(secret)?;
    let mut mac =
        HmacSha1::new_from_slice(&key_bytes).map_err(|_| "HMAC key error")?;
    mac.update(&counter_bytes);
    let hash = mac.finalize().into_bytes();

    // Steam-style code generation
    let offset = (hash[19] & 0x0F) as usize;
    let mut full_code: u32 = 0;
    for i in 0..4 {
        full_code = (full_code << 8) | (hash[offset + i] as u32);
    }
    full_code &= 0x7FFFFFFF;

    // Generate 5-character Steam code
    let mut code = String::with_capacity(5);
    let mut remaining = full_code;
    for _ in 0..5 {
        let idx = (remaining % STEAM_CHARS.len() as u32) as usize;
        code.push(STEAM_CHARS[idx] as char);
        remaining /= STEAM_CHARS.len() as u32;
    }
    Ok(code)
}

/// Get remaining seconds in the current time period.
pub fn get_time_remaining(current_time_seconds: u64) -> u32 {
    (STEAM_PERIOD - (current_time_seconds % STEAM_PERIOD)) as u32
}

/// Get progress (0.0 - 1.0) through the current time period.
pub fn get_progress(current_time_seconds: u64) -> f32 {
    (current_time_seconds % STEAM_PERIOD) as f32 / STEAM_PERIOD as f32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_steam_generate_returns_5_chars() {
        let code = generate("JBSWY3DPEHPK3PXP", 1234567890);
        assert!(code.is_ok());
        assert_eq!(code.unwrap().len(), 5);
    }
}
