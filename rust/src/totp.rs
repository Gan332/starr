use hmac::{Hmac, Mac};
use sha1::Sha1;

type HmacSha1 = Hmac<Sha1>;

const BASE32_CHARS: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

/// Decode a Base32-encoded string to a byte vector.
pub fn base32_decode(secret: &str) -> Result<Vec<u8>, &'static str> {
    let cleaned: String = secret
        .chars()
        .filter(|c| matches!(c, 'A'..='Z' | 'a'..='z' | '2'..='7'))
        .map(|c| c.to_ascii_uppercase())
        .collect();

    let mut output = Vec::new();
    let mut buffer: u32 = 0;
    let mut bits_left: u32 = 0;

    for c in cleaned.chars() {
        let idx = BASE32_CHARS
            .iter()
            .position(|&b| b == c as u8)
            .ok_or("Invalid Base32 character")?;
        buffer = (buffer << 5) | (idx as u32);
        bits_left += 5;
        if bits_left >= 8 {
            output.push(((buffer >> (bits_left - 8)) & 0xFF) as u8);
            bits_left -= 8;
        }
    }
    Ok(output)
}

/// Generate a TOTP code for the given secret (RFC 6238).
pub fn generate(
    secret: &str,
    digits: u32,
    period: u64,
    current_time_seconds: u64,
) -> Result<String, &'static str> {
    let counter = current_time_seconds / period;
    generate_for_counter(secret, counter, digits)
}

/// Generate a TOTP code for a given counter value.
fn generate_for_counter(
    secret: &str,
    counter: u64,
    digits: u32,
) -> Result<String, &'static str> {
    // Build 8-byte big-endian counter
    let counter_bytes = counter.to_be_bytes();

    // HMAC-SHA1
    let key_bytes = base32_decode(secret)?;
    let mut mac = HmacSha1::new_from_slice(&key_bytes)
        .map_err(|_| "HMAC key error")?;
    mac.update(&counter_bytes);
    let hash = mac.finalize().into_bytes();

    // Dynamic truncation (RFC 4226)
    let offset = (hash[19] & 0x0F) as usize;
    let binary = ((hash[offset] as u32 & 0x7F) << 24)
        | ((hash[offset + 1] as u32) << 16)
        | ((hash[offset + 2] as u32) << 8)
        | (hash[offset + 3] as u32);

    let otp = binary % 10u32.pow(digits);
    Ok(format!("{:0>width$}", otp, width = digits as usize))
}

/// Get remaining seconds in the current time period.
pub fn get_time_remaining(period: u64, current_time_seconds: u64) -> u32 {
    (period - (current_time_seconds % period)) as u32
}

/// Get progress (0.0 - 1.0) through the current time period.
pub fn get_progress(period: u64, current_time_seconds: u64) -> f32 {
    (current_time_seconds % period) as f32 / period as f32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_base32_decode() {
        let result = base32_decode("JBSWY3DPEHPK3PXP");
        assert!(result.is_ok());
        let bytes = result.unwrap();
        assert_eq!(bytes, b"Hello!\xde\xad\xbe\xef");
    }

    #[test]
    fn test_generate_returns_correct_length() {
        let code = generate("JBSWY3DPEHPK3PXP", 6, 30, 1234567890);
        assert!(code.is_ok());
        assert_eq!(code.unwrap().len(), 6);
    }
}
