use std::fs::File;
use std::io::{BufReader, Read};
use std::time::Instant;
use sha2::{Digest, Sha256};
use subtle::ConstantTimeEq;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum RustCoreError {
    #[error("I/O error: {msg}")]
    IoError { msg: String },
    #[error("Security verification failed: {msg}")]
    SecurityError { msg: String },
    #[error("Invalid input or format: {msg}")]
    InvalidFormat { msg: String },
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RustSha256Result {
    pub sha256_hex: String,
    pub bytes_hashed: u64,
    pub elapsed_micros: u64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RustSha256VerifyResult {
    pub actual_sha256_hex: String,
    pub matches: bool,
    pub bytes_hashed: u64,
    pub elapsed_micros: u64,
}

pub fn normalize_sha256_hex(raw: &str) -> Option<String> {
    let trimmed = raw.trim();
    let no_prefix = trimmed
        .strip_prefix("sha256:")
        .or_else(|| trimmed.strip_prefix("SHA256:"))
        .unwrap_or(trimmed);
    let cleaned: String = no_prefix
        .chars()
        .filter(|&c| c != ':' && c != ' ')
        .map(|c| c.to_ascii_lowercase())
        .collect();
    if cleaned.len() == 64 && cleaned.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f')) {
        Some(cleaned)
    } else {
        None
    }
}

pub fn constant_time_hex_eq(a: &str, b: &str) -> bool {
    let ab = a.to_ascii_lowercase();
    let bb = b.to_ascii_lowercase();
    if ab.len() != bb.len() {
        return false;
    }
    ab.as_bytes().ct_eq(bb.as_bytes()).into()
}

pub fn compute_file_sha256(file_path: &str) -> Result<RustSha256Result, RustCoreError> {
    let start = Instant::now();
    let file = File::open(file_path).map_err(|e| RustCoreError::IoError {
        msg: format!("Failed to open '{file_path}': {e}"),
    })?;
    let mut reader = BufReader::with_capacity(64 * 1024, file);
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    let mut total: u64 = 0;

    loop {
        let read = reader.read(&mut buffer).map_err(|e| RustCoreError::IoError {
            msg: format!("Read error on '{file_path}': {e}"),
        })?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
        total += read as u64;
    }

    let digest = hasher.finalize();
    Ok(RustSha256Result {
        sha256_hex: hex::encode(digest),
        bytes_hashed: total,
        elapsed_micros: start.elapsed().as_micros() as u64,
    })
}

pub fn verify_file_sha256(
    file_path: &str,
    expected_sha256: &str,
) -> Result<RustSha256VerifyResult, RustCoreError> {
    let normalized_expected =
        normalize_sha256_hex(expected_sha256).ok_or_else(|| RustCoreError::SecurityError {
            msg: format!("Malformed expected SHA-256 digest '{expected_sha256}'"),
        })?;
    let computed = compute_file_sha256(file_path)?;
    let matches = constant_time_hex_eq(&computed.sha256_hex, &normalized_expected);
    Ok(RustSha256VerifyResult {
        actual_sha256_hex: computed.sha256_hex,
        matches,
        bytes_hashed: computed.bytes_hashed,
        elapsed_micros: computed.elapsed_micros,
    })
}
