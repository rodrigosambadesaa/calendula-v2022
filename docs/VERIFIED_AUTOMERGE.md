# Automatic merging of verified owner pull requests

The GitHub Actions workflow `.github/workflows/verified-owner-automerge.yml`
can squash-merge **opted-in pull requests from the repository owner only**.
It is **not** an unrestricted bot that approves arbitrary incoming pull requests.

## How to opt in

1. Develop an isolated improvement on a `modernize/*` branch in this repository.
2. Open the pull request against `main`, with its title beginning with
   `[automerge] ` (including the trailing space).
3. Wait for both **Android CI** and **Android emulator smoke (API 23, 33, 36)**
   to finish **successfully for the same head commit**.

When either workflow completes, the default-branch-only privileged workflow
rechecks the current PR, head commit, branch origin, owner identity and both
workflow results. Only then does it request a squash merge through GitHub's
merge API, supplying the exact commit SHA so that changed heads are rejected.

A failed, pending, missing or cancelled latest CI/emulator run prevents the
automatic merge. An opted-in PR may need a new completed workflow event or a
manual dispatch after correcting a failed run. Branch protection and GitHub
merge restrictions still apply.

## Security properties

- The privileged workflow never checks out or executes PR content.
- External forks, bots, non-owner PRs, non-main targets, and branches outside
  `modernize/*` are never eligible.
- A passing build alone is insufficient: real Android emulator checks on
  API 23, 33 and 36 are also required.
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
