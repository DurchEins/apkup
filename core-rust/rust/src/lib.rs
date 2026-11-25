mod fdroid;
mod hasher;
mod semver;

uniffi::setup_scaffolding!();

pub use fdroid::{RustFDroidFilteredJsonResult, RustFDroidJarVerifyResult};
pub use hasher::{RustCoreError, RustSha256Result, RustSha256VerifyResult};
pub use semver::{RustApkAssetInput, RustBatchVersionEvalInput, RustBatchVersionEvalOutput};

#[uniffi::export]
pub fn rust_core_engine_info() -> String {
    format!(
        "ApkUp Rust Native Core v{} ({})",
        env!("CARGO_PKG_VERSION"),
        std::env::consts::ARCH
    )
}

#[uniffi::export]
pub fn rust_clean_version_string(tag_or_name: String) -> String {
    semver::clean_version_string(&tag_or_name)
}

#[uniffi::export]
pub fn rust_is_newer_version(current_version: String, candidate_tag: String) -> bool {
    semver::is_newer_version(&current_version, &candidate_tag)
}

#[uniffi::export]
pub fn rust_evaluate_update_candidates_batch(
    requests: Vec<RustBatchVersionEvalInput>,
) -> Vec<RustBatchVersionEvalOutput> {
    semver::evaluate_batch(requests)
}

#[uniffi::export]
pub fn rust_select_best_apk_asset_index(
    assets: Vec<RustApkAssetInput>,
    supported_abis: Vec<String>,
    required_substring: Option<String>,
    asset_filter_regex: Option<String>,
    prefer_foss_flavor: bool,
) -> Option<i32> {
    semver::select_best_apk_asset_index(
        &assets,
        &supported_abis,
        required_substring.as_deref(),
        asset_filter_regex.as_deref(),
        prefer_foss_flavor,
    )
}

#[uniffi::export]
pub fn rust_compute_file_sha256(file_path: String) -> Result<RustSha256Result, RustCoreError> {
    hasher::compute_file_sha256(&file_path)
}

#[uniffi::export]
pub fn rust_verify_file_sha256(
    file_path: String,
    expected_sha256: String,
) -> Result<RustSha256VerifyResult, RustCoreError> {
    hasher::verify_file_sha256(&file_path, &expected_sha256)
}

#[uniffi::export]
pub fn rust_verify_and_extract_fdroid_jar(
    jar_path: String,
    target_entry_name: String,
    expected_cert_sha256: String,
    allow_unpinned_cert: bool,
    output_entry_path: String,
    max_jar_bytes: u64,
    max_target_bytes: u64,
) -> Result<RustFDroidJarVerifyResult, RustCoreError> {
    fdroid::verify_and_extract_fdroid_jar(
        &jar_path,
        &target_entry_name,
        &expected_cert_sha256,
        allow_unpinned_cert,
        &output_entry_path,
        max_jar_bytes,
        max_target_bytes,
    )
}

#[uniffi::export]
pub fn rust_stream_filter_fdroid_v2_index(
    index_path: String,
    installed_packages: Option<Vec<String>>,
) -> Result<RustFDroidFilteredJsonResult, RustCoreError> {
    fdroid::stream_filter_fdroid_v2_index(&index_path, installed_packages)
}

#[uniffi::export]
pub fn rust_stream_filter_fdroid_v1_index(
    index_path: String,
    installed_packages: Option<Vec<String>>,
) -> Result<RustFDroidFilteredJsonResult, RustCoreError> {
    fdroid::stream_filter_fdroid_v1_index(&index_path, installed_packages)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn temp_file_path(prefix: &str, ext: &str) -> std::path::PathBuf {
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        std::env::temp_dir().join(format!("{prefix}_{nanos}.{ext}"))
    }

    #[test]
    fn test_semver_comparison_and_guards() {
        assert!(rust_is_newer_version("1.46.0".into(), "v1.47.0".into()));
        assert!(!rust_is_newer_version("1.47.0".into(), "1.47.0-beta.1".into()));
        assert!(rust_is_newer_version("1.47.0-beta.1".into(), "1.47.0".into()));
        assert!(rust_is_newer_version("1.47.0-beta.1".into(), "1.47.0-beta.2".into()));
        // Bare date/build-number tag guard
        assert!(!rust_is_newer_version("2.4.0-261014".into(), "v2026072802".into()));
    }

    #[test]
    fn test_batch_eval_and_asset_selection() {
        let batch = rust_evaluate_update_candidates_batch(vec![
            RustBatchVersionEvalInput {
                id: "app1".into(),
                current_version: "1.0.0".into(),
                candidate_tag: "v1.1.0-beta.1".into(),
            },
            RustBatchVersionEvalInput {
                id: "app2".into(),
                current_version: "2.0.0".into(),
                candidate_tag: "v2.0.0".into(),
            },
        ]);
        assert_eq!(batch.len(), 2);
        assert!(batch[0].is_newer);
        assert!(batch[0].is_pre_release);
        assert_eq!(batch[0].pre_release_channel.as_deref(), Some("BETA"));
        assert!(!batch[1].is_newer);

        let assets = vec![
            RustApkAssetInput {
                index: 0,
                filename: "app-armeabi-v7a.apk".into(),
            },
            RustApkAssetInput {
                index: 1,
                filename: "app-arm64-v8a-foss.apk".into(),
            },
            RustApkAssetInput {
                index: 2,
                filename: "app-universal.apk".into(),
            },
            RustApkAssetInput {
                index: 3,
                filename: "app-arm64-v8a-gplay.apk".into(),
            },
        ];
        let picked = rust_select_best_apk_asset_index(
            assets,
            vec!["arm64-v8a".into(), "armeabi-v7a".into()],
            None,
            None,
            true,
        );
        assert_eq!(picked, Some(1));
    }

    #[test]
    fn test_stream_sha256_hash_and_verify() {
        let p = temp_file_path("rust_sha_test", "bin");
        {
            let mut f = std::fs::File::create(&p).unwrap();
            f.write_all(b"apkup-rust-stream-test").unwrap();
        }
        let path_str = p.to_string_lossy().to_string();
        let res = rust_compute_file_sha256(path_str.clone()).unwrap();
        assert_eq!(res.bytes_hashed, 22);
        assert_eq!(res.sha256_hex.len(), 64);

        let ver_ok = rust_verify_file_sha256(path_str.clone(), format!("sha256:{}", res.sha256_hex)).unwrap();
        assert!(ver_ok.matches);

        let ver_bad = rust_verify_file_sha256(path_str, "0".repeat(64)).unwrap();
        assert!(!ver_bad.matches);
        let _ = std::fs::remove_file(&p);
    }

    #[test]
    fn test_stream_filter_fdroid_v2_index() {
        let p = temp_file_path("fdroid_v2", "json");
        let sample = r#"{
            "repo": { "timestamp": 1730761200000, "version": 20002, "address": "https://example.org" },
            "packages": {
                "com.keep.app": { "metadata": { "name": { "en-US": "Keep" } }, "versions": {} },
                "com.skip.app": { "metadata": { "name": { "en-US": "Skip" } }, "versions": {} }
            }
        }"#;
        std::fs::write(&p, sample).unwrap();
        let res = rust_stream_filter_fdroid_v2_index(
            p.to_string_lossy().to_string(),
            Some(vec!["com.keep.app".to_string()]),
        )
        .unwrap();
        assert_eq!(res.matched_package_count, 1);
        assert!(res.filtered_json.contains("com.keep.app"));
        assert!(!res.filtered_json.contains("com.skip.app"));
        let _ = std::fs::remove_file(&p);
    }

    #[test]
    fn test_stream_filter_fdroid_v1_index_and_unsigned_jar_rejected() {
        let p = temp_file_path("fdroid_v1", "json");
        let sample = r#"{
            "repo": { "timestamp": 1730761200000, "version": 21, "name": "Test V1" },
            "apps": [
                { "packageName": "com.keep.v1", "name": "Keep V1" },
                { "packageName": "com.drop.v1", "name": "Drop V1" }
            ],
            "packages": {
                "com.keep.v1": [{ "versionName": "1.0", "versionCode": 1 }],
                "com.drop.v1": [{ "versionName": "2.0", "versionCode": 2 }]
            }
        }"#;
        std::fs::write(&p, sample).unwrap();
        let res = rust_stream_filter_fdroid_v1_index(
            p.to_string_lossy().to_string(),
            Some(vec!["com.keep.v1".to_string()]),
        )
        .unwrap();
        assert_eq!(res.matched_package_count, 1);
        assert!(res.filtered_json.contains("com.keep.v1"));
        assert!(!res.filtered_json.contains("com.drop.v1"));
        let _ = std::fs::remove_file(&p);

        // Unsigned JAR must be rejected fail-closed
        let jar_p = temp_file_path("unsigned", "jar");
        let out_p = temp_file_path("out_entry", "json");
        {
            let file = std::fs::File::create(&jar_p).unwrap();
            let mut zw = zip::ZipWriter::new(file);
            let opts = zip::write::SimpleFileOptions::default();
            zw.start_file("entry.json", opts).unwrap();
            zw.write_all(b"{}").unwrap();
            zw.finish().unwrap();
        }
        let res = rust_verify_and_extract_fdroid_jar(
            jar_p.to_string_lossy().to_string(),
            "entry.json".into(),
            "".into(),
            true,
            out_p.to_string_lossy().to_string(),
            4 * 1024 * 1024,
            2 * 1024 * 1024,
        );
        assert!(res.is_err());
        let _ = std::fs::remove_file(&jar_p);
        let _ = std::fs::remove_file(&out_p);
    }
}
