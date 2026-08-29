# Security policy

## Reporting a vulnerability

**Please do not open a public issue.**

Email **marvelchampcompanion@proton.me**, or use GitHub's private reporting on
the Security tab of this repository ("Report a vulnerability"). Either reaches
me alone.

Please include what you found, how to reproduce it, and what you think the
consequence is. A rough report is far better than no report: I would rather read
five that turn out to be nothing than miss one that was real.

I maintain this alone in my spare time, so expect a first reply within a week. I
will tell you what I think, what I intend to do, and when. If I disagree that it
is a problem I will say why rather than go quiet.

If you would like credit in the release notes, say so. If you would rather not
be named, that is fine too.

## What is worth reporting

Thwart has no account, no server and no analytics, so the usual categories mostly
do not apply. What genuinely matters here:

- Anything that could let a downloaded file, a card image, or MarvelCDB's
  responses run code or read data it should not.
- Anything exposing the BoardGameGeek credentials a user may have entered. These
  are encrypted with a key held in the Android keystore and must never leave the
  device except to log in to BoardGameGeek.
- Anything letting another app on the phone read Thwart's database, backups or
  table photographs.
- Anything in the build or release process by which code that is not in this
  repository could end up inside a signed release.

## What is out of scope

- The app talks to MarvelCDB over the network to fetch card data. That is by
  design and is documented in the privacy policy.
- Reports produced only by an automated scanner, with no explanation of how the
  finding applies to this app.
- Anything requiring an already fully compromised device, such as a rooted phone
  with a malicious app already running as root.

## Supported versions

Only the latest release is supported. If you are several versions behind, please
check the problem still exists on the current one before reporting.
