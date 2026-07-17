use crate::totp::base32_decode;
use hmac::{Hmac, Mac};
use sha1::Sha1;

type HmacSha1 = Hmac<Sha1>;

/// Generate an HOTP code (RFC 4226).
pub fn generate(secret: &str, counter: u64, digits: u32) -> Result<String, &'static str> {
    // Build 8-byte big-endian counter
    let counter_bytes = counter.to_be_bytes();

    // HMAC-SHA1
    let key_bytes = base32_decode(secret)?;
    let mut mac =
        HmacSha1::new_from_slice(&key_bytes).map_err(|_| "HMAC key error")?;
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hotp_returns_correct_length() {
        let code = generate("JBSWY3DPEHPK3PXP", 0, 6);
        assert!(code.is_ok());
        assert_eq!(code.unwrap().len(), 6);
    }
}
