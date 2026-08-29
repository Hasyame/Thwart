# Contributing to Thwart

Thank you for wanting to help. Thwart is made by one person in his spare time,
and every correction, translation or fix genuinely lightens the load.

You do not need to be a programmer to contribute, and you do not need to have
contributed to an open source project before. This page assumes you have not.

---

## The short version

- Anything you change happens in **your own copy** of the project. You cannot
  break anything.
- Point your changes at the **`dev`** branch, not `main`.
- If you are planning something big, **open an issue first** so we can agree on
  it before you spend an evening on it.
- If you get stuck at any point, open an issue and ask. That is not a bother, it
  is how this is supposed to work.

---

## What is most useful

In rough order of how much it helps:

**Reporting a bug you hit while playing.** You do not have to fix it. A clear
report with the scenario, the campaign and what you expected is worth a lot.

**French or English wording.** The app is bilingual and both halves matter. If a
setup step reads badly, contradicts your printed booklet, or is still in the
wrong language, that is a real bug. Say which campaign, which scenario, and what
your booklet says.

**Campaign data corrections.** The campaigns are reconstructed from the printed
booklets, and mistakes creep in. If a step is wrong, tell me the page.

**Documentation.** If something on this page or in the README confused you, that
is a fault in the page, not in you. Say so.

**Code.** Bug fixes are always welcome. For features, please open an issue first,
see below.

---

## Contributing without touching code

Two of the most useful contributions need no build tools at all.

### Wording and translations

The app's own text lives in two files:

| Language | File |
|---|---|
| English | `app/src/main/res/values/strings.xml` |
| French | `app/src/main/res/values-fr/strings.xml` |

They must stay in step: every string in one has a matching entry in the other.
A build fails if a translation is missing, which is deliberate.

You can edit these directly on GitHub, in your browser, with the pencil icon.
GitHub will offer to make a copy and open a pull request for you.

### Campaign text

Campaign steps live in `app/src/main/assets/campaigns/`, one file per campaign.

**Do not edit those files directly.** Almost all of them are *generated* from a
script in `tools/`, so an edit to the JSON is overwritten the next time anybody
regenerates it, and your work is silently lost.

| Campaign | Edit this |
|---|---|
| Most campaigns | `tools/build_<name>.py`, then run it |
| Galaxy's Most Wanted (`gmw.json`) | the JSON directly, it has no generator |

If that sounds like too much, do not let it stop you. **Open an issue saying
what is wrong and what it should say, and I will make the change.** A correct
report is the hard part; applying it is easy.

### A note about card names and card text

Card names, card text and card images do not live in this repository. The app
downloads them from [MarvelCDB](https://marvelcdb.com) when it runs. If a card's
French name is wrong or missing in the app, it is wrong at MarvelCDB, and fixing
it there fixes it for every app that uses their data, not just this one.

The campaigns' own instructions *are* in this repository, and those I can fix.

---

## Contributing code

### What you need

| Tool | Version | Note |
|---|---|---|
| JDK | **21** | The build sets a toolchain, so 21 is required, not merely suggested |
| Android SDK | **API 37** | `compileSdk` and `targetSdk` are 37, minimum supported device is API 28 |
| Gradle | none | The wrapper fetches Gradle 9.6.1 for you, do not install Gradle yourself |
| Android Studio | any recent version | Optional, the command line is enough |

### Getting it building

1. **Fork** the repository, using the Fork button at the top of the GitHub page.
   That gives you your own copy that you can freely break.
2. **Clone** your fork to your machine.
3. Build and test:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`.

The first build downloads a lot and takes a while. Later ones are quick.

There is no signing key in the repository and you do not need one. Debug builds
install as a separate app (`com.hasyame.marvelchampions.debug`), so you can keep
the real Thwart on your phone alongside the one you are working on, with its own
data. Your campaigns are safe.

### Which branch

**Target `dev`.** Not `main`.

`main` is what has been released to F-Droid and the Play Store. `dev` is where
work happens, and where changes get tried by beta testers before they reach
anybody's phone. When you open a pull request, GitHub will show you a "base"
dropdown near the top: choose `dev`.

If you get this wrong, do not worry, it takes me one click to fix.

### Commit messages

Please use the form `type: what changed`, for example:

```
fix: correct the Expert setup step in Mad Titan's Shadow
feat: add a filter for owned packs on the deck list
docs: explain where campaign text is generated from
```

Common types are `feat`, `fix`, `docs`, `refactor`, `test` and `chore`.

If there is a reason behind the change that is not obvious from the code, put it
in the body of the message. Six months from now that sentence is worth more than
the diff.

### Before a large feature, open an issue

If your change is more than a fix, please open an issue describing what you want
to do before you build it. This is not bureaucracy. It is so that nobody spends
three evenings on something I then have to decline, which is miserable for
everyone. I will answer.

---

## What I am likely to accept, and what I am not

**Likely to accept**

- Bug fixes, with a test where the bug is testable
- Wording and translation corrections, in either language
- Campaign corrections checked against a printed booklet, quoting the page
- Accessibility improvements
- Documentation

**Likely to decline, and why**

- **Anything that copies rules text or card text into this repository.** The
  campaign files hold mechanics only: what to fetch and where to put it, never
  the wording from the book. This is the line that keeps the project legal and I
  will not move it.
- **Proprietary dependencies**, including Google Play Services, Firebase,
  advertising, analytics and crash reporting services. F-Droid will not build
  the app if they are present, and the app's promise is that it collects
  nothing.
- **Anything that requires an account or a server.** The app works offline and
  has no backend, deliberately.
- **Large refactors without a discussion first.** Not because they are
  unwelcome, but because I have to be able to review and maintain them alone.

If you are unsure which side of this a change falls on, ask in an issue first.

---

## After you open a pull request

Automated checks will run. Some of them are advisory: a red mark does not mean
your contribution is unwelcome, and I will tell you plainly which failures
actually need fixing.

I maintain this in my free time, so a reply may take a few days. It is not
disinterest.

---

## Reporting a security problem

Please do not open a public issue for security problems. See
[SECURITY.md](SECURITY.md).
