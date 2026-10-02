# Release signing (phone-only)

The release keystore never enters the repository or any chat. It is created on the
maintainer's phone and handed to CI only through GitHub Actions secrets.

## 0. Install the release-ready workflow (one time)

Copy the whole content of `docs/ci-release.yml` over `.github/workflows/ci.yml`
(GitHub web editor). Workflow files can only be changed by the repository owner.

## 1. Create the keystore in Termux (F-Droid build)

```sh
pkg install openjdk-17
keytool -genkeypair -v -keystore pulse-release.jks -alias pulse \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 pulse-release.jks > pulse-release.b64
termux-setup-storage
cp pulse-release.jks pulse-release.b64 ~/storage/downloads/
```

The keystore is PKCS12, so the key password equals the store password.

## 2. Add four repository secrets

GitHub > repository > Settings > Secrets and variables > Actions > New repository secret:

| Name | Value |
|---|---|
| `PULSE_KEYSTORE_BASE64` | full content of `pulse-release.b64` (one line) |
| `PULSE_KEYSTORE_PASSWORD` | the password typed in keytool |
| `PULSE_KEY_ALIAS` | `pulse` |
| `PULSE_KEY_PASSWORD` | the same password |

## 3. Build

The next CI run builds `app-release` next to `app-debug` and prints the certificate
fingerprint in the run summary. Without the secrets the step is skipped and CI stays green.

## Backup

Losing the keystore or its password means no update can ever be installed over the
existing app. Keep `pulse-release.jks` and the password in two separate safe places.
Delete `pulse-release.b64` after the secret is saved.

A release-signed APK has a different signature from the CI debug APK, so switching
from debug to release needs one uninstall (back up with Vault first).
