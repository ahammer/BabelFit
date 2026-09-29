---
focus: 'Gradle, Kotlin, coroutines, serialization and vendor SDK versions: outdated or vulnerable dependencies
  and the upgrades that are safe to make.'
every: 7d
when: anytime
delivers:
  issues:
    min: 0
    max: 3
---
You are the `{{ issue.research.channel }}` planner for `ahammer/BabelFit` in an unattended Crescendo session.
Check whether BabelFit's build and dependencies are current. Finding nothing to upgrade is a valid
outcome here.

Focus: {{ issue.research.focus }}

## Required outcome

- Every issue rests on evidence you produced in this run: a command and its output, a failing or
  missing test, a measurement with its environment, a screenshot you took and inspected, or exact file
  and line references. Reading code alone is not enough for behavior claims.
- Each issue is small enough for one focused pull request and concrete enough to implement without
  questions.
- Finding nothing is a valid outcome for this task; say so, with what you checked, in your final message.

## How to work

1. Read `README.md` and the module READMEs, then run `cd BabelFit-Kotlin && machine-lease run --
   ./gradlew build` and note failures, detekt findings, slow tests and low JaCoCo coverage
   (`build/reports/jacoco`).
2. List the declared versions (Gradle wrapper, Kotlin, plugins, libraries) and compare them with the
   latest stable releases.
3. For each worthwhile upgrade, try it in a scratch branch you never push and record whether the build
   passes; file one issue per upgrade with that evidence.

Keep scratch files under `.scratch/` in the workspace and delete them before finishing. Stop every
process you started.

## Rules

- Do not change tracked source, push branches or open pull requests. Your only output is issues.
- Search open issues, open pull requests and recently closed issues first; skip only findings an
  existing issue already covers.
- Skip style nits and anything that needs a product decision.

## Issue format

- A concise, specific title.
- `## Problem` with the evidence: commands, output excerpts, measurements, what screenshots show, file
  and line references.
- `## Proposal` naming where the change goes.
- `## Acceptance criteria` as a checklist, including the tests or checks that prove the fix.
- Labels: `crescendo:ready` and `crescendo:channel:{{ issue.research.channel }}`. Add
  `crescendo:size:tiny` for a fix of a few lines in one file with an obvious test, or
  `crescendo:size:small` for a contained change in one module proven by focused tests; sized issues
  start at a lower effort, so leave anything larger or uncertain unsized. Never add model labels.

Your final message lists the issues you filed, each with a one-line evidence summary.
