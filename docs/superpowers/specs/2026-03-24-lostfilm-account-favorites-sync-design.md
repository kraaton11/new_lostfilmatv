# LostFilm Account Favorites Sync Design

## Goal

Add account-synced LostFilm favorites to the Android TV app in two places:
- a secondary `Добавить в избранное` / `Убрать из избранного` action on `Details`
- an optional `Избранное` rail on `Home` that shows only new releases from favorite series

The feature should use the user's real LostFilm account state as the source of truth, stay watch-first on TV, and avoid rendering empty placeholder UI.

## Scope

This design covers:
- `app/src/main/java/com/kraat/lostfilmnewtv/data/network/`
- `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/`
- `app/src/main/java/com/kraat/lostfilmnewtv/data/model/`
- `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/`
- `app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStore.kt`
- focused repository, parser, view-model, and Compose tests for the new favorite behavior

Included:
- parsing favorite metadata from LostFilm details pages
- account-backed follow and unfollow requests from the TV app
- a Home rail for new releases from favorite series
- a user setting that enables or disables the Home favorites rail
- non-blocking error handling when favorite sync is unavailable

Excluded:
- a standalone `Избранное` screen or navigation destination
- an empty `Избранное` rail placeholder on `Home`
- Android TV home channel publishing for favorites in this iteration
- manual import, export, or editing of favorites outside LostFilm account sync
- changing the existing LostFilm authentication flow

## Source Validation

The site contract was re-checked on March 24, 2026 before writing this design.

Confirmed:
- LostFilm details pages expose a `FollowSerial(serialId, isMovie)` action in HTML
- LostFilm `main.min.js?ver=6` currently sends favorite updates through `POST /ajaxik.php`
- the request payload includes `act=serial`, `type=follow`, `id=<serialId>`, and `session=<UserData.session>`
- the current JS expects `result` values of `on` and `off`
- the current LostFilm JS uses the same `_FollowSerial` request shape for both series and movies; `isMovie` only changes UI copy and confirmation text
- unauthenticated probing confirmed the existence of `/my/`, `/my/type_0`, `/my/type_1`, and `/my/serials`
- unauthenticated probing also confirmed that these `/my/*` routes currently return a lightweight redirect wrapper back to `/` instead of usable feed content

Not fully confirmed without a live authenticated session:
- which exact `/my/*` route is the best source for the favorite-release feed
- the final authenticated HTML shape of the favorite-release listing

Because of that uncertainty, the repository should own route selection and HTML parsing for the Home favorites feed instead of leaking a specific `my` route into UI code.

## Product Intent

The current app already has one account-synced interaction pattern:
- `Details` can mark an episode watched on LostFilm
- `Home` can immediately reflect that watched state

Favorites should feel like the same family of behavior:
- `Details` is where the user explicitly manages the selected title
- `Home` is where the user benefits from that account preference

The experience should stay TV-first:
- the primary watch action remains dominant
- the favorite action is secondary, not co-primary
- missing account state should degrade quietly instead of taking over the screen

## Approved Direction

### Chosen concept: sync-first native integration

The approved direction is a native app integration that uses LostFilm account data directly:
- `Details` reads and writes favorite state against LostFilm
- `Home` loads a separate favorite-release feed from LostFilm account pages
- `Settings` gates whether the extra rail appears on `Home`

This avoids a fake local favorite system and avoids pretending that a filtered subset of already loaded `/new/` pages is a complete account view.

### Why this direction

Compared with a local-only favorite flag:
- account state stays consistent with LostFilm
- the user does not manage two different favorite systems
- the app can reflect favorite changes made outside the TV app

Compared with building the Home rail only from already loaded `/new/` pages:
- the rail can be complete even when the user has not paged far enough
- the rail remains an account feature, not a best-effort local guess

## UX Behavior

## 1. Details screen

`Details` gains a secondary favorite action that sits below or beside the existing hero action cluster without taking initial focus away from `Смотреть`.

Required states:
- `Добавить в избранное`
- `Убрать из избранного`
- `Сохраняем...`
- `Войдите в LostFilm`
- `Не удалось обновить избранное`

Behavior rules:
- the primary watch action remains the default focus target
- the favorite action is enabled only when the app has a valid LostFilm session and a resolvable favorite target
- if LostFilm state cannot be parsed reliably, the button stays visible but disabled rather than showing a guessed state
- on success, `Details` updates immediately without requiring a screen reload
- on failure, the button returns to the previous stable state and shows a short inline status message

The favorite toggle should support both series and movies when LostFilm exposes the button. In the currently verified LostFilm JS contract, both cases use the same `type=follow` request; `isMovie` affects only UI text. The Home rail still remains series-only because only favorite series generate new episodic releases.

## 2. Home screen

`Home` becomes a multi-rail surface:
- the existing all-new rail remains first and behaves as it does today
- a new `Избранное` rail may appear below it

The `Избранное` rail appears only when all of the following are true:
- the user enabled it in `Settings`
- the app currently has a valid LostFilm session
- the favorite-release feed loaded successfully
- the resulting item list is not empty

The app must not render:
- an empty favorites rail
- a special placeholder card
- a full-screen error caused only by favorite-feed failure

If the favorite feed fails, `Home` should continue rendering the standard `/new/` content with no extra rail.

## 3. Settings

The feature requires a user-controlled visibility preference for the Home rail.

To avoid adding a new high-level settings section in this iteration, the preference should live in the existing content-surfacing area represented by the current Android TV channel section.

Concrete placement:
- keep the left rail unchanged, including the existing `Канал Android TV` section entry
- extend the `CHANNEL` right-panel content in `SettingsScreen`
- keep the existing Android TV channel overview card and its three mode buttons at the top of that panel
- append a second subsection below those buttons with a small overview card titled `Главный экран`
- render the Home favorites preference there as the same TV-button family already used elsewhere, with two explicit states such as `Показывать` and `Скрывать`

The new control should read like:
- `Показывать полку Избранное на главном экране`

Behavior rules:
- the preference controls only `Home` inside the app
- it does not change Android TV channel publishing
- enabling it while logged out is valid; the rail simply stays hidden until a valid session exists

This keeps the settings change small while still making the feature user-controlled.

## Architecture And Responsibilities

## 1. Favorite metadata on details

The details layer should gain just enough metadata to drive the favorite UI and update flow.

Recommended fields on `ReleaseDetails`:
- `favoriteTargetId: Int?`
- `favoriteTargetKind: FavoriteTargetKind?`
- `isFavorite: Boolean?`

Where:
- `favoriteTargetId` is parsed from `FollowSerial(<id>, <isMovie>)`
- `favoriteTargetKind` distinguishes serial vs movie for UI text and request parity
- `isFavorite` reflects the parsed LostFilm button state when reliable

`isFavorite` should remain nullable because an uncertain parse is different from a confirmed `false`.

## 2. Dedicated favorite operations in the repository

The repository should expose favorite behavior explicitly instead of burying it inside generic details loading.

Recommended additions:
- `setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult`
- `loadFavoriteReleases(): FavoriteReleasesResult`

This keeps account-favorite behavior separate from:
- general page pagination
- torrent loading
- watched-state mutation

The repository remains the only layer that knows:
- how to resolve the correct LostFilm request
- which HTML source to parse for favorite releases
- how to merge refreshed favorite state back into cached details and Home state
- how to turn `detailsUrl` into a concrete favorite target id and ajax session token

Boundary rule:
- UI and view models call only `setFavorite(detailsUrl, targetFavorite)`
- the repository fetches or reuses the current details HTML, parses `favoriteTargetId`, `favoriteTargetKind`, and `UserData.session`, and decides whether a network call is possible
- the HTTP client receives only the low-level network parameters needed to perform the request and does not parse HTML itself

## 3. LostFilm network contract

The app should add a dedicated network method for favorite mutation instead of overloading watched-state code.

Recommended client contract:
- `toggleFavorite(favoriteTargetId: Int, ajaxSessionToken: String): FavoriteToggleNetworkResult`

Request shape should mirror the currently verified LostFilm site contract:
- `POST /ajaxik.php`
- `act=serial`
- `type=follow`
- `id=<favoriteTargetId>`
- `session=<UserData.session>`

This same request shape applies to both series and movies in the currently verified site JS. The `favoriteTargetKind` is still useful for UI strings and parser consistency, but it does not change the network payload in this iteration.

Because the current site toggles rather than submitting an explicit desired boolean, the repository should verify the resulting state and compare it against the requested target before claiming success.

## 4. Favorite-release feed loading

The Home favorites rail should come from a dedicated account feed rather than from the general `/new/` pagination path.

The repository should own route resolution with a small internal strategy such as:
1. try these exact candidate routes in order:
   - `/my/`
   - `/my/type_0`
   - `/my/type_1`
   - `/my/serials`
2. reject any response that matches the currently observed redirect wrapper back to `/`
3. treat a route as fully compatible only if the document contains at least one series episode link matching `/series/<slug>/season_<n>/episode_<n>/` and enough surrounding data to build a Home-safe card from that same document
4. if multiple routes are fully compatible, choose the first one in the fixed precedence order above and ignore the rest for this iteration
5. if a route is only partially compatible, such as containing episode links but not enough card data, reject it instead of merging data across routes
6. parse the accepted document into favorite-release summaries
7. fail closed if no candidate route produces a fully compatible document

UI code should never know which `/my/*` route won.

This keeps the inevitable site-shape uncertainty localized to one layer.

To keep planning deterministic, the first implementation step must commit one authenticated fixture file for the accepted route to `app/src/test/resources/fixtures/favorite-releases.html`. That fixture becomes the canonical parser contract for this iteration, and the chosen route path must be recorded in the fixture-producing test or helper so later work is pinned to one concrete source.

## 5. Home rail model

`Home` should stop assuming there is exactly one content rail.

Recommended direction:
- introduce a small rail UI model such as `HomeContentRail`
- keep one rail for all-new releases
- optionally append one rail for favorite releases

Each rail should own:
- stable rail id
- title
- items
- whether end-of-list paging applies

Only the all-new rail participates in page-based infinite loading in this iteration. The favorite rail is a fixed feed load.

## 6. Settings persistence

The new Home favorites preference belongs in `PlaybackPreferencesStore` with a dedicated boolean key.

The setting should be read:
- when building `SettingsViewModel`
- when constructing Home state

The setting should be written only from the settings UI, not inferred from auth state or current rail contents.

## State Management

## 1. Details state

`DetailsUiState` should explicitly model favorite interaction state rather than burying it in transient Compose locals.

Recommended state additions:
- `isFavoriteMutationInFlight`
- `favoriteStatusMessage: String?`

This allows tests to verify:
- busy state
- disabled state
- restored stable state after failure

## 2. Navigation callback back to Home

Favorite changes should trigger a Home refresh path similar to the existing watched-state callback.

When a favorite mutation succeeds:
- `Details` notifies navigation or shared state that Home content changed
- `Home` refreshes the favorite rail on return
- the all-new rail remains intact

The update should be targeted:
- no full app restart
- no unnecessary clearing of Home selection

## 3. Home loading rules

Home should load its data in two independent tracks:
- the existing `/new/` content load
- the optional favorites feed load

The favorite track must be non-blocking:
- it must not delay initial rendering of the main rail
- it must not replace the main rail's error handling
- it may finish later and append the extra rail when ready

This preserves the current Home reliability while letting favorites behave like an enhancement.

## Parsing Strategy

## 1. Details favorite parsing

The details parser should extract:
- whether a favorite button exists
- the LostFilm target id from `FollowSerial(...)`
- whether the button currently represents favorite-on or favorite-off

Reliable cues may include:
- button class differences such as `favorites-btn` vs `favorites-btn2`
- text and title strings from the current LostFilm page
- the `isMovie` argument from `FollowSerial`

The parser should treat contradictory cues as unknown and return nullable favorite state instead of guessing.

## 2. Favorite-release parsing

The favorite feed parser should output the same `ReleaseSummary` shape used by Home rails where possible.

The parser should prefer extracting:
- details URL
- title
- poster URL
- season and episode when applicable
- release date

If the authenticated favorite feed shape cannot provide every field that the all-new feed provides, the parser may map a minimal but still Home-safe `ReleaseSummary` as long as the card and bottom stage remain readable.

The parser should not attempt to infer watched state for the favorite rail unless the feed provides it clearly.

The canonical accepted fixture for this iteration is the committed authenticated document at `app/src/test/resources/fixtures/favorite-releases.html`. A feed document is considered parser-compatible only if it is not the redirect wrapper and contains real series episode links.

## Error Handling

The design should explicitly separate hard failures from enhancement failures.

Hard failures:
- normal Home `/new/` load fails
- details load fails

Enhancement failures:
- favorite button state cannot be parsed
- favorite mutation request fails
- favorite-release feed fails

Rules:
- enhancement failures must not take over the whole screen
- failed favorite mutation should leave the previous confirmed state intact
- failed favorite-release load should hide the rail, not replace Home content with an error
- an expired session should surface as a user-readable favorite-specific message, not a generic crash or empty UI mystery

## Testing Strategy

Implementation should follow TDD with failing tests before production code.

## 1. Parser tests

Add parser coverage for:
- parsing favorite target id from details HTML
- parsing series vs movie favorite targets
- parsing favorite-on vs favorite-off state
- returning unknown when cues conflict or are absent
- parsing a favorite-release feed fixture into Home-safe release summaries

## 2. Network and repository tests

Add repository coverage for:
- favorite mutation succeeds when LostFilm returns the requested resulting state
- favorite mutation fails when no authenticated session exists
- favorite mutation fails when the ajax session token cannot be found
- favorite mutation refreshes cached details favorite state on success
- Home favorite releases load without affecting ordinary page pagination
- favorite-feed failure leaves ordinary Home content intact

## 3. Details view-model and route tests

Add tests verifying:
- the favorite button becomes busy during mutation
- the button label and enabled state match parsed favorite state
- success updates the visible state
- failure restores the previous confirmed state
- missing auth shows the expected disabled messaging

## 4. Home tests

Add tests verifying:
- Home renders the favorite rail only when enabled and non-empty
- Home does not render an empty favorites rail
- Home still shows the all-new rail when favorite load fails
- the favorite rail appears below the main rail and does not break selection behavior

## 5. Settings tests

Add tests verifying:
- the new favorites-rail setting is rendered
- toggling the setting persists the new preference
- the setting summary reflects enabled or disabled state if a summary is shown in the left rail

## Risks And Mitigations

### Risk: the authenticated favorite feed route is less stable than expected

Mitigation:
- hide the concrete route behind repository code
- allow a small ordered route fallback
- add parser fixtures for the first working authenticated structure

### Risk: favorite mutation becomes out of sync because the site endpoint toggles state

Mitigation:
- issue mutations only from a known confirmed or requested target state
- verify resulting state before updating cached details
- treat ambiguous responses as failure, not silent success

### Risk: Home multi-rail work breaks current focus and paging behavior

Mitigation:
- keep paging attached only to the all-new rail in the first iteration
- introduce a narrow rail UI model rather than generalizing everything at once
- add focus and ordering regression tests for the new rail layout

### Risk: the settings location feels slightly overloaded

Mitigation:
- keep the preference wording specific to `Home`
- keep the Android TV channel controls unchanged
- treat this as a small scoped extension of content-surfacing settings, not a full settings information architecture rewrite

## Success Criteria

- `Details` can add or remove a title from LostFilm favorites using the authenticated account session.
- The primary watch action remains the dominant TV interaction on `Details`.
- `Home` can show a non-empty `Избранное` rail with new releases from favorite series when the user enabled it.
- `Home` never shows an empty favorites rail.
- Favorite failures degrade quietly without breaking ordinary browsing.
- The app's favorite state remains aligned with LostFilm account state rather than with a local-only flag.

## Next Step

After the user reviews and approves this document, the next step is to write a focused implementation plan for LostFilm account favorites sync.
