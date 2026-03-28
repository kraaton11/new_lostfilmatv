# GitHub Setup

## Required Repository Settings

Configure the repository once before relying on the direct-push `main` workflow and release flow.

## Branch Protection

Protect the `main` branch with these settings:

- allow direct pushes to `main`
- do not require pull requests before changes reach `main`
- do not require blocking status checks in the branch protection rule
- keep force-pushes disabled
- keep branch deletions disabled

## Actions Permissions

In `Settings -> Actions -> General`, allow GitHub Actions to:

- read and write repository contents

## Verify Workflow

The `pull-request-checks` workflow should run on:

- direct pushes to `main`
- optional pull requests that target `main`

This keeps `verify` visible in Actions after every push without making it a branch-protection blocker.

## Release Secrets

Add these repository secrets before expecting `release.yml` to publish signed APK files:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

### Secret Formatting

`ANDROID_KEYSTORE_BASE64` should contain the full Base64-encoded keystore file contents.

Example PowerShell command:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-keystore.jks"))
```

Store the other three secrets exactly as they are used in your Android signing config.

## Release Workflow

The `release` workflow runs on pushes to `main`, generates a monotonically increasing `versionCode` from `github.run_number`, creates a date-based release name, builds `assembleRelease`, and uploads the signed APK into a GitHub Release.
