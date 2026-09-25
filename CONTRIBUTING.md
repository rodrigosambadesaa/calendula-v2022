# Contributing to this fork

Contributions are welcome. This repository is an **unofficial GitHub fork/mirror** of the public
CiTIUS `Calendula-v2022` source. It is maintained independently and is not an official CiTIUS,
USC, SERGAS or Xunta de Galicia repository.

Calendula is licensed under the terms of the [GNU General Public License v3](LICENSE.md).
By submitting code to this repository, you agree that your contribution is distributed under
the same applicable GPL terms.

## Before you start

Please check the open issues and pull requests before starting substantial work so duplicate
changes can be avoided.

For this GitHub fork:

- `main` is the integration branch and should remain buildable.
- Create a focused topic branch from the current `main`.
- Keep unrelated changes in separate pull requests.
- Do not rewrite or squash away historical upstream authorship.
- Preserve existing copyright and license notices.
- Run the relevant Gradle checks before requesting review.

A typical workflow is:

```bash
git checkout main
git pull --ff-only origin main
git checkout -b feature/my-change
```

After making changes, open a pull request back to `main`.

## Validation

The repository CI currently validates the legacy Android baseline used by this fork. Before
submitting a pull request, run as much of the following as your local environment supports:

```bash
./gradlew assembleCiDebug
./gradlew testCiDebugUnitTest
./gradlew assembleCiDebugAndroidTest
./gradlew lintCiDebug
```

The project intentionally preserves a legacy compatibility baseline while modernization is
performed incrementally. Avoid dependency or toolchain upgrades that silently raise
`minSdk`, require a newer `compileSdk`, or change library APIs without updating and testing
the affected code.

## Upstream relationship

The original public source is hosted by CiTIUS at:

- https://gitlab.citius.gal/calendula-mp/calendula-mp

A local clone may keep that repository as an `upstream` remote:

```bash
git remote add upstream https://gitlab.citius.gal/calendula-mp/calendula-mp.git
git fetch upstream
```

Changes developed here may later be proposed upstream when appropriate, but this fork does
not imply that CiTIUS has reviewed, endorsed or adopted them.

## Historical contribution model

The original Calendula project used a `master` / `develop` / release-branch workflow and
referenced the historical `citiususc/calendula` GitHub repository, CLAHub, Google Play beta
testing and POEditor. Those instructions belong to the historical upstream project and should
not be treated as the contribution workflow for this fork.

If you are preparing a contribution specifically for an upstream CiTIUS repository, follow
the contribution and governance requirements published by that upstream at the time you
submit it.
