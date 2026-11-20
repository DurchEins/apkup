# 🔑 Configuring GitHub, GitLab & Codeberg Personal Access Tokens

← Back to [Wiki Home](Home) · [Troubleshooting Rate Limits & Auth](Troubleshooting-Source-Errors#3-authentication--rate-limits-http-401-403-429)

By default, ApkUp accesses public developer repositories on GitHub, GitLab, and Codeberg / Forgejo anonymously. However, for active users, private repositories, or hosts with strict rate limits, adding your own **Personal Access Token (PAT)** provides maximum reliability.

---

## 📑 Quick Navigation

* [1. Why Do I Need an Access Token?](#1-why-do-i-need-an-access-token)
* [2. How to Generate a GitHub Personal Access Token](#2-how-to-generate-a-github-personal-access-token)
* [3. How to Generate a GitLab Personal Access Token](#3-how-to-generate-a-gitlab-personal-access-token)
* [4. How to Generate a Codeberg / Forgejo Access Token](#4-how-to-generate-a-codeberg--forgejo-access-token)
* [5. Adding and Testing Tokens in ApkUp](#5-adding-and-testing-tokens-in-apkup)
* [6. Multi-Level GitLab Subgroups (`group/subgroup/project`)](#6-multi-level-gitlab-subgroups-groupsubgroupproject)
* [7. Privacy & Security FAQ](#7-privacy--security-faq)

---

## 1. Why Do I Need an Access Token?

### GitHub Rate Limits: 60 vs. 5,000 Requests/Hour
GitHub strictly restricts anonymous API requests to **60 requests per hour per public IP address**.
* If you track 15–20 apps hosted on GitHub, checking for updates twice quickly exhausts this quota.
* If you use a VPN or public Wi-Fi, other users sharing that IP also consume your quota, leading to immediate `HTTP 403 API rate limit exceeded` errors.
* Adding a **free** GitHub Personal Access Token raises your limit immediately to **5,000 requests per hour** **and enables batched GitHub GraphQL queries** (checking up to 20 repositories in a single API call instead of one REST request per app).

### GitLab: Private Repositories & Subgroups
While public GitLab projects can often be browsed anonymously, many developers and organizations host repositories under multi-level subgroups or private team workspaces. A GitLab token ensures ApkUp can query release tags and asset URLs reliably.

---

## 2. How to Generate a GitHub Personal Access Token

You can generate a free token in less than 2 minutes:

1. Sign in to your account at **[github.com](https://github.com)**.
2. Click your profile avatar in the top right corner and open **Settings**.
3. In the left sidebar, scroll to the bottom and click **Developer settings**.
4. Select **Personal access tokens** ➔ **Tokens (classic)** (or *Fine-grained tokens*).
5. Click **Generate new token (classic)**:
   * **Note:** Enter a recognizable name (e.g., `ApkUp-Updater`).
   * **Expiration:** Select *No expiration* (or 90/365 days if preferred).
   * **Scopes:** For public repositories, **no scopes are required** (leave all checkboxes unchecked). If you want to track your own private repositories, check `repo`.
6. Click **Generate token** at the bottom of the page.
7. **Important:** Copy the generated token string (`ghp_xxxxxxxxxxxxxxxxxxxx`) immediately. GitHub will never display it again.

---

## 3. How to Generate a GitLab Personal Access Token

1. Sign in to your account at **[gitlab.com](https://gitlab.com)** (or your self-hosted GitLab instance).
2. Click your avatar in the left sidebar and select **Edit profile**.
3. In the left navigation, select **Access Tokens**.
4. Click **Add new token**:
   * **Token name:** `ApkUp-Updater`
   * **Expiration date:** Choose an expiration date.
   * **Select scopes:** Check **`read_api`** (grants read-only access to repository releases and package assets).
5. Click **Create personal access token** and copy the token (`glpat-xxxxxxxxxxxxxxxxxxxx`).

---

## 4. How to Generate a Codeberg / Forgejo Access Token

Starting with **v1.43.0+**, ApkUp supports Codeberg (`codeberg.org`) and self-hosted Forgejo instances via the v1 Releases REST API:

1. Sign in to your account at **[codeberg.org](https://codeberg.org)** (or open **Create token on Codeberg** directly from ApkUp's Codeberg bottom sheet).
2. Navigate to **Settings ➔ Applications** (`https://codeberg.org/user/settings/applications`).
3. Under **Generate New Token**, enter a name (e.g., `ApkUp-Updater`).
4. Select minimal read permissions (`read:repository` for private repos; public repos require no extra scopes).
5. Click **Generate Token** and copy the resulting string into ApkUp.

---

## 5. Adding and Testing Tokens in ApkUp

ApkUp includes a built-in token validator in unified Setup bottom sheets:

1. Open **ApkUp** on your device and go to the **Setup** tab.
2. Enable **GitHub**, **GitLab**, or **Codeberg** and tap the row to open its bottom sheet.
3. Paste your personal access token into the input field.
4. Tap **Test Token** to verify connectivity and inspect remaining API quotas directly before saving.
5. Tap **Save**. Your token is active immediately for both manual and background checks.

---

## 6. Multi-Level GitLab Subgroups (`group/subgroup/project`)

Starting in **ApkUp v1.40.1+**, the GitLab repository resolver supports arbitrary nested subgroup paths.

When linking an app to a GitLab project, you can now enter:
* Standard project: `developer/app-name`
* Subgroup project: `organization/category/app-name`
* Multi-level hierarchy: `enterprise/division/team/mobile/app-name`

ApkUp automatically URL-encodes nested subgroup slugs (`enterprise%2Fdivision%2Fteam%2Fmobile%2Fapp-name`) when querying GitLab REST API v4 endpoints, ensuring releases are fetched accurately without manual configuration.

---

## 7. Privacy & Security FAQ

### Where are my tokens stored?
Your tokens are stored exclusively on your device in Android's private, sandbox-isolated application storage encrypted at rest via **Google Tink (`AES-256-GCM`)** backed by the Android Keystore (`SecurePrefsStore`), with cloud backup disabled (`allowBackup="false"`).

### Does ApkUp transmit my tokens to any third-party server?
**Never.** ApkUp enforces strict host isolation: your GitHub token is sent exclusively to `api.github.com`, your GitLab token exclusively to `gitlab.com`, and your Codeberg token exclusively to `codeberg.org` / `*.codeberg.org` over HTTPS (tokens are stripped automatically on cross-host redirects and are never sent to third-party custom domains). There are no intermediate telemetry, analytics, or proxy servers.

### Can a token be revoked?
Yes, at any time. Simply visit your GitHub, GitLab, or Codeberg token management settings and click **Revoke** or **Delete**.

---

← Back to [Wiki Home](Home) | Related: [Update Sources, Repository Linking & Signature Protection](Update-Sources,-Repository-Linking-&-Signature-Protection) · [Troubleshooting Source Errors](Troubleshooting-Source-Errors#3-authentication--rate-limits-http-401-403-429)
