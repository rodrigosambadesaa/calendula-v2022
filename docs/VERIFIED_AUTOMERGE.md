# Automatic merging of verified owner pull requests

The GitHub Actions workflow `.github/workflows/verified-owner-automerge.yml`
can squash-merge **opted-in pull requests from the repository owner only**.
It is **not** an unrestricted bot that approves arbitrary incoming pull requests.

## How to opt in

1. Develop an isolated improvement on a `modernize/*` branch in this repository.
2. Open the pull request against `main`, with its title beginning with
   `[automerge] ` (including the trailing space).
3. Wait for **all three** workflows — **Android CI**,
   **Android emulator smoke (API 23, 33, 36)**, and
   **Android 17 runtime compatibility (API 37.0)** — to finish
   **successfully for the same head commit**.

When one of these workflows completes, the default-branch-only privileged
workflow rechecks the current PR, head commit, branch origin, owner identity
and all three workflow results. Only then does it request a squash merge through
GitHub's merge API, supplying the exact commit SHA so that changed heads are
rejected. The branch must also contain the current `main` commit.

A failed, pending, missing or cancelled latest CI/emulator/Android 17 run
prevents the automatic merge. An opted-in PR may need a new completed workflow event or a
manual dispatch after correcting a failed run. Branch protection and GitHub
merge restrictions still apply.

## Security properties

- The privileged workflow never checks out or executes PR content.
- External forks, bots, non-owner PRs, non-main targets, and branches outside
  `modernize/*` are never eligible.
- A passing build alone is insufficient: real Android emulator checks on
  API 23, 33, 36 and 37 are also required.
- Changes to the opt-in title, branch or head commit are revalidated.
- `workflow_dispatch` defaults to **dry-run**, allowing eligibility audits
  without modifying the repository.
- The workflow does not silently declare an app release safe: it merges code
  only. Medical data authenticity, real notification delivery and data
  migration remain independent release gates.

The repository's native auto-merge feature is not required for this workflow.
Keep the opt-in limited to small, reviewed owner changes with comprehensive
tests; do not use it for security- or data-migration-sensitive changes that
need manual review beyond CI.

## Post-merge verification of the actual `main` commit

**Important:** GitHub suppresses normal `push`-triggered workflows when a merge
is performed with the workflow's own `GITHUB_TOKEN`. Therefore, a successful
PR run does **not** imply that the newly generated squash commit on `main`
was tested. The post-merge `push` triggers remain useful for ordinary manual
merges, but they are not sufficient for this privileged auto-merge path.

After GitHub confirms an actual squash merge, the trusted workflow explicitly
dispatches `android-ci.yml`, `android-emulator-smoke.yml` and
`android-17-compatibility.yml` on **`main`**, using only its repository-scoped
`GITHUB_TOKEN` and `actions: write` permission. These explicit
`workflow_dispatch` events are supported by GitHub. It never checks out
or executes the untrusted PR revision in the privileged merge job.

The post-merge dispatch verifies the **combined default-branch commit**, not
just the PR head. Record each dispatched run's actual `head_sha` and result
before concluding the merged candidate is clean. A successful pre-merge check
and a successful API merge response **cannot** be used as evidence of
post-merge success. If dispatch fails, the merge has already happened; treat
the latest `main` as unverified until the missing workflow is rerun.
Multiple rapid merges may cancel superseded checks; only completed successful
runs for the current `main` commit count.

GitHub documentation: https://docs.github.com/en/actions/concepts/security/github_token
