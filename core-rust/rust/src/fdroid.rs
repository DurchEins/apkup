use std::collections::{HashMap, HashSet};
use std::fmt;
use std::fs::{self, File};
use std::io::{BufReader, BufWriter, Read, Write};
use std::path::Path;
use std::time::Instant;

use base64::engine::general_purpose::STANDARD as BASE64;
use base64::Engine;
use cms::cert::CertificateChoices;
use cms::content_info::ContentInfo;
use cms::signed_data::SignedData;
use const_oid::db::rfc5911::ID_SIGNED_DATA;
use const_oid::db::rfc5912::{
    ECDSA_WITH_SHA_256, ECDSA_WITH_SHA_384, ID_EC_PUBLIC_KEY, RSA_ENCRYPTION,
    SHA_1_WITH_RSA_ENCRYPTION, SHA_256_WITH_RSA_ENCRYPTION, SHA_384_WITH_RSA_ENCRYPTION,
    SHA_512_WITH_RSA_ENCRYPTION,
};
use const_oid::ObjectIdentifier;
use der::{Decode, Encode};
use p256::ecdsa::{DerSignature as P256DerSignature, VerifyingKey as P256VerifyingKey};
use p384::ecdsa::{DerSignature as P384DerSignature, VerifyingKey as P384VerifyingKey};
use rsa::pkcs1v15::{Signature as RsaSignature, VerifyingKey as RsaVerifyingKey};
use rsa::pkcs8::DecodePublicKey;
use rsa::RsaPublicKey;
use serde::de::{DeserializeSeed, IgnoredAny, MapAccess, SeqAccess, Visitor};
use serde::Deserializer;
use serde_json::{Map, Value};
use sha1::Sha1;
use sha2::{Digest, Sha256, Sha384, Sha512};
use signature::Verifier;
use zip::ZipArchive;

use crate::hasher::{constant_time_hex_eq, normalize_sha256_hex, RustCoreError};

const MAX_JAR_ENTRIES: usize = 64;
const OID_MESSAGE_DIGEST: ObjectIdentifier = ObjectIdentifier::new_unwrap("1.2.840.113549.1.9.4");
const OID_SHA256: ObjectIdentifier = ObjectIdentifier::new_unwrap("2.16.840.1.101.3.4.2.1");
const OID_SHA384: ObjectIdentifier = ObjectIdentifier::new_unwrap("2.16.840.1.101.3.4.2.2");
const OID_SHA512: ObjectIdentifier = ObjectIdentifier::new_unwrap("2.16.840.1.101.3.4.2.3");
const OID_SHA1: ObjectIdentifier = ObjectIdentifier::new_unwrap("1.3.14.3.2.26");

#[derive(Debug, Clone, uniffi::Record)]
pub struct RustFDroidJarVerifyResult {
    pub signer_sha256_hex: String,
    pub extracted_bytes: u64,
    pub elapsed_micros: u64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RustFDroidFilteredJsonResult {
    pub filtered_json: String,
    pub matched_package_count: u32,
    pub elapsed_micros: u64,
}

fn read_zip_entry_bounded<R: Read>(
    entry: &mut zip::read::ZipFile<'_, R>,
    max_bytes: u64,
    label: &str,
) -> Result<Vec<u8>, RustCoreError> {
    if entry.size() > max_bytes {
        return Err(RustCoreError::SecurityError {
            msg: format!("{label} exceeds maximum allowed size ({max_bytes} bytes)"),
        });
    }
    let mut out = Vec::new();
    let mut buf = [0u8; 16 * 1024];
    let mut total: u64 = 0;
    loop {
        let n = entry.read(&mut buf).map_err(|e| RustCoreError::IoError {
            msg: format!("Failed reading ZIP entry '{label}': {e}"),
        })?;
        if n == 0 {
            break;
        }
        total += n as u64;
        if total > max_bytes {
            return Err(RustCoreError::SecurityError {
                msg: format!("{label} exceeds maximum allowed size ({max_bytes} bytes)"),
            });
        }
        out.extend_from_slice(&buf[..n]);
    }
    Ok(out)
}

fn parse_manifest_sections(bytes: &[u8]) -> (HashMap<String, String>, HashMap<String, HashMap<String, String>>) {
    let text = String::from_utf8_lossy(bytes);
    let normalized = text.replace("\r\n", "\n").replace('\r', "\n");

    // Unfold RFC822 continuation lines (" \n" / "\n ")
    let mut unfolded_lines: Vec<String> = Vec::new();
    for line in normalized.split('\n') {
        if let Some(rest) = line.strip_prefix(' ') {
            if let Some(last) = unfolded_lines.last_mut() {
                last.push_str(rest);
                continue;
            }
        }
        unfolded_lines.push(line.to_string());
    }

    let mut sections: Vec<HashMap<String, String>> = Vec::new();
    let mut current: HashMap<String, String> = HashMap::new();
    for line in unfolded_lines {
        if line.trim().is_empty() {
            if !current.is_empty() {
                sections.push(current);
                current = HashMap::new();
            }
            continue;
        }
        if let Some((k, v)) = line.split_once(':') {
            current.insert(k.trim().to_ascii_lowercase(), v.trim().to_string());
        }
    }
    if !current.is_empty() {
        sections.push(current);
    }

    let main_attrs = if !sections.is_empty() && !sections[0].contains_key("name") {
        sections.remove(0)
    } else {
        HashMap::new()
    };

    let mut by_name = HashMap::new();
    for sec in sections {
        if let Some(name) = sec.get("name").cloned() {
            by_name.insert(name, sec);
        }
    }
    (main_attrs, by_name)
}

fn verify_section_digest(
    attrs: &HashMap<String, String>,
    prefix_key_sha256: &str,
    prefix_key_sha512: &str,
    prefix_key_sha1: &str,
    payload: &[u8],
    context_label: &str,
) -> Result<(), RustCoreError> {
    if let Some(expected_b64) = attrs.get(prefix_key_sha256) {
        let actual_b64 = BASE64.encode(Sha256::digest(payload));
        if constant_time_hex_eq(&actual_b64, expected_b64) {
            return Ok(());
        }
        return Err(RustCoreError::SecurityError {
            msg: format!("SHA-256 digest mismatch for {context_label}"),
        });
    }
    if let Some(expected_b64) = attrs.get(prefix_key_sha512) {
        let actual_b64 = BASE64.encode(Sha512::digest(payload));
        if constant_time_hex_eq(&actual_b64, expected_b64) {
            return Ok(());
        }
        return Err(RustCoreError::SecurityError {
            msg: format!("SHA-512 digest mismatch for {context_label}"),
        });
    }
    if let Some(expected_b64) = attrs.get(prefix_key_sha1) {
        let actual_b64 = BASE64.encode(Sha1::digest(payload));
        if constant_time_hex_eq(&actual_b64, expected_b64) {
            return Ok(());
        }
        return Err(RustCoreError::SecurityError {
            msg: format!("SHA-1 digest mismatch for {context_label}"),
        });
    }
    Err(RustCoreError::SecurityError {
        msg: format!("Missing cryptographic digest attribute for {context_label}"),
    })
}

fn compute_digest_by_oid(oid: ObjectIdentifier, data: &[u8]) -> Result<Vec<u8>, RustCoreError> {
    if oid == OID_SHA256 {
        Ok(Sha256::digest(data).to_vec())
    } else if oid == OID_SHA384 {
        Ok(Sha384::digest(data).to_vec())
    } else if oid == OID_SHA512 {
        Ok(Sha512::digest(data).to_vec())
    } else if oid == OID_SHA1 {
        Ok(Sha1::digest(data).to_vec())
    } else {
        Err(RustCoreError::SecurityError {
            msg: format!("Unsupported CMS digest algorithm OID: {oid}"),
        })
    }
}

fn verify_cms_pkcs7_signature(
    sig_der: &[u8],
    sf_bytes: &[u8],
) -> Result<String, RustCoreError> {
    let content_info = ContentInfo::from_der(sig_der).map_err(|e| RustCoreError::SecurityError {
        msg: format!("Malformed PKCS#7 ContentInfo: {e}"),
    })?;
    if content_info.content_type != ID_SIGNED_DATA {
        return Err(RustCoreError::SecurityError {
            msg: "JAR signature block is not CMS SignedData".to_string(),
        });
    }
    let signed_data = content_info
        .content
        .decode_as::<SignedData>()
        .map_err(|e| RustCoreError::SecurityError {
            msg: format!("Malformed CMS SignedData: {e}"),
        })?;

    let certs = signed_data
        .certificates
        .as_ref()
        .ok_or_else(|| RustCoreError::SecurityError {
            msg: "CMS SignedData contains no certificates".to_string(),
        })?;

    let mut leaf_cert: Option<x509_cert::Certificate> = None;
    for choice in certs.0.iter() {
        if let CertificateChoices::Certificate(cert) = choice {
            leaf_cert = Some(cert.clone());
            break;
        }
    }
    let cert = leaf_cert.ok_or_else(|| RustCoreError::SecurityError {
        msg: "No X.509 certificate found in CMS SignedData".to_string(),
    })?;

    let cert_der = cert.to_der().map_err(|e| RustCoreError::SecurityError {
        msg: format!("Failed to encode signer X.509 certificate: {e}"),
    })?;
    let signer_fp_hex = hex::encode_upper(Sha256::digest(&cert_der));

    let signer_info = signed_data
        .signer_infos
        .0
        .iter()
        .next()
        .ok_or_else(|| RustCoreError::SecurityError {
            msg: "CMS SignedData contains no SignerInfo".to_string(),
        })?;

    let digest_oid = signer_info.digest_alg.oid;
    let signed_payload: Vec<u8> = if let Some(ref signed_attrs) = signer_info.signed_attrs {
        // Verify messageDigest attribute equals digest(sf_bytes)
        let computed_sf_digest = compute_digest_by_oid(digest_oid, sf_bytes)?;
        let mut found_md = false;
        for attr in signed_attrs.iter() {
            if attr.oid == OID_MESSAGE_DIGEST {
                if let Some(val) = attr.values.iter().next() {
                    let octet = val
                        .decode_as::<der::asn1::OctetString>()
                        .map_err(|e| RustCoreError::SecurityError {
                            msg: format!("Malformed CMS messageDigest attribute: {e}"),
                        })?;
                    if octet.as_bytes() == computed_sf_digest.as_slice() {
                        found_md = true;
                        break;
                    }
                    return Err(RustCoreError::SecurityError {
                        msg: "CMS messageDigest mismatch against .SF file".to_string(),
                    });
                }
            }
        }
        if !found_md {
            return Err(RustCoreError::SecurityError {
                msg: "CMS signedAttrs missing messageDigest attribute".to_string(),
            });
        }
        signed_attrs
            .to_der()
            .map_err(|e| RustCoreError::SecurityError {
                msg: format!("Failed encoding signedAttrs DER: {e}"),
            })?
    } else {
        sf_bytes.to_vec()
    };

    let sig_bytes = signer_info.signature.as_bytes();
    let spki_der = cert
        .tbs_certificate
        .subject_public_key_info
        .to_der()
        .map_err(|e| RustCoreError::SecurityError {
            msg: format!("Failed encoding certificate SPKI DER: {e}"),
        })?;

    let sig_alg_oid = signer_info.signature_algorithm.oid;
    let key_alg_oid = cert.tbs_certificate.subject_public_key_info.algorithm.oid;

    if sig_alg_oid == SHA_256_WITH_RSA_ENCRYPTION
        || (sig_alg_oid == RSA_ENCRYPTION && digest_oid == OID_SHA256)
    {
        let pub_key = RsaPublicKey::from_public_key_der(&spki_der).map_err(|e| {
            RustCoreError::SecurityError {
                msg: format!("Invalid RSA public key in certificate: {e}"),
            }
        })?;
        let vk = RsaVerifyingKey::<Sha256>::new(pub_key);
        let sig = RsaSignature::try_from(sig_bytes).map_err(|e| RustCoreError::SecurityError {
            msg: format!("Malformed RSA signature: {e}"),
        })?;
        vk.verify(&signed_payload, &sig).map_err(|e| RustCoreError::SecurityError {
            msg: format!("RSA-SHA256 signature verification failed: {e}"),
        })?;
    } else if sig_alg_oid == SHA_384_WITH_RSA_ENCRYPTION
        || (sig_alg_oid == RSA_ENCRYPTION && digest_oid == OID_SHA384)
    {
        let pub_key = RsaPublicKey::from_public_key_der(&spki_der).map_err(|e| {
            RustCoreError::SecurityError {
                msg: format!("Invalid RSA public key in certificate: {e}"),
            }
        })?;
        let vk = RsaVerifyingKey::<Sha384>::new(pub_key);
        let sig = RsaSignature::try_from(sig_bytes).map_err(|e| RustCoreError::SecurityError {
            msg: format!("Malformed RSA signature: {e}"),
        })?;
        vk.verify(&signed_payload, &sig).map_err(|e| RustCoreError::SecurityError {
            msg: format!("RSA-SHA384 signature verification failed: {e}"),
        })?;
    } else if sig_alg_oid == SHA_512_WITH_RSA_ENCRYPTION
        || (sig_alg_oid == RSA_ENCRYPTION && digest_oid == OID_SHA512)
    {
        let pub_key = RsaPublicKey::from_public_key_der(&spki_der).map_err(|e| {
            RustCoreError::SecurityError {
                msg: format!("Invalid RSA public key in certificate: {e}"),
            }
        })?;
        let vk = RsaVerifyingKey::<Sha512>::new(pub_key);
        let sig = RsaSignature::try_from(sig_bytes).map_err(|e| RustCoreError::SecurityError {
            msg: format!("Malformed RSA signature: {e}"),
        })?;
        vk.verify(&signed_payload, &sig).map_err(|e| RustCoreError::SecurityError {
            msg: format!("RSA-SHA512 signature verification failed: {e}"),
        })?;
    } else if sig_alg_oid == SHA_1_WITH_RSA_ENCRYPTION
        || (sig_alg_oid == RSA_ENCRYPTION && digest_oid == OID_SHA1)
    {
        let pub_key = RsaPublicKey::from_public_key_der(&spki_der).map_err(|e| {
            RustCoreError::SecurityError {
                msg: format!("Invalid RSA public key in certificate: {e}"),
            }
        })?;
        let vk = RsaVerifyingKey::<Sha1>::new(pub_key);
        let sig = RsaSignature::try_from(sig_bytes).map_err(|e| RustCoreError::SecurityError {
            msg: format!("Malformed RSA signature: {e}"),
        })?;
        vk.verify(&signed_payload, &sig).map_err(|e| RustCoreError::SecurityError {
            msg: format!("RSA-SHA1 signature verification failed: {e}"),
        })?;
    } else if sig_alg_oid == ECDSA_WITH_SHA_256 || (key_alg_oid == ID_EC_PUBLIC_KEY && digest_oid == OID_SHA256) {
        let vk = P256VerifyingKey::from_public_key_der(&spki_der).map_err(|e| {
            RustCoreError::SecurityError {
                msg: format!("Invalid P-256 ECDSA public key: {e}"),
            }
        })?;
        let sig = P256DerSignature::from_bytes(sig_bytes).map_err(|e| {
            RustCoreError::SecurityError {
                msg: format!("Malformed P-256 ECDSA signature: {e}"),
            }
        })?;
        vk.verify(&signed_payload, &sig).map_err(|e| RustCoreError::SecurityError {
            msg: format!("ECDSA-P256 signature verification failed: {e}"),
        })?;
    } else if sig_alg_oid == ECDSA_WITH_SHA_384 || (key_alg_oid == ID_EC_PUBLIC_KEY && digest_oid == OID_SHA384) {
        let vk = P384VerifyingKey::from_public_key_der(&spki_der).map_err(|e| {
            RustCoreError::SecurityError {
                msg: format!("Invalid P-384 ECDSA public key: {e}"),
            }
        })?;
        let sig = P384DerSignature::from_bytes(sig_bytes).map_err(|e| {
            RustCoreError::SecurityError {
                msg: format!("Malformed P-384 ECDSA signature: {e}"),
            }
        })?;
        vk.verify(&signed_payload, &sig).map_err(|e| RustCoreError::SecurityError {
            msg: format!("ECDSA-P384 signature verification failed: {e}"),
        })?;
    } else {
        return Err(RustCoreError::SecurityError {
            msg: format!("Unsupported JAR signature algorithm OID: {sig_alg_oid}"),
        });
    }

    Ok(signer_fp_hex)
}

pub fn verify_and_extract_fdroid_jar(
    jar_path: &str,
    target_entry_name: &str,
    expected_cert_sha256: &str,
    allow_unpinned_cert: bool,
    output_entry_path: &str,
    max_jar_bytes: u64,
    max_target_bytes: u64,
) -> Result<RustFDroidJarVerifyResult, RustCoreError> {
    let start = Instant::now();
    let expected_norm = normalize_sha256_hex(expected_cert_sha256);
    if expected_norm.is_none() && !(allow_unpinned_cert && expected_cert_sha256.trim().is_empty()) {
        return Err(RustCoreError::SecurityError {
            msg: "Invalid expected F-Droid repository fingerprint".to_string(),
        });
    }

    let jar_meta = fs::metadata(jar_path).map_err(|e| RustCoreError::IoError {
        msg: format!("Cannot stat JAR '{jar_path}': {e}"),
    })?;
    if jar_meta.len() > max_jar_bytes {
        return Err(RustCoreError::SecurityError {
            msg: format!("JAR exceeds maximum allowed size ({max_jar_bytes} bytes)"),
        });
    }

    let file = File::open(jar_path).map_err(|e| RustCoreError::IoError {
        msg: format!("Cannot open JAR '{jar_path}': {e}"),
    })?;
    let mut archive = ZipArchive::new(BufReader::new(file)).map_err(|e| RustCoreError::SecurityError {
        msg: format!("Corrupt JAR archive: {e}"),
    })?;

    let mut non_dir_count = 0usize;
    let mut manifest_bytes: Option<Vec<u8>> = None;
    let mut sf_files: HashMap<String, Vec<u8>> = HashMap::new();
    let mut sig_files: HashMap<String, Vec<u8>> = HashMap::new();
    let mut target_bytes: Option<Vec<u8>> = None;

    for i in 0..archive.len() {
        let mut entry = archive.by_index(i).map_err(|e| RustCoreError::IoError {
            msg: format!("Failed reading JAR entry #{i}: {e}"),
        })?;
        if entry.is_dir() {
            continue;
        }
        non_dir_count += 1;
        if non_dir_count > MAX_JAR_ENTRIES {
            return Err(RustCoreError::SecurityError {
                msg: format!("Too many entries inside JAR ({non_dir_count})"),
            });
        }

        let name = entry.name().to_string();
        let upper = name.to_ascii_uppercase();
        if upper == "META-INF/MANIFEST.MF" {
            manifest_bytes = Some(read_zip_entry_bounded(&mut entry, max_target_bytes, &name)?);
        } else if upper.starts_with("META-INF/") && upper.ends_with(".SF") {
            let stem = upper
                .trim_start_matches("META-INF/")
                .trim_end_matches(".SF")
                .to_string();
            sf_files.insert(stem, read_zip_entry_bounded(&mut entry, max_target_bytes, &name)?);
        } else if upper.starts_with("META-INF/")
            && (upper.ends_with(".RSA") || upper.ends_with(".EC") || upper.ends_with(".DSA"))
        {
            let stem = upper
                .trim_start_matches("META-INF/")
                .rsplit_once('.')
                .map(|(s, _)| s.to_string())
                .unwrap_or_default();
            sig_files.insert(stem, read_zip_entry_bounded(&mut entry, 1024 * 1024, &name)?);
        } else if name == target_entry_name {
            if target_bytes.is_some() {
                return Err(RustCoreError::SecurityError {
                    msg: format!("Duplicate {target_entry_name} inside JAR"),
                });
            }
            target_bytes = Some(read_zip_entry_bounded(&mut entry, max_target_bytes, &name)?);
        } else {
            let _ = read_zip_entry_bounded(&mut entry, max_target_bytes, &name)?;
        }
    }

    let manifest_raw = manifest_bytes.ok_or_else(|| RustCoreError::SecurityError {
        msg: "JAR missing META-INF/MANIFEST.MF".to_string(),
    })?;
    let target_raw = target_bytes.ok_or_else(|| RustCoreError::IoError {
        msg: format!("{target_entry_name} not found inside JAR"),
    })?;

    // Find matching (.SF, .RSA/.EC/.DSA) pair
    let (sf_raw, sig_raw) = sf_files
        .iter()
        .find_map(|(stem, sf)| sig_files.get(stem).map(|sig| (sf, sig)))
        .ok_or_else(|| RustCoreError::SecurityError {
            msg: "JAR is unsigned (missing matching .SF / signature block)".to_string(),
        })?;

    // 1. Verify CMS/PKCS#7 signature over .SF and extract signer X.509 SHA-256 fingerprint
    let signer_fp = verify_cms_pkcs7_signature(sig_raw, sf_raw)?;

    // 2. Pinning check against expected_cert_sha256
    if let Some(expected_fp) = expected_norm {
        if !constant_time_hex_eq(&signer_fp, &expected_fp) {
            return Err(RustCoreError::SecurityError {
                msg: format!(
                    "F-Droid JAR certificate mismatch: expected {}, got {}",
                    expected_fp.to_ascii_uppercase(),
                    signer_fp
                ),
            });
        }
    }

    // 3. Verify .SF digests MANIFEST.MF
    let (sf_main, _) = parse_manifest_sections(sf_raw);
    verify_section_digest(
        &sf_main,
        "sha-256-digest-manifest",
        "sha-512-digest-manifest",
        "sha1-digest-manifest",
        &manifest_raw,
        "META-INF/MANIFEST.MF",
    )?;

    // 4. Verify MANIFEST.MF digests target_entry_name
    let (_, manifest_entries) = parse_manifest_sections(&manifest_raw);
    let target_section = manifest_entries
        .get(target_entry_name)
        .ok_or_else(|| RustCoreError::SecurityError {
            msg: format!("MANIFEST.MF missing digest entry for {target_entry_name}"),
        })?;
    verify_section_digest(
        target_section,
        "sha-256-digest",
        "sha-512-digest",
        "sha1-digest",
        &target_raw,
        target_entry_name,
    )?;

    // 5. Write verified target entry atomically to output_entry_path
    let out_path = Path::new(output_entry_path);
    if let Some(parent) = out_path.parent() {
        fs::create_dir_all(parent).map_err(|e| RustCoreError::IoError {
            msg: format!("Failed creating output parent dir: {e}"),
        })?;
    }
    let tmp_path = format!("{output_entry_path}.rusttmp");
    {
        let out_file = File::create(&tmp_path).map_err(|e| RustCoreError::IoError {
            msg: format!("Failed creating temp file '{tmp_path}': {e}"),
        })?;
        let mut writer = BufWriter::new(out_file);
        writer.write_all(&target_raw).map_err(|e| RustCoreError::IoError {
            msg: format!("Failed writing extracted entry: {e}"),
        })?;
        writer.flush().map_err(|e| RustCoreError::IoError {
            msg: format!("Failed flushing extracted entry: {e}"),
        })?;
    }
    if out_path.exists() {
        let _ = fs::remove_file(out_path);
    }
    fs::rename(&tmp_path, out_path).map_err(|e| RustCoreError::IoError {
        msg: format!("Failed committing verified entry to '{output_entry_path}': {e}"),
    })?;

    Ok(RustFDroidJarVerifyResult {
        signer_sha256_hex: signer_fp,
        extracted_bytes: target_raw.len() as u64,
        elapsed_micros: start.elapsed().as_micros() as u64,
    })
}

struct FilteredPackagesSeed<'a> {
    filter: Option<&'a HashSet<String>>,
}

impl<'de, 'a> DeserializeSeed<'de> for FilteredPackagesSeed<'a> {
    type Value = Map<String, Value>;

    fn deserialize<D>(self, deserializer: D) -> Result<Self::Value, D::Error>
    where
        D: Deserializer<'de>,
    {
        struct PkgMapVisitor<'a> {
            filter: Option<&'a HashSet<String>>,
        }
        impl<'de, 'a> Visitor<'de> for PkgMapVisitor<'a> {
            type Value = Map<String, Value>;

            fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
                formatter.write_str("a packages JSON object")
            }

            fn visit_map<M>(self, mut map: M) -> Result<Self::Value, M::Error>
            where
                M: MapAccess<'de>,
            {
                let mut matched = Map::new();
                while let Some(pkg_name) = map.next_key::<String>()? {
                    let keep = match self.filter {
                        Some(set) => set.contains(&pkg_name),
                        None => true,
                    };
                    if keep {
                        let val: Value = map.next_value()?;
                        if !val.is_null() {
                            matched.insert(pkg_name, val);
                        }
                    } else {
                        let _: IgnoredAny = map.next_value()?;
                    }
                }
                Ok(matched)
            }
        }
        deserializer.deserialize_map(PkgMapVisitor { filter: self.filter })
    }
}

struct FilteredAppsArraySeed<'a> {
    filter: Option<&'a HashSet<String>>,
}

impl<'de, 'a> DeserializeSeed<'de> for FilteredAppsArraySeed<'a> {
    type Value = Vec<Value>;

    fn deserialize<D>(self, deserializer: D) -> Result<Self::Value, D::Error>
    where
        D: Deserializer<'de>,
    {
        struct AppsSeqVisitor<'a> {
            filter: Option<&'a HashSet<String>>,
        }
        impl<'de, 'a> Visitor<'de> for AppsSeqVisitor<'a> {
            type Value = Vec<Value>;

            fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
                formatter.write_str("an apps JSON array")
            }

            fn visit_seq<A>(self, mut seq: A) -> Result<Self::Value, A::Error>
            where
                A: SeqAccess<'de>,
            {
                let mut apps = Vec::new();
                while let Some(val) = seq.next_element::<Value>()? {
                    if let Some(pkg) = val.get("packageName").and_then(|v| v.as_str()) {
                        if !pkg.trim().is_empty() {
                            let keep = match self.filter {
                                Some(set) => set.contains(pkg),
                                None => true,
                            };
                            if keep {
                                apps.push(val);
                            }
                        }
                    }
                }
                Ok(apps)
            }
        }
        deserializer.deserialize_seq(AppsSeqVisitor { filter: self.filter })
    }
}

pub fn stream_filter_fdroid_v2_index(
    index_path: &str,
    installed_packages: Option<Vec<String>>,
) -> Result<RustFDroidFilteredJsonResult, RustCoreError> {
    let start = Instant::now();
    let filter_set: Option<HashSet<String>> = installed_packages.map(|v| v.into_iter().collect());

    let file = File::open(index_path).map_err(|e| RustCoreError::IoError {
        msg: format!("Failed opening V2 index '{index_path}': {e}"),
    })?;
    let reader = BufReader::with_capacity(64 * 1024, file);
    let mut de = serde_json::Deserializer::from_reader(reader);

    struct V2RootVisitor<'a> {
        filter: Option<&'a HashSet<String>>,
    }
    impl<'de, 'a> Visitor<'de> for V2RootVisitor<'a> {
        type Value = (Option<Value>, Map<String, Value>);

        fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
            formatter.write_str("F-Droid index-v2.json root object")
        }

        fn visit_map<M>(self, mut map: M) -> Result<Self::Value, M::Error>
        where
            M: MapAccess<'de>,
        {
            let mut repo: Option<Value> = None;
            let mut packages: Map<String, Value> = Map::new();
            while let Some(key) = map.next_key::<String>()? {
                match key.as_str() {
                    "repo" => {
                        let val: Value = map.next_value()?;
                        if !val.is_null() {
                            repo = Some(val);
                        }
                    }
                    "packages" => {
                        let opt = map.next_value_seed(NullableSeed(FilteredPackagesSeed {
                            filter: self.filter,
                        }))?;
                        if let Some(pkgs) = opt {
                            packages = pkgs;
                        }
                    }
                    _ => {
                        let _: IgnoredAny = map.next_value()?;
                    }
                }
            }
            Ok((repo, packages))
        }
    }

    let (repo_val, packages_map) = de
        .deserialize_map(V2RootVisitor {
            filter: filter_set.as_ref(),
        })
        .map_err(|e| RustCoreError::InvalidFormat {
            msg: format!("Failed streaming V2 index JSON: {e}"),
        })?;

    let matched_count = packages_map.len() as u32;
    let mut root = Map::new();
    if let Some(r) = repo_val {
        root.insert("repo".to_string(), r);
    }
    root.insert("packages".to_string(), Value::Object(packages_map));

    let filtered_json = serde_json::to_string(&Value::Object(root)).map_err(|e| {
        RustCoreError::InvalidFormat {
            msg: format!("Failed serializing filtered V2 JSON: {e}"),
        }
    })?;

    Ok(RustFDroidFilteredJsonResult {
        filtered_json,
        matched_package_count: matched_count,
        elapsed_micros: start.elapsed().as_micros() as u64,
    })
}

pub fn stream_filter_fdroid_v1_index(
    index_path: &str,
    installed_packages: Option<Vec<String>>,
) -> Result<RustFDroidFilteredJsonResult, RustCoreError> {
    let start = Instant::now();
    let filter_set: Option<HashSet<String>> = installed_packages.map(|v| v.into_iter().collect());

    let file = File::open(index_path).map_err(|e| RustCoreError::IoError {
        msg: format!("Failed opening V1 index '{index_path}': {e}"),
    })?;
    let reader = BufReader::with_capacity(64 * 1024, file);
    let mut de = serde_json::Deserializer::from_reader(reader);

    struct V1RootVisitor<'a> {
        filter: Option<&'a HashSet<String>>,
    }
    impl<'de, 'a> Visitor<'de> for V1RootVisitor<'a> {
        type Value = (Option<Value>, Vec<Value>, Map<String, Value>);

        fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
            formatter.write_str("F-Droid index-v1.json root object")
        }

        fn visit_map<M>(self, mut map: M) -> Result<Self::Value, M::Error>
        where
            M: MapAccess<'de>,
        {
            let mut repo: Option<Value> = None;
            let mut apps: Vec<Value> = Vec::new();
            let mut packages: Map<String, Value> = Map::new();
            while let Some(key) = map.next_key::<String>()? {
                match key.as_str() {
                    "repo" => {
                        let val: Value = map.next_value()?;
                        if !val.is_null() {
                            repo = Some(val);
                        }
                    }
                    "apps" => {
                        let opt = map.next_value_seed(NullableSeed(FilteredAppsArraySeed {
                            filter: self.filter,
                        }))?;
                        if let Some(a) = opt {
                            apps = a;
                        }
                    }
                    "packages" => {
                        let opt = map.next_value_seed(NullableSeed(FilteredPackagesSeed {
                            filter: self.filter,
                        }))?;
                        if let Some(pkgs) = opt {
                            packages = pkgs;
                        }
                    }
                    _ => {
                        let _: IgnoredAny = map.next_value()?;
                    }
                }
            }
            Ok((repo, apps, packages))
        }
    }

    let (repo_val, apps_vec, packages_map) = de
        .deserialize_map(V1RootVisitor {
            filter: filter_set.as_ref(),
        })
        .map_err(|e| RustCoreError::InvalidFormat {
            msg: format!("Failed streaming V1 index JSON: {e}"),
        })?;

    let matched_count = packages_map.len() as u32;
    let mut root = Map::new();
    if let Some(r) = repo_val {
        root.insert("repo".to_string(), r);
    }
    root.insert("apps".to_string(), Value::Array(apps_vec));
    root.insert("packages".to_string(), Value::Object(packages_map));

    let filtered_json = serde_json::to_string(&Value::Object(root)).map_err(|e| {
        RustCoreError::InvalidFormat {
            msg: format!("Failed serializing filtered V1 JSON: {e}"),
        }
    })?;

    Ok(RustFDroidFilteredJsonResult {
        filtered_json,
        matched_package_count: matched_count,
        elapsed_micros: start.elapsed().as_micros() as u64,
    })
}

struct NullableSeed<S>(S);

impl<'de, S> DeserializeSeed<'de> for NullableSeed<S>
where
    S: DeserializeSeed<'de>,
{
    type Value = Option<S::Value>;

    fn deserialize<D>(self, deserializer: D) -> Result<Self::Value, D::Error>
    where
        D: Deserializer<'de>,
    {
        struct OptVisitor<S>(S);
        impl<'de, S> Visitor<'de> for OptVisitor<S>
        where
            S: DeserializeSeed<'de>,
        {
            type Value = Option<S::Value>;

            fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
                formatter.write_str("nullable value")
            }

            fn visit_unit<E>(self) -> Result<Self::Value, E> {
                Ok(None)
            }

            fn visit_none<E>(self) -> Result<Self::Value, E> {
                Ok(None)
            }

            fn visit_some<D>(self, deserializer: D) -> Result<Self::Value, D::Error>
            where
                D: Deserializer<'de>,
            {
                self.0.deserialize(deserializer).map(Some)
            }

            fn visit_map<A>(self, map: A) -> Result<Self::Value, A::Error>
            where
                A: MapAccess<'de>,
            {
                self.0
                    .deserialize(serde::de::value::MapAccessDeserializer::new(map))
                    .map(Some)
            }

            fn visit_seq<A>(self, seq: A) -> Result<Self::Value, A::Error>
            where
                A: SeqAccess<'de>,
            {
                self.0
                    .deserialize(serde::de::value::SeqAccessDeserializer::new(seq))
                    .map(Some)
            }
        }
        deserializer.deserialize_any(OptVisitor(self.0))
    }
}
