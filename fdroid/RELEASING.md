# Releasing a new version to F-Droid

Short version: **you do nothing.** Tag a release on GitHub the way you already
do, and F-Droid picks it up by itself. This file exists to say why that works,
what would break it, and how to tell when it has.

For the one-time submission — the merge request that got the app in — see
[SUBMITTING.md](SUBMITTING.md).

## What makes it automatic

Two lines of the metadata do all of it:

```yaml
UpdateCheckMode: Tags      # watch the git repo for new tags
AutoUpdateMode: Version    # when one appears, write a new build entry
```

So a release goes:

1. You push a tag — `git push origin v1.14.0`
2. F-Droid's bot notices it on GitHub, and reads `versionCode` and
   `versionName` out of `app/build.gradle.kts` **at that tag**
3. The bot commits a new `Builds:` entry to `fdroiddata` itself and bumps
   `CurrentVersion` / `CurrentVersionCode`
4. Their buildserver compiles it from source, offline, on their machines
5. It appears in the F-Droid client

Nothing here involves GitLab, and nothing involves the APK you attach to the
GitHub release. **F-Droid never sees your APK** — it builds its own from your
source at the tag.

Which means the trigger is the **git tag**, not the GitHub Release. Pushing a
tag without publishing a release would still reach F-Droid; the release page is
for the people who sideload it or run it in BlueStacks.

Expect a few days end to end: the bot runs on a cycle, then the build queues,
then the repo index republishes.

## What you still have to get right

- **`versionCode` must increase.** It is the only thing Android and F-Droid
  order releases by.
- **Tag names stay `vX.Y.Z`.** The updater writes the tag it found straight
  into `commit:`, so the prefix is fine — but a change of scheme would need the
  metadata changing too.
- **Nothing new from outside `google()` and `mavenCentral()`.** Their scanner
  refuses JitPack, flatDir and binary blobs, and it reads the compiled bytecode
  rather than trusting the build file.
- **Keep `dependenciesInfo { includeInApk = false }`.** Putting it back would
  reintroduce the signing block their scanner rejects, and every later build
  would fail. See SUBMITTING.md for the story.

## What auto-update cannot do

The bot reuses the **existing build recipe**. It only knows how to change the
version, so anything that changes *how* the app is built needs a new merge
request against `fdroiddata`:

- moving or adding a Gradle module, or changing `subdir`
- needing an NDK, a prebuild step, or extra Gradle flags
- a new dependency from a repository they do not allow

Ordinary feature work and bug fixes need none of that.

## Telling whether it worked

**A failed build is silent.** Nobody emails you; the version simply never
appears while the older ones stay available. So after a release, if it has not
shown up in a week, go and look rather than assuming it is slow:

- Package page: <https://f-droid.org/packages/com.hasyame.marvelchampions/>
- Build logs: F-Droid's build monitor, <https://monitor.f-droid.org/builds>

Worth checking deliberately after the first auto-updated release, to prove the
whole chain works end to end. After that it can be left alone.

## One consequence to remember

Reproducible builds were declined at submission, because the release APKs built
here bundle a card snapshot fetched from MarvelCDB and their buildserver has no
network, so the two can never match byte for byte.

That means **F-Droid signs its build with F-Droid's key**, and the GitHub APK is
signed with yours. Android will not let one replace the other: a player moving
between them has to uninstall first, which takes every campaign, deck and play
record with it. Point anyone doing that at Backup and restore in Settings.

The decision is not reversible — F-Droid cannot switch to your signature later.
