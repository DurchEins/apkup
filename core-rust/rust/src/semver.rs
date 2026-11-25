use std::cmp::Ordering;
use std::collections::HashSet;
use std::sync::OnceLock;
use regex::Regex;

#[derive(Debug, Clone, uniffi::Record)]
pub struct RustBatchVersionEvalInput {
    pub id: String,
    pub current_version: String,
    pub candidate_tag: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RustBatchVersionEvalOutput {
    pub id: String,
    pub cleaned_candidate: String,
    pub is_newer: bool,
    pub is_pre_release: bool,
    pub pre_release_channel: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RustApkAssetInput {
    pub index: i32,
    pub filename: String,
}

#[derive(Debug, Clone)]
pub struct ParsedSemVer {
    pub segments: Vec<i64>,
    pub pre_release: Option<String>,
}

fn pre_release_regex() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| {
        Regex::new(r"(?i)(?:^|[.\-_+\s])(alpha|beta|rc|nightly|canary|dev|preview|snapshot|pre|experimental)\d*(?:$|[.\-_+\s])")
            .expect("valid pre-release regex")
    })
}

fn digits_regex() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"\d+").expect("valid digits regex"))
}

pub fn clean_version_string(tag_or_name: &str) -> String {
    let mut s = tag_or_name.trim();
    if let Some(rest) = s.strip_prefix('v') {
        s = rest;
    } else if let Some(rest) = s.strip_prefix('V') {
        s = rest;
    }
    if let Some(rest) = s.strip_prefix("release-") {
        s = rest;
    } else if let Some(rest) = s.strip_prefix("Release-") {
        s = rest;
    }
    s.trim().to_string()
}

pub fn parse_sem_ver(version_str: &str) -> ParsedSemVer {
    let cleaned = clean_version_string(version_str);
    let without_build_meta = cleaned.split('+').next().unwrap_or("").trim();
    let mut parts = without_build_meta.splitn(2, '-');
    let base = parts.next().unwrap_or("");
    let pre = parts
        .next()
        .map(|p| p.trim().to_string())
        .filter(|p| !p.is_empty());

    let segments = base
        .split('.')
        .filter_map(|seg| {
            let digits: String = seg.chars().take_while(|c| c.is_ascii_digit()).collect();
            if digits.is_empty() {
                None
            } else {
                digits.parse::<i64>().ok()
            }
        })
        .collect();

    ParsedSemVer {
        segments,
        pre_release: pre,
    }
}

pub fn classify_pre_release_channel(version_or_tag: &str) -> Option<String> {
    let cleaned = clean_version_string(version_or_tag);
    let caps = pre_release_regex().captures(&cleaned)?;
    let token = caps.get(1)?.as_str().to_ascii_lowercase();
    let channel = match token.as_str() {
        "beta" => "BETA",
        "rc" | "pre" | "preview" => "RC",
        "alpha" => "ALPHA",
        "nightly" | "canary" | "dev" | "snapshot" | "experimental" => "NIGHTLY",
        _ => "BETA",
    };
    Some(channel.to_string())
}

pub fn is_pre_release_version(version_or_tag: &str, is_prerelease_flag: bool) -> bool {
    if is_prerelease_flag {
        return true;
    }
    let cleaned = clean_version_string(version_or_tag);
    if cleaned.is_empty() {
        return false;
    }
    pre_release_regex().is_match(&cleaned)
}

fn is_bare_build_number_tag(parsed: &ParsedSemVer) -> bool {
    parsed.segments.len() == 1 && parsed.segments[0] >= 100_000
}

fn compare_pre_release(candidate: &str, current: &str) -> i32 {
    let cand_tokens: Vec<&str> = candidate
        .split(['.', '-', '_'])
        .filter(|s| !s.is_empty())
        .collect();
    let curr_tokens: Vec<&str> = current
        .split(['.', '-', '_'])
        .filter(|s| !s.is_empty())
        .collect();
    let max_tokens = cand_tokens.len().max(curr_tokens.len());

    for i in 0..max_tokens {
        let c_tok = match cand_tokens.get(i) {
            Some(t) => *t,
            None => return -1,
        };
        let cur_tok = match curr_tokens.get(i) {
            Some(t) => *t,
            None => return 1,
        };
        let c_num = c_tok.parse::<i64>().ok();
        let cur_num = cur_tok.parse::<i64>().ok();
        match (c_num, cur_num) {
            (Some(cn), Some(curn)) => {
                if cn != curn {
                    return match cn.cmp(&curn) {
                        Ordering::Less => -1,
                        Ordering::Greater => 1,
                        Ordering::Equal => 0,
                    };
                }
            }
            (Some(_), None) => return -1,
            (None, Some(_)) => return 1,
            (None, None) => {
                let c_num_emb = digits_regex()
                    .find(c_tok)
                    .and_then(|m| m.as_str().parse::<i64>().ok());
                let cur_num_emb = digits_regex()
                    .find(cur_tok)
                    .and_then(|m| m.as_str().parse::<i64>().ok());
                let c_type = digits_regex().replace_all(c_tok, "").to_ascii_lowercase();
                let cur_type = digits_regex().replace_all(cur_tok, "").to_ascii_lowercase();
                if c_type != cur_type {
                    return match c_type.cmp(&cur_type) {
                        Ordering::Less => -1,
                        Ordering::Greater => 1,
                        Ordering::Equal => 0,
                    };
                } else if let (Some(cn), Some(curn)) = (c_num_emb, cur_num_emb) {
                    if cn != curn {
                        return match cn.cmp(&curn) {
                            Ordering::Less => -1,
                            Ordering::Greater => 1,
                            Ordering::Equal => 0,
                        };
                    }
                }
                if c_tok != cur_tok {
                    let cmp = c_tok.to_ascii_lowercase().cmp(&cur_tok.to_ascii_lowercase());
                    if cmp != Ordering::Equal {
                        return if cmp == Ordering::Less { -1 } else { 1 };
                    }
                }
            }
        }
    }
    0
}

pub fn is_newer_version(current_version: &str, candidate_tag: &str) -> bool {
    if current_version.trim().is_empty() {
        return !clean_version_string(candidate_tag).is_empty();
    }
    let current = parse_sem_ver(current_version);
    let candidate = parse_sem_ver(candidate_tag);
    if candidate.segments.is_empty() {
        return false;
    }

    if current.segments.len() >= 2 && current.segments[0] < 10_000 && is_bare_build_number_tag(&candidate) {
        return false;
    }

    let max_len = current.segments.len().max(candidate.segments.len());
    for i in 0..max_len {
        let c = *current.segments.get(i).unwrap_or(&0);
        let n = *candidate.segments.get(i).unwrap_or(&0);
        if n != c {
            return n > c;
        }
    }

    if current.pre_release.is_some() && candidate.pre_release.is_none() {
        return true;
    }
    if current.pre_release.is_none() && candidate.pre_release.is_some() {
        return false;
    }
    if let (Some(cand_pre), Some(curr_pre)) = (&candidate.pre_release, &current.pre_release) {
        return compare_pre_release(cand_pre, curr_pre) > 0;
    }
    false
}

pub fn evaluate_batch(requests: Vec<RustBatchVersionEvalInput>) -> Vec<RustBatchVersionEvalOutput> {
    requests
        .into_iter()
        .map(|req| {
            let cleaned = clean_version_string(&req.candidate_tag);
            let is_newer = is_newer_version(&req.current_version, &cleaned);
            let is_pre = is_pre_release_version(&req.candidate_tag, false);
            let channel = if is_pre {
                classify_pre_release_channel(&req.candidate_tag)
            } else {
                None
            };
            RustBatchVersionEvalOutput {
                id: req.id,
                cleaned_candidate: cleaned,
                is_newer,
                is_pre_release: is_pre,
                pre_release_channel: channel,
            }
        })
        .collect()
}

fn has_nested_quantifier_risk(pattern: &str) -> bool {
    let chars: Vec<char> = pattern.chars().collect();
    let mut stack: Vec<bool> = Vec::new();
    let mut in_char_class = false;
    let mut i = 0usize;

    while i < chars.len() {
        let ch = chars[i];
        if ch == '\\' {
            i += 2;
            continue;
        }
        if in_char_class {
            if ch == ']' {
                in_char_class = false;
            }
            i += 1;
            continue;
        }
        match ch {
            '[' => in_char_class = true,
            '(' => stack.push(false),
            ')' => {
                let inner_q = stack.pop().unwrap_or(false);
                let (outer_q, next_i) = consume_quantifier(&chars, i + 1);
                if inner_q && outer_q {
                    return true;
                }
                if outer_q || inner_q {
                    if let Some(last) = stack.last_mut() {
                        *last = true;
                    }
                }
                i = next_i;
                continue;
            }
            '*' | '+' => {
                if let Some(last) = stack.last_mut() {
                    *last = true;
                }
            }
            '{' => {
                let (bounded_q, next_i) = consume_brace_quantifier(&chars, i);
                if bounded_q {
                    if let Some(last) = stack.last_mut() {
                        *last = true;
                    }
                    i = next_i;
                    continue;
                }
            }
            _ => {}
        }
        i += 1;
    }
    false
}

fn consume_quantifier(chars: &[char], start: usize) -> (bool, usize) {
    if start >= chars.len() {
        return (false, start);
    }
    match chars[start] {
        '*' | '+' => {
            let mut next = start + 1;
            if next < chars.len() && (chars[next] == '?' || chars[next] == '+') {
                next += 1;
            }
            (true, next)
        }
        '{' => {
            let (is_rep, mut next) = consume_brace_quantifier(chars, start);
            if is_rep {
                if next < chars.len() && (chars[next] == '?' || chars[next] == '+') {
                    next += 1;
                }
                (true, next)
            } else {
                (false, start)
            }
        }
        _ => (false, start),
    }
}

fn consume_brace_quantifier(chars: &[char], start: usize) -> (bool, usize) {
    if start >= chars.len() || chars[start] != '{' {
        return (false, start);
    }
    let Some(rel_close) = chars[start + 1..].iter().position(|&c| c == '}') else {
        return (false, start);
    };
    let close = start + 1 + rel_close;
    let body: String = chars[start + 1..close].iter().collect();
    let parts: Vec<&str> = body.split(',').collect();
    if parts.len() > 2 {
        return (false, start);
    }
    let Ok(min) = parts[0].parse::<u64>() else {
        return (false, start);
    };
    let max = if parts.len() == 2 {
        if parts[1].is_empty() {
            u64::MAX
        } else {
            let Ok(v) = parts[1].parse::<u64>() else {
                return (false, start);
            };
            v
        }
    } else {
        min
    };
    (max > 1, close + 1)
}

fn compile_safe_regex(pattern: &str) -> Option<Regex> {
    let trimmed = pattern.trim();
    if trimmed.is_empty() || trimmed.len() > 256 || has_nested_quantifier_risk(trimmed) {
        return None;
    }
    Regex::new(&format!("(?i){trimmed}")).ok()
}

fn has_flavor_token(lowercase_filename: &str, token: &str) -> bool {
    let escaped = regex::escape(token);
    let pat = format!(r"(^|[._-]){escaped}([._-]|\.apk$)");
    Regex::new(&pat)
        .map(|re| re.is_match(lowercase_filename))
        .unwrap_or(false)
}

fn compute_flavor_adjustment(lowercase_filename: &str, prefer_foss_flavor: bool) -> i32 {
    let mut adj = 0;
    if has_flavor_token(lowercase_filename, "debug") {
        adj -= 60;
    }
    if has_flavor_token(lowercase_filename, "huawei") {
        adj -= 25;
    }
    if prefer_foss_flavor {
        for tok in ["foss", "libre", "fdroid", "nogms"] {
            if has_flavor_token(lowercase_filename, tok) {
                adj += 15;
                break;
            }
        }
        for tok in ["gplay", "gms", "playstore", "proprietary"] {
            if has_flavor_token(lowercase_filename, tok) {
                adj -= 30;
                break;
            }
        }
    }
    adj
}

fn detect_abi_in_filename(lowercase_filename: &str) -> Option<&'static str> {
    const ABIS: [(&str, &[&str]); 4] = [
        ("x86", &["x86", "i386", "i686"]),
        ("x86_64", &["x86_64", "x64", "amd64"]),
        ("armeabi-v7a", &["armeabi-v7a", "armv7", "armv7a", "armeabi"]),
        ("arm64-v8a", &["arm64-v8a", "arm64", "aarch64"]),
    ];
    for (canonical, aliases) in ABIS {
        for alias in aliases {
            let esc = regex::escape(alias);
            let pat = format!(r"[._-]{esc}[._-]|^{esc}[._-]|[._-]{esc}\.apk$");
            if let Ok(re) = Regex::new(&pat) {
                if re.is_match(lowercase_filename) {
                    return Some(canonical);
                }
            }
        }
    }
    None
}

pub fn select_best_apk_asset_index(
    assets: &[RustApkAssetInput],
    supported_abis: &[String],
    required_substring: Option<&str>,
    asset_filter_regex: Option<&str>,
    prefer_foss_flavor: bool,
) -> Option<i32> {
    let req_lower = required_substring
        .map(|s| s.trim().to_ascii_lowercase())
        .filter(|s| !s.is_empty());

    let compiled_regex = match asset_filter_regex.map(|s| s.trim()).filter(|s| !s.is_empty()) {
        Some(pat) => Some(compile_safe_regex(pat)?),
        None => None,
    };

    let device_abis: Vec<String> = supported_abis
        .iter()
        .map(|s| s.to_ascii_lowercase())
        .collect();
    let device_abi_set: HashSet<&str> = device_abis.iter().map(|s| s.as_str()).collect();
    let primary_abi = device_abis.first().map(|s| s.as_str()).unwrap_or("arm64-v8a");

    let mut best: Option<(i32, i32)> = None; // (index, score)

    for asset in assets {
        let fname_lower = asset.filename.to_ascii_lowercase();
        if !fname_lower.ends_with(".apk") {
            continue;
        }
        if let Some(ref req) = req_lower {
            if !fname_lower.contains(req) {
                continue;
            }
        }
        if let Some(ref re) = compiled_regex {
            if !re.is_match(&asset.filename) {
                continue;
            }
        }

        if fname_lower.ends_with("-unaligned.apk")
            || fname_lower.ends_with("-sources.apk")
            || fname_lower.ends_with("-debug.apk")
            || fname_lower.ends_with("_debug.apk")
            || fname_lower.contains("-debug-")
        {
            continue;
        }

        let base_score = if let Some(detected) = detect_abi_in_filename(&fname_lower) {
            if !device_abi_set.contains(detected) {
                continue;
            }
            if detected == primary_abi {
                100
            } else {
                80
            }
        } else if fname_lower.contains("universal") {
            60
        } else {
            50
        };

        let score = base_score + compute_flavor_adjustment(&fname_lower, prefer_foss_flavor);
        match best {
            None => best = Some((asset.index, score)),
            Some((_, best_score)) if score > best_score => best = Some((asset.index, score)),
            _ => {}
        }
    }

    best.map(|(idx, _)| idx)
}
