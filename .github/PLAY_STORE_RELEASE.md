# Google Play release setup

The repository contains two workflows:

- `Android CI` verifies pull requests and pushes to `main` and produces a debug APK.
- `Google Play release` builds a signed AAB and uploads it to a selected Play track.

## One-time Google Play setup

1. Create the app with package name `com.example.pocastcloni` in Google Play Console.
2. Enable Play App Signing and register the upload certificate.
3. Upload the first AAB through Play Console if the Publishing API does not know the package yet.
4. Enable the Google Play Android Developer API in a Google Cloud project.
5. Create a dedicated service account and grant it access only to this app and the required release tracks.

## GitHub environment and secrets

Create GitHub environments named `google-play` and `google-play-production`. Restrict both environments to the `main` branch and add a required reviewer to `google-play-production`. Store the following secrets in both environments (or as repository secrets if environment protection is not required):

| Secret | Content |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded upload keystore (`.jks`) |
| `ANDROID_KEYSTORE_PASSWORD` | Upload keystore password |
| `ANDROID_KEY_ALIAS` | Upload key alias |
| `ANDROID_KEY_PASSWORD` | Upload key password |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Complete JSON credential for the dedicated service account |

Do not commit the keystore, its passwords, or the service-account JSON. The repository ignores `*.jks`, `*.keystore`, APKs and AABs.

## Release procedure

1. Increase `appVersionCode` and set the release `appVersionName` in `gradle.properties`.
2. Merge the tested change into `main`.
3. Open **Actions → Google Play release → Run workflow**.
4. Start with `internal` and `draft`.
5. Review the draft in Play Console and complete the release there.
6. Promote the tested version to production only after approval. A completed production release additionally requires `RELEASE` in the confirmation field.

The workflow does not run automatically on tags or pushes and its publish job only accepts `refs/heads/main`. This prevents releases from feature branches and accidental production uploads.
