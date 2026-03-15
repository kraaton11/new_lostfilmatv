# GitHub Setup

## Required Repository Settings

Configure the repository once before relying on the automated PR and release flow.

## Branch Protection

Protect the `main` branch with these settings:

- require pull requests before merging
- require status checks to pass before merging
- require the `pull-request-checks` workflow as a required check
- disable direct pushes to `main` for normal day-to-day work

## Actions Permissions

In `Settings -> Actions -> General`, allow GitHub Actions to:

- read and write repository contents
- create pull requests
- approve and merge pull requests when GitHub asks for explicit permission

## Required Checks

After the first PR run completes, add `pull-request-checks` as a required status check in the branch protection rule for `main`.

## Auto-Merge

Enable `Allow auto-merge` in the repository settings so the `open-pr` workflow can call `gh pr merge --auto --squash` after required checks go green.
