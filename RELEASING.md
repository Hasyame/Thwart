# Releasing

## The short version

Work on `dev`. Nothing you do there reaches anybody.

A release happens when you merge `dev` into `main` and **create a tag**. The tag
is the trigger, not the branch, not the commit, not the push.

## Why the tag is the only thing that matters

F-Droid's metadata for this app says `UpdateCheckMode: Tags` and
`AutoUpdateMode: Version`. Their updater watches the tags on this repository and
nothing else. It has no idea `dev` exists, and it does not care what lands on
`main`.

The practical consequence: **never tag anything you do not want published.** A
tag like `v1.30.0-beta` pushed for your own convenience is a release as far as
F-Droid is concerned.

If you want to mark a point in `dev` for yourself, use a branch or write the
commit hash down. Not a tag.

## Testing your own builds

The debug build has `applicationIdSuffix = ".debug"`, so it installs as a
separate app next to the real one, with its own database. You can put a test
build on your own phone without risking the campaigns you actually play.

```
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Doing a release

1. Merge `dev` into `main`.
2. Bump `versionCode` and `versionName` in `app/build.gradle.kts`. A feature
   moves the minor, a bug fix moves the patch.
3. Write the changelog, both languages, in
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and
   `fastlane/metadata/android/fr-FR/changelogs/<versionCode>.txt`. Keep it under
   about 500 characters, F-Droid truncates.
4. `./gradlew clean :app:testDebugUnitTest :app:lintDebug` and then
   `:app:assembleRelease`. The release build needs `keystore.properties`
   present, or it signs with the debug key and warns loudly.
5. Check the APK is signed by you and carries the right version:
   `aapt2 dump badging app/build/outputs/apk/release/app-release.apk`
6. Push `main`, then tag `vX.Y.Z` and push the tag.
7. Publish the GitHub release with the APK attached, named
   `Thwart-X.Y.Z.apk`, using the English changelog as the body.

F-Droid picks it up from the tag on its own. There is nothing to submit.

## Betas

A tag with a suffix is a beta: `v1.30.0-beta.1`. Tag betas on `dev`, stable
releases on `main`.

Both take the same workflow, the same protected environment, the same signing
key and the same checks, because a beta APK is still an APK signed by you and
installed on somebody's phone.

They differ only at the end. **A beta is never published.** The signed APK is
uploaded as a workflow artifact, which only you can download, and you hand it to
supporters yourself. A GitHub pre-release would be public: the releases page
lists it, and anyone running Obtainium with prereleases enabled would get it.

So the beta ritual is: tag, approve the run, download the artifact from the
Actions tab, attach it to a patrons-only Patreon post. Testers are pointed at
[docs/BETA.md](docs/BETA.md), which starts by telling them to take a backup.

The tag itself is public, because tags always are. That is fine: it says a beta
exists without giving anybody the binary.

**F-Droid must be told to ignore these tags.** Its metadata reads every tag
unless given a pattern, so without the change below your betas ship to everyone.

## The one thing that is not automatic

F-Droid builds from source and signs with **their** key, not yours. An APK
installed from the GitHub releases page therefore cannot be updated by the
F-Droid one: Android refuses an install when the signature differs.

Anyone moving from GitHub to F-Droid has to export a backup from the settings,
uninstall, reinstall, and restore. Say so in the release notes when that day
comes, because they will otherwise find out by losing their campaigns.

## Keep these two in step

The tag and `versionName` must match, or F-Droid's tag check will not find the
version it expects. `v1.29.0` goes with `versionName = "1.29.0"`. It has been
right every release so far, and it is the easiest thing to get wrong.
