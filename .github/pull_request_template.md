## What this changes

<!-- A sentence or two in plain language. If it fixes an issue, write "Fixes #12". -->

## Why

<!-- What was wrong or missing. If a campaign booklet says otherwise, quote the
     page: the booklet always wins over the app. -->

## Checklist

Tick what applies. **An unticked box is not a problem**, it just tells me what to
look at. If something here is unfamiliar, say so in the pull request and I will
help rather than send you away.

- [ ] This targets the **`dev`** branch, not `main`
- [ ] I ran `./gradlew testDebugUnitTest` and it passed, or I could not run it
      and would like help
- [ ] If I changed text, I changed **both** `values/strings.xml` and
      `values-fr/strings.xml`
- [ ] If I changed a campaign, I edited the generator in `tools/` and ran it,
      rather than editing the JSON in `assets/campaigns/` by hand
      (Galaxy's Most Wanted is the exception: it has no generator)
- [ ] I have not added any rules text or card text from a printed booklet
- [ ] I have not added a new dependency, or if I have, I have said which and why

## Anything you are unsure about

<!-- Genuinely: write it here. Saying "I could not work out how to test this"
     is more useful than silence, and it is not held against you. -->
