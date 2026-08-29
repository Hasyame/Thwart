# Repository settings to apply by hand

Things that cannot live in a file. Each item says what it protects against, so
you can judge whether it is worth it rather than ticking boxes on trust.

Two repositories are involved:

- **`Hasyame/Thwart`**, public. What users and F-Droid see. Contributions arrive
  here, because nobody can fork a repository they cannot see.
- **`Hasyame/Thwart-dev`**, private. Where work happens.

Where a `gh` command can do the job it is given. Everything else is web only.

---

## Already correct, nothing to do

Checked on both repositories:

- **Default workflow permissions: read.** A workflow that is not explicitly
  granted more cannot write to the repository, so a compromised action cannot
  push to your branches.
- **Workflows cannot approve pull requests.** Otherwise a workflow could satisfy
  your own review requirement.

---

## 1. Actions, on `Hasyame/Thwart` only

**Settings → Actions → General**

- [ ] **Fork pull request workflows from collaborators and outside
      collaborators: require approval for all external contributors.**
      *Protects against:* a stranger's first pull request running their code on
      a runner the moment they open it. With this on, nothing runs until you
      have looked at the diff. This is the single most valuable setting on this
      page, because CI runs `./gradlew`, and a pull request can change what
      Gradle does.

- [ ] Leave **Allow all actions and reusable workflows** as it is, or tighten to
      "Allow actions created by GitHub and select non-GitHub actions" and list
      `gradle/actions@*`.
      *Protects against:* a future workflow pulling in an action nobody vetted.
      Optional: the workflows already pin every action to a commit SHA, which
      covers the same ground.

The private repository needs none of this. Nobody but you can open a pull
request against it.

---

## 2. Pull request settings, on `Hasyame/Thwart`

**Settings → General → Pull Requests**

```bash
gh api -X PATCH repos/Hasyame/Thwart \
  -F allow_merge_commit=false \
  -F allow_rebase_merge=false \
  -F allow_squash_merge=true \
  -F delete_branch_on_merge=true
```

- [ ] **Squash merging only.** *Protects against:* a contributor's twelve
      "wip", "fix typo", "try again" commits becoming permanent history. One
      commit per change also makes `git revert` meaningful.
- [ ] **Automatically delete head branches.** *Protects against:* a fork's merged
      branches accumulating.

---

## 3. The branch contributors land on, `Hasyame/Thwart`

**Settings → General → Default branch**

- [ ] Consider setting the default branch to **`dev`**.
      *Protects against:* every contributor opening their pull request against
      `main` by accident, because GitHub preselects the default branch. You then
      retarget each one by hand, or merge to `main` something that never went
      through beta.

The cost: anyone landing on the repository sees `dev` first, which may be ahead
of what is released. For a project where `main` is what users run, that is worth
a thought. My view is that retargeting every pull request by hand is the bigger
tax, so I would switch it.

```bash
gh api -X PATCH repos/Hasyame/Thwart -f default_branch=dev
```

---

## 4. Rulesets on `Hasyame/Thwart`

**Settings → Rules → Rulesets → New branch ruleset**

There are none today, so `main` is currently unprotected.

### Ruleset "main is released code"

- Target: **`main`**
- [ ] Restrict deletions
- [ ] Block force pushes
- [ ] Require a pull request before merging, 1 approval
- [ ] Require status checks to pass:
      - `Build and test`
      - `F-Droid compliance`
- [ ] Bypass list: **yourself**

*Protects against:* code reaching a release, and therefore signed APKs on other
people's phones, without a build, a test run and an F-Droid check. The bypass
entry is there so you can still fix something at midnight without fighting your
own rules.

**Deliberately not required:** `Android Lint` and `Campaign data`. Lint is set to
`warningsAsErrors`, so a new library version can turn it red through no fault of
the contributor. Campaign data depends on MarvelCDB being reachable, and someone
else's downtime must never block a contribution. Both are worth reading, neither
is worth blocking on.

### Ruleset "dev is mine"

- Target: **`dev`**
- [ ] Restrict updates
- [ ] Restrict deletions
- [ ] Block force pushes
- [ ] Bypass list: **yourself**

*Protects against:* anyone but you pushing to `dev`, if you ever add a
collaborator. Pull requests from forks can still target `dev`, which is what you
want: contributions get a beta stage before they reach users, and you decide
what enters.

### On the private repository

**Rulesets are not available there.** GitHub returns "Upgrade to GitHub Pro or
make this repository public". They would protect against nothing anyway, since
you are the only person with access. Nothing to do, but worth knowing so you do
not go looking for the menu.

---

## 5. Dependabot, on `Hasyame/Thwart`

**Settings → Code security**

Vulnerability alerts are **currently off**.

```bash
gh api -X PUT repos/Hasyame/Thwart/vulnerability-alerts
```

- [ ] **Dependabot alerts.** *Protects against:* shipping a known vulnerable
      library for months because nobody told you. This is the one that matters:
      it is a notification, it costs nothing, and a signed APK on someone's
      phone is exactly where you do not want a known hole.
- [ ] **Dependabot security updates.** Opens a pull request for the fix rather
      than only telling you.

`.github/dependabot.yml` is already in the repository and handles routine
version bumps: grouped, monthly, targeting `dev`. Security updates ignore that
schedule and arrive immediately.

---

## 6. CodeQL, on `Hasyame/Thwart`

**Settings → Code security → Code scanning → Set up → Default**

- [ ] Enable CodeQL, default setup, for Kotlin and Java.
      *Protects against:* a class of bug a compiler does not see, in code you
      did not write and are reviewing at midnight. Free on public repositories.

Use **default setup** rather than the advanced workflow: it needs no YAML and
GitHub keeps it current. Treat its findings as advisory. Do not make it a
required check.

---

## 7. The release environment, on `Hasyame/Thwart`

**Only if you decide CI should sign. It does not today, and my recommendation is
to leave it that way for now.**

**Settings → Environments → New environment**, named `release`

- [ ] Required reviewer: **yourself**
      *Protects against:* a tag, pushed by accident or by anyone who ever gains
      write access, turning straight into a signed APK. Nothing is signed until
      you press approve.
- [ ] Deployment branches and tags: restrict to tags matching `v*`
      *Protects against:* the signing secrets being reachable from any other
      branch or workflow.
- [ ] Environment secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
      `KEY_PASSWORD`
      Environment secrets, not repository secrets. A repository secret is
      readable by every workflow in the repository; an environment secret is
      readable only by a job that names that environment and has passed its
      approval.

**Understand what you are accepting.** Today your signing key exists on one
machine. Adding these secrets copies it into GitHub's infrastructure, and from
then on a compromise of your GitHub account is a compromise of your signing key.
The protections above are real but not absolute.

The alternative, which costs one command per release: keep signing local, build
the APK yourself, and let the workflow sit unused.

---

## 8. What is deliberately not here

- **Requiring signed commits.** It would lock out every contributor who has not
  set up GPG, which is most of them, to solve a problem you do not have.
- **CODEOWNERS review enforcement on the private repository.** You are the only
  person there.
- **Dependabot on `Thwart-dev`.** The same dependencies, watched twice, is twice
  the pull requests for one fix.
