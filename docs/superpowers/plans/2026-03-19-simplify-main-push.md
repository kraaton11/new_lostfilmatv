# Simplify Main Push Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the mandatory PR path for `main` while keeping `verify` as a post-push safety signal.

**Architecture:** Update repository automation to fit a direct-push model: run verification on pushes to `main`, remove the auto-PR workflow, and relax GitHub branch protection so pushes are no longer blocked by PR-only rules. Keep the release flow unchanged.

**Tech Stack:** GitHub Actions, GitHub branch protection API, Gradle Android build verification

---

### Task 1: Switch CI to direct-push verification

**Files:**
- Delete: `.github/workflows/open-pr.yml`
- Modify: `.github/workflows/pull-request-checks.yml`
- Modify: `docs/github-setup.md`

- [ ] Remove the auto-PR workflow so branch pushes no longer create or manage PRs.
- [ ] Make `verify` run on `push` to `main` and keep optional `pull_request` coverage.
- [ ] Rewrite the GitHub setup doc for the direct-push model.

### Task 2: Relax main branch protection

**Files:**
- Remote only: GitHub branch protection for `main`

- [ ] Remove the pull-request-only requirement from `main`.
- [ ] Remove blocking branch-protection checks tied to PR merge flow.
- [ ] Keep `verify` running from Actions after direct pushes.

### Task 3: Verify and publish

**Files:**
- Verify: `.github/workflows/pull-request-checks.yml`
- Verify: `docs/github-setup.md`

- [ ] Run repository verification commands relevant to the updated workflow.
- [ ] Commit the workflow and documentation changes.
- [ ] Push directly to `main` to confirm the simplified path works.
