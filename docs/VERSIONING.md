# Application Versioning

This policy keeps every installable PocastCloni APK identifiable and prevents
different application contents from sharing the same Android version.

## Version format

Development builds use a two-part version name with a two-digit minor sequence:

```text
<major>.<minor>-dev
```

Examples are `3.63-dev`, `3.64-dev`, and `4.01-dev`. The `-dev` suffix remains
the default until the user explicitly declares a beta or production release.

The standard Android version code is calculated as:

```text
major * 10000 + minor * 100
```

| Version name | Version code |
| --- | ---: |
| `3.63-dev` | `36300` |
| `3.64-dev` | `36400` |
| `4.01-dev` | `40100` |

The final two version-code digits remain reserved for a future revision scheme.
They must stay `00` until that scheme is documented here.

## Increment rules

Every accepted change that can affect packaged application content must update
`appVersionName` and `appVersionCode` in `gradle.properties` in the same commit.
A version must never be reused for different APK contents.

Use a normal minor increment for:

- bug fixes;
- normal features;
- UI or resource changes;
- compatible database, backup, or behavior changes;
- dependency, manifest, or build changes that alter the packaged application.

For example, `3.63-dev` becomes `3.64-dev` and `36400`.

Use a major increment and restart the minor sequence at `01` for:

- major application redesigns;
- intentionally incompatible database or backup changes;
- significant product milestones;
- other changes explicitly designated as major by the user.

For example, `3.65-dev` becomes `4.01-dev` and `40100`. After `3.99-dev`,
the next normal version is also `4.01-dev`.

If the classification is uncertain, use a normal minor increment and record the
decision in the handoff. Do not select a major increment merely because a change
touches many files.

No application version increment is required for work that cannot change APK
contents, including:

- analysis or review with no implementation;
- documentation-only changes;
- tests-only changes;
- local scripts and ignored workspace files;
- plans, comments, or formatting that do not alter packaged behavior;
- rebuilding or re-signing the exact same application content.

## Source and changelog workflow

1. Read the current values from `gradle.properties` before implementation.
2. Classify the accepted application change as normal or major.
3. Update both version properties in the same commit as the application change.
4. Update `[Unreleased]` in `CHANGELOG.md` when required by the project changelog
   policy.
5. Confirm that the Settings Data tab displays the new values through
   `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`.

## Signed APK workflow

Signed device builds use this filename:

```text
dist/pocastcloni-<versionName>-p30-signed.apk
```

For each signed APK:

1. Run the appropriate checks and build the minified release variant.
2. Align and sign it with the configured local release keystore.
3. Verify the certificate against the pinned SHA-256 fingerprint in
   `signing.local.properties`.
4. Verify the packaged version name and version code before installation.
5. Retain the new APK in `dist/` without deleting older versions unless the user
   explicitly requests removal.
6. Install it as an in-place update so application data and caches are preserved.
7. Verify the installed package version.
8. Compare the installed APK SHA-256 with the matching `dist/` artifact.

Release keystores, signing credentials, generated APKs, and APK signature sidecar
files remain local and must not be committed.
