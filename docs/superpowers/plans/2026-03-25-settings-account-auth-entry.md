# Settings Account Auth Entry Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the top-header `Войти/Выйти` action from Home and move account entry to a dedicated `Аккаунт` section inside Settings.

**Architecture:** Keep the existing auth flow and `AuthViewModel` ownership in navigation. Home only loses its header auth action, while Settings gains a new rail section that displays auth status and exposes the same `Войти`/`Выйти` callback already used by Home. The centered Favorites login CTA on Home stays unchanged.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Navigation Compose, Robolectric/JUnit unit tests

---

## Planned File Structure

- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeHeader.kt`
  Responsibility: Remove the top-header auth action and keep header focus order stable.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
  Responsibility: Stop creating/passing the removed header auth focus requester while keeping the centered Favorites login CTA intact.
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`
  Responsibility: Lock that Home no longer renders the header auth action.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
  Responsibility: Pass auth state and auth callback into Settings instead of Home header.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt`
  Responsibility: Accept `isAuthenticated` and `onAuthClick` and forward them into the settings screen.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`
  Responsibility: Add a dedicated `Аккаунт` rail section, summary text, and auth action panel.
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt`
  Responsibility: Lock the new account section summary and `Войти`/`Выйти` action behavior.

## Chunk 1: Remove Home Header Auth Entry

### Task 1: Write the failing Home header regression test

**Files:**
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`

- [ ] **Step 1: Add a RED test that asserts the Home header no longer shows `home-action-auth`.**

- [ ] **Step 2: Run the focused Home screen test and verify RED.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest"
```

Expected: FAIL because Home still renders the header auth action.

### Task 2: Implement the minimal Home header change

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeHeader.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`

- [ ] **Step 1: Remove the Home header auth button and related focus wiring.**

- [ ] **Step 2: Keep the centered Favorites login-required CTA untouched.**

- [ ] **Step 3: Re-run the focused Home screen test and verify GREEN.**

- [ ] **Step 4: Commit the Home header slice.**

## Chunk 2: Add Account Section to Settings

### Task 3: Write the failing Settings account-section tests

**Files:**
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt`

- [ ] **Step 1: Add a RED test that the rail includes an `Аккаунт` section with the correct summary for anonymous state.**

- [ ] **Step 2: Add a RED test that selecting `Аккаунт` shows status text and invokes the `Войти` action.**

- [ ] **Step 3: Add a RED test that authenticated state shows `Выйти` and invokes the same callback.**

- [ ] **Step 4: Run the focused settings test class and verify RED.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest"
```

Expected: FAIL because Settings has no account section or auth action yet.

### Task 4: Implement the minimal Settings auth section

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Thread `isAuthenticated` and `onAuthClick` from navigation into Settings.**

- [ ] **Step 2: Add the `Аккаунт` rail item, summary, and right-panel content with `Войти`/`Выйти`.**

- [ ] **Step 3: Keep existing settings sections and focus behavior stable.**

- [ ] **Step 4: Re-run the focused settings test class and verify GREEN.**

- [ ] **Step 5: Run the combined Home + Settings unit tests as a safety pass.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest" --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest"
```

Expected: PASS

- [ ] **Step 6: Commit the Settings account slice.**
