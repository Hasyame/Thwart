# Reviewing a pull request from someone you do not know

This app ships APKs signed with your key. Whatever is merged and released runs
on other people's phones with your name on it, and they installed it because
they trust you. That is the whole reason this page exists.

Most contributions are fine and most contributors are exactly who they appear to
be. This is not a suspicion list, it is an order to read things in, so that the
five minutes you have are spent where a mistake would be expensive.

---

## Read in this order

### 1. `.github/` — anything at all

**Stop and read every line.** A change here changes what CI does, and CI is the
thing that decides whether the other checks mean anything.

Refuse without much discussion:

- Any workflow gaining `pull_request_target`. It runs with a writable token and
  access to secrets while checking out the contributor's code. There is almost
  never a legitimate reason in a project like this.
- Any workflow gaining `permissions: write` it did not have.
- Any reference to `secrets.` in a workflow that a pull request can trigger.
- An action pinned to a tag or branch rather than a commit SHA.
- A new action from an author you have not heard of.

A genuine contributor almost never needs to touch this directory. If they have,
ask why before reading anything else.

### 2. `app/build.gradle.kts`, `gradle/libs.versions.toml`, the wrapper

- **A new dependency.** Search the coordinate. Is it maintained, is it free
  software, does it pull in Google Play Services transitively? The F-Droid check
  catches the blocklist, but it cannot catch a small library nobody has heard of
  that happens to phone home.
- **A change to `gradle-wrapper.properties`.** The `distributionUrl` decides
  which Gradle runs on your machine and on the runner. It should point at
  `services.gradle.org`, nowhere else.
- **A new Gradle task, `doLast`, or anything executing a command.** Gradle files
  are code and they run during the build.
- **Anything touching `signingConfigs`.** There is one correct answer here and
  it is already written.

### 3. `AndroidManifest.xml`

The app asks for **one** permission: internet. Anything added is a question that
needs answering before the code is read.

Particularly: `READ_EXTERNAL_STORAGE`, `READ_CONTACTS`, location of any kind,
`QUERY_ALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED`, and any new exported component.
F-Droid also displays permissions prominently, so a new one is visible to every
user as a change in what the app can do.

### 4. Network code

The app talks to two hosts: **marvelcdb.com** for card data, and
**boardgamegeek.com**, and only if the user set it up.

Look for: a new base URL or host, anything sending data rather than fetching it,
anything reading the BoardGameGeek credentials outside the code that logs in,
and anything that looks like an identifier being attached to a request. The
privacy policy makes promises; this is where they are kept or broken.

### 5. Data files

`assets/campaigns/*.json`, `scenario_rules.json`, the string resources.

- **Was the JSON edited by hand?** Almost every campaign is generated from
  `tools/build_*.py`. An edit to the JSON is overwritten the next time anybody
  regenerates it, and their work is silently lost. Galaxy's Most Wanted is the
  exception. Ask them to change the generator instead, and explain why, because
  this is not obvious and it is not their fault.
- **Is there rules text or card text?** The line this project holds is
  mechanics only: what to fetch and where to put it, never wording from the
  book. A well meaning contributor who "improved" a step by quoting the booklet
  has to be turned down, and it is worth explaining that it is a legal line and
  not a style preference.
- **Both languages?** Lint catches a missing translation, but not a French
  string quietly left in English.

### 6. Everything else

Ordinary code review. Does it do what the pull request says, is it tested, does
it match the surrounding style. If the first five sections were clean, this is
the part where being wrong is cheap and fixable.

---

## Three questions worth asking of any pull request

1. **Does the change match the description?** A pull request titled "fix typo"
   that also touches the build is the oldest trick there is, and also what an
   inexperienced contributor does by accident when their IDE reformats a file.
2. **Would I understand this in six months?** You maintain this alone. Clever
   code you cannot re-read is a liability whoever wrote it.
3. **Am I taking on maintenance I do not want?** A feature you would never have
   built is a feature you will be fixing for years. It is fair to decline on
   those grounds, and it is kinder to say so early.

---

## Declining without losing the person

This is the part that wears maintainers down, so here are the words, ready to
use at midnight.

The principles underneath them: answer quickly even when the answer is no,
thank them for the work before saying anything else, give the reason rather than
a verdict, and never leave a pull request open for months because you cannot
face replying. Silence is the thing contributors remember.

### Declining a feature you do not want

> Thank you for this, and sorry for the slow reply.
>
> I am going to decline this one, and it is not about the quality of the work.
> [Reason: it is a direction I do not want to take the app / it adds something I
> would have to maintain alone for years / it needs a server, and the app is
> deliberately offline.]
>
> I should have made that clearer before you spent the time, and I have added a
> note to CONTRIBUTING.md so the next person asks first. If you would like to
> take one of the issues labelled "good first issue" instead, I would be glad of
> the help.

### Declining something that would break F-Droid or privacy

> Thank you for this. I cannot take it as it stands, and the reason is specific
> rather than a matter of taste.
>
> [Library] is not free software, so F-Droid would refuse to build the app and
> everyone installing from there would stop getting updates. The app also
> promises it collects nothing about its users, and this would make that untrue.
>
> If the underlying problem is [what they were solving], I would be glad to look
> at another way of doing it. Open an issue and let us work it out.

### Asking for substantial changes

> Thank you, this is genuinely useful and I would like to take it. Two things
> first:
>
> 1. [Specific, with a file and a line.]
> 2. [Specific.]
>
> The second one is a habit of this project rather than anything you could have
> known: [explain]. It is written down in CONTRIBUTING.md but not clearly
> enough, which is my fault.
>
> No rush, and tell me if you would rather I finished it off. Either is fine.

### When somebody edited generated JSON

> Thank you for spotting this, the correction is right.
>
> One thing that is entirely my fault for not making obvious: that file is
> generated from `tools/build_<name>.py`, so this edit would be wiped the next
> time anybody regenerates it. The same change in the generator will stick.
>
> If you would rather not, say so and I will move it across myself, and you keep
> the credit. Finding the mistake was the hard part.

### When you have gone quiet for weeks

> Sorry for the silence, this was not disinterest. [One sentence, honest.]
>
> Coming back to it now: [answer].

---

## Two things not to do

**Do not merge something you do not understand because refusing feels rude.**
You maintain it afterwards, alone, and the contributor will have moved on. "I am
not confident enough in this to maintain it" is a legitimate and respectable
reason.

**Do not leave it open indefinitely.** A pull request declined in a week with a
reason leaves someone who might come back. The same pull request open for six
months with no reply leaves someone who will not, and who tells other people
not to bother either.
