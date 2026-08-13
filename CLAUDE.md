# CLAUDE.md — ChattyChat

Context for continuing this project in Claude Code. Rewritten 2026-08-13 (second pass) from a full read of the
current tree (backend + frontend), after the "added security layers" commit. **Where it says "verify", check the
actual code before trusting it** — this file drifts from the code the moment someone edits without updating it. The
first pass of this rewrite (same day) was itself based on a stale partial re-read and missed real changes — don't
trust a "re-read" that only re-reads the files that seem relevant; re-read the whole tree.

---

## What this is

A small real-time chat app: Spring Boot backend + a vanilla-JS single-page frontend (branded "AriChat" in the UI).
Users log in with Google or GitHub, see/create rooms, and exchange messages in a room over WebSocket/STOMP. Messages
are persisted to Postgres and loaded as history when a user enters a room.

Purpose is **learning**, with a stretch goal of possibly running it as a small friends-and-family tool. It is **not
production-ready** (see "Known gaps").

Auth status: **Google OAuth2 authentication itself works** (session created, `User` row provisioned, CSRF cookie
issued, `/v1/users/me` resolves correctly). **But first-time onboarding is currently broken**: any brand-new Google
user gets sent to the display-name screen (`displayName == null`), and the `PATCH /v1/users/{id}` call that screen
makes always returns 403 — see Known gaps #1. Nobody who hasn't already got a `displayName` set in the DB some other
way can currently reach the rooms screen. **GitHub OAuth2 is still broken** — see Known gaps #2, unchanged since
last review.

---

## Stack

- Java 21, Spring Boot (Spring Framework 7.x, Spring Data JPA 4.x, Hibernate 7.4).
- PostgreSQL (via HikariCP). Driver `postgresql-42.7.x`.
- Auth: Spring Security `oauth2Login`, session-cookie based (not resource-server/JWT). Sessions are stored in
  Postgres via `spring-session-jdbc` (`spring.session.store-type=jdbc`), not in-memory — survives app restarts.
- CSRF: enabled (not disabled), cookie-based token (`CookieCsrfTokenRepository.withHttpOnlyFalse()` +
  `CsrfTokenRequestAttributeHandler`) so the frontend JS can read the `XSRF-TOKEN` cookie and echo it back as
  `X-XSRF-TOKEN` on state-changing requests. A `CsrfCookieFilter` forces the deferred token to actually render into
  the cookie on every request.
- `spring.config.import=file:.env[.properties]` — **no longer `optional:`** (it was before). The app now hard-fails
  to boot if `.env` is missing, instead of silently running without secrets. Operational note, not a bug.
- Messaging: STOMP over SockJS, in-memory `SimpleBroker`. `WebSocketConfig`'s STOMP endpoint now only allows origin
  `http://localhost:8080` — the earlier `setAllowedOriginPatterns("*")` wildcard is gone.
- Frontend: plain JS, no build step, but **now split into ES modules** loaded via `<script type="module" src="app.js">`
  (not a single IIFE anymore): `config.js`, `state.js`, `api.js`, `ws.js`, `ui.js`, `utils.js`, `app.js` (entry point),
  plus `index.html` / `styles.css`. SockJS + stomp.js from CDN.
- Frontend talks to `http://localhost:8080` (hardcoded `API_BASE` / `WS_URL` in `config.js`).

---

## Package / layout (read from current tree)

Backend package root `com.chattychat`, layered controller → service → repository:

- `com.chattychat.Config`
    - `SecurityConfig` — CSRF, authorization rules, `oauth2Login` wiring, custom success handler
    - `WebSocketConfig` — STOMP/SockJS registration
- `com.chattychat.Controller`
    - `UserController` — REST, `/v1/users` (list, get-by-id, `/me`, PATCH display name — **PATCH is currently
      broken, see Known gaps #1**)
    - `RoomController` — REST, `/v1/rooms` (list, create)
    - `MessageWSController` — STOMP `@Controller`. Now has both `@MessageMapping` (resolves sender from the STOMP
      `Principal`, not the payload — see Auth model) and a `@MessageExceptionHandler` that routes failures to the
      sender's private `/user/queue/errors` queue.
    - `MessageRestController` — REST `@RestController`, `/v1/rooms/{room}/messages` history endpoint
- `com.chattychat.Services` — `UserService`, `RoomService`, `MessageService`, `CustomOAuth2UserService`
- `com.chattychat.Repositories` — `UserRepository`, `RoomRepository`, `MessageRepository`
- `com.chattychat.Entities` — `User`, `Room`, `ChatMessage`, `RoomMember` + `RoomMemberId` (composite-key embeddable;
  **`RoomMember` is currently unused** — no repository, service, or controller references it. Looks like scaffolding
  for a future "room membership" feature; safe to ignore until something actually wires it up. `User`'s PK is `UUID`
  again, matching `RoomMemberId.userId`'s type, so the `@MapsId` type-mismatch risk noted in Gotchas is currently
  dormant, not active)
- `com.chattychat.dto` — `InboundMessageDTO` (now just `{content}`, see Auth model), `OutboundMessageDTO` (dropped
  the `room` field), `UserDTO`, `RoomDTO`, `UpdateNameRequestDTO`, `ErrorDTO` (new — `{error}`, payload for
  `/user/queue/errors`), `AuthUser` (the OAuth2 principal type, see Auth section)

Note the package names are capitalized (`Controller`, `Services`, `Entities`, `Config`) — unusual for Java convention
(normally lowercase), but that's the existing choice; stay consistent.

---

## Data model (read from entities)

Tables: `Users`, `Room`, `messages`. (Naming is inconsistent — capitalized vs lowercase, singular vs plural. Not
worth churning now, but be aware when writing raw SQL/migrations.)

- `Users`: `user_id` (UUID PK, `@GeneratedValue(strategy = UUID)`), `provider` (String), `provider_id` (String),
  `first_name`, `last_name`, `email` (nullable), `display_name` (nullable — null until the user picks one).
  Unique constraint on `(provider, provider_id)` — this is the real identity anchor, the surrogate `user_id` UUID is
  just the internal PK every other table's FK points at.
- `Room`: `id` (UUID), `name` (unique), `created_at`, `last_updated_at`. Has `@PrePersist`/`@PreUpdate` hooks that
  stamp both timestamps automatically — don't set them manually from a service.
- `messages`: `id` (UUID), `content`, `room_id` (FK → Room), `sender_id` (FK → Users), `sent_at`. Both FKs are plain
  `@ManyToOne` (NOT unique — see gotcha).

`ChatMessage` uses `@ManyToOne` for `sender` and `room` (EAGER by default, unchanged from before).

`ddl-auto` is currently `update` (not `create`) — schema persists across restarts now. The `create` line is still
present but commented out in `application.properties`.

---

## Auth model — how login actually works

This is new since the last version of this doc; read it before touching anything OAuth2-related.

**Flow (Google, the one that works):**
1. Frontend renders two plain `<a href>` buttons on `screen-login` in `index.html`: `/oauth2/authorization/google`
   and `/oauth2/authorization/github`. No JS involved — real browser navigation.
2. Spring redirects to Google's consent screen, then Google redirects back to
   `/login/oauth2/code/google?code=...&state=...` (the only endpoint that receives anything directly from the
   provider in the browser-facing sense — it's just an auth code, not profile data).
3. Server-to-server: Spring exchanges the code for tokens, then calls `CustomOAuth2UserService.loadUser()`
   ([CustomOAuth2UserService.java](src/main/java/com/chattychat/Services/CustomOAuth2UserService.java)), which reads
   OIDC claims (`sub`, `given_name`, `family_name`, `email`, `name`) off the fetched `OAuth2User`, looks up
   `User` by `(provider, providerId)`, creates the row on first login (defaulting missing names to `"Unknown"`), and
   returns a custom `AuthUser` principal (a `record` implementing `OAuth2User`) carrying the app's surrogate
   `userId`, `provider`, `providerId`, and a display `name`.
4. `SecurityConfig` wires this via `.oauth2Login(oauth2 -> oauth2.userInfoEndpoint(userInfo ->
   userInfo.userService(customOAuth2UserService)).successHandler(new
   SimpleUrlAuthenticationSuccessHandler("http://localhost:8080")))` — success always redirects to a **hardcoded**
   localhost URL (see Known gaps).
5. Session (backed by `spring-session-jdbc`) now holds the `AuthUser` principal; browser has a session cookie +
   `XSRF-TOKEN` cookie.
6. Frontend's `initializeApp()` (`app.js`) calls `GET /v1/users/me` on every boot. `UserController.getCurrentUser`
   reads `@AuthenticationPrincipal AuthUser`, looks the user back up by `(provider, providerId)`, and returns the
   `UserDTO`. 401 → show `screen-login`. 200 with `displayName == null` → show the display-name screen (`screen-name`,
   `PATCH /v1/users/{id}`). 200 with a `displayName` → straight to rooms. **This is the localStorage-free identity
   model** — no client-supplied user id anywhere anymore for the REST/session layer.

**Why GitHub is broken, not just unfinished:** `CustomOAuth2UserService.loadUser()` unconditionally reads
`oAuth2User.getAttribute("sub")` for `providerId`, and `"given_name"`/`"family_name"` for names. Those are OIDC
claim names — Google has them because Google speaks OIDC. GitHub does **not** speak OIDC; its `/user` payload uses
`"id"` (a number, not `"sub"`) and has no `given_name`/`family_name` at all, only a single `"name"` field (which can
itself be null). Logging in via GitHub right now means `providerId` resolves to `null`, and since `User.provider_id`
is `nullable = false`, the insert fails. This needs the same `registrationId`-branching fix discussed earlier — read
`"id"` (cast to `String`) for GitHub, `"sub"` for Google — before GitHub can work at all, not just before it's
"complete." Nothing about this changed in the "added security layers" commit — still exactly as broken as before.

**New risk: Google's requested scope was narrowed and might no longer request what `CustomOAuth2UserService` needs.**
`application.properties` now sets `spring.security.oauth2.client.registration.google.scope=profile,email` —
`openid` is not in that list. Google's userinfo endpoint (`https://www.googleapis.com/oauth2/v3/userinfo`, which is
what `CommonOAuth2Provider.GOOGLE` points at) is the OIDC userinfo endpoint and typically requires the `openid`
scope to have been granted. `CustomOAuth2UserService` still resolves `providerId` from the OIDC-only `sub` claim.
**I have not run a live login to confirm this actually fails** — I don't have credentials or a running instance —
but the dependency is real: if the userinfo call now comes back without `sub` because `openid` wasn't granted, new
Google logins would fail to provision a `User` row the same way GitHub logins do. Re-test this live before trusting
it, or just add `openid` back to the scope list since nothing here needs it removed.

**Chat/STOMP identity resolution — FIXED since last review.** `InboundMessageDTO` is now just `{content}` — no more
client-supplied `senderId`. `MessageWSController.sendToRoom` takes a `Principal` parameter, casts it to
`Authentication`, pulls the `AuthUser` off `getPrincipal()`, and passes `user.getUserId()` into
`MessageService.save()` explicitly. This is the fix the previous version of this doc was waiting on (old gap #3) —
it landed. The manual `(Authentication) principal` cast works because `/ws/**` requires HTTP-level authentication
and Spring Security's request principal is always the `Authentication` object, but it's an implicit assumption
baked into an unchecked cast rather than using `@AuthenticationPrincipal AuthUser` (as `UserController` already
does) — would throw `ClassCastException` instead of failing gracefully if that assumption ever stopped holding.
Minor robustness note, not urgent.

**Error feedback — FIXED since last review.** `MessageWSController` now has a `@MessageExceptionHandler` that
catches any exception from `sendToRoom` (unknown room, unknown user, etc.) and sends an `ErrorDTO` to the sender's
private `/user/queue/errors` destination via `SimpMessagingTemplate.convertAndSendToUser`. The frontend already
subscribes to this in `ws.js` (`onConnected`) and renders it as a notice. `AuthUser.getName()` was changed to
*always* return `userId.toString()` (previously fell back to a human display name) — this is the right call, since
`convertAndSendToUser` routes by `Principal.getName()`, and a human name isn't guaranteed unique, so it could
misroute error frames between two users who happen to share a name. Keep it this way; don't revert to a display
name for `getName()`.

---

## HTTP + messaging contract

REST:

- `GET  /v1/users` → all users (no auth check visible beyond the blanket `/v1/**` rule — mostly a debug endpoint).
- `GET  /v1/users/{userId}` → `UserDTO` by surrogate UUID.
- `GET  /v1/users/me` → current session's `UserDTO`, 401 if not authenticated or if the `AuthUser` can't be
  re-resolved to a `User` row.
- `PATCH /v1/users/{userId}` body `{ "displayName": "..." }` → **currently broken, always returns 403.** A
  self-scoping check was added (attempting to fix the old "not scoped to your own user" gap) but it compares the
  wrong types: `!userId.equals(authUser.getProviderId())` compares a `UUID` (the path variable) against a `String`
  (the provider's raw id, e.g. Google's `sub`). `UUID.equals()` returns `false` for any non-`UUID` argument by
  contract, so this condition is unconditionally `true` and the endpoint 403s every request, including the
  rightful owner's. **This breaks the entire first-login onboarding flow** — see Known gaps #1. Fix: compare
  against `authUser.getUserId()` (also a `UUID`), not `getProviderId()`.
- `GET  /v1/rooms` → array of rooms (`{id,name}` or `["name"]`; frontend normalizes).
- `POST /v1/rooms` body `{ "name": "..." }` → created room.
- `GET  /v1/rooms/{room}/messages` → `OutboundMessageDTO[]`, oldest-first (history).

STOMP:

- Connect endpoint: `/ws` (SockJS, origin locked to `http://localhost:8080`).
- App prefix `/app`; send destination `/app/chat.send/{room}`.
- Topic (broadcast, public): `/topic/chat/{room}`.
- Private per-user queue (new): `/user/queue/errors` — carries `ErrorDTO { error }` when a send fails server-side.
- Inbound payload from client: `{ content: "..." }` **only** — no more `senderId`. The sender is resolved
  server-side from the authenticated STOMP `Principal` (see Auth model above — this is fixed now).
- `OutboundMessageDTO` carries: `id`, `senderId`, `from` (sender's `displayName`, falling back to `firstName` — the
  old gotcha about it using `firstName` unconditionally is gone), `content`, `sentAt`. **The `room` field was
  dropped** — if any frontend code still reads `m.room` off an inbound frame, it'll always be `undefined` now
  (checked: nothing in the current frontend reads it, but flag this if a future change adds room-name display in
  the message list).

Authorization rule in `SecurityConfig`: only `/v1/**` and `/ws/**` require authentication; everything else
(`anyRequest().permitAll()`) is open by default. Inverted from "deny unless listed" — fine at this size, but revisit
before adding more routes.

---

## Key design decisions

- **Surrogate UUID PK on `User`, with `(provider, providerId)` as a separate unique lookup key.** This is the right
  call — every FK (`ChatMessage.sender`, `RoomMember.member`) references the simple UUID exactly like before OAuth2,
  and only the login path needs to know about provider identity. (An earlier iteration made `provider_id` itself the
  PK with no provider scoping — that risked two different providers' ids colliding into one account. Current design
  fixes that; don't regress it.)
- **`AuthUser` is a custom `OAuth2User` record, not the default `DefaultOAuth2User`.** Carries clean primitives
  (`userId`, `provider`, `providerId`, `name`) instead of the raw non-serializable attributes map — needed because
  the session is now JDBC-persisted (`spring-session-jdbc`), and the default attribute map doesn't serialize cleanly.
- **Session-cookie auth, not JWT/resource-server.** `oauth2Login()` is the right choice for a same-origin browser SPA
  — the session cookie backs both REST calls and the STOMP handshake for free. No `oauth2-resource-server` starter
  needed (and it isn't in `build.gradle` anymore — good, it was removed).
- **CSRF is on, cookie-based, and the frontend actually participates.** `api.js`'s `apiFetch` reads the `XSRF-TOKEN`
  cookie and attaches `X-XSRF-TOKEN` on non-GET requests. Don't "fix" a CSRF 403 by disabling CSRF — the plumbing to
  do it right is already there.
- **Identity resolution moved from localStorage to a server round-trip.** `app.js` no longer trusts any locally
  cached user id; `initializeApp()` always calls `GET /v1/users/me` first and branches on the response. This is a
  real improvement over the old honor-system localStorage model — keep it this way.
- **Sender identity is now resolved server-side on the STOMP path too.** This was the last piece of the old
  client-asserted-identity model and it's now fixed — `MessageWSController` pulls the sender off the authenticated
  `Principal`, not the message body. Don't regress this by adding a `senderId` back to `InboundMessageDTO`.
- **Room `name` IS unique; user identity uniqueness is `(provider, providerId)`, not name.** Room uniqueness is
  required because STOMP topics route by room name (`/topic/chat/{room}`) and `RoomRepository` looks rooms up by
  name.
- **Mapping lives in the service/entity `toDTO()`, not a separate mapper.** Entities have a `toDTO()` method (a
  deviation from the earlier no-`toDTO()`-on-entities rule, now the actual pattern in use — `User.toDTO()`,
  `Room.toDTO()` both exist. Follow this pattern for new entities rather than the old one.)
- **Persist-then-broadcast.** `MessageService.save` writes the row, then the handler's return value is broadcast via
  `@SendTo`. A DB failure means no broadcast (usually desired), but see "silent send failure" in Known gaps.
- **History loads on room entry, then subscribe.** Frontend GETs history, renders it, and only *then* subscribes to
  the live topic, so live messages append in order. Small message-loss window exists between the history query and
  the subscription.
- **Timestamps:** backend sends `LocalDateTime` as ISO-8601 strings; frontend formats them to `HH:MM:SS` via
  `new Date(iso)` (`formatSentAt` in `utils.js`).

---

## Gotchas learned the hard way (do not re-introduce)

- **`sub` is an OIDC-only claim; don't assume it exists for every provider.** GitHub isn't OIDC and has no `sub` —
  see Auth model above. Any future provider-agnostic code needs to branch on `registrationId`, not assume a common
  claim shape.
- **`@MapsId` requires the mapped-id field's type to exactly match the referenced entity's `@Id` type.**
  `RoomMemberId.userId` is `UUID` and `User`'s `@Id` is (currently) `UUID` — consistent now, but this broke
  transiently during the OAuth2 migration when `User`'s PK was briefly a `String`. If `User`'s PK type ever changes
  again, `RoomMemberId` has to change with it or `RoomMember`'s metadata fails to build at Hibernate bootstrap
  (whether or not `RoomMember` is actually queried anywhere).
- **Reserved SQL words.** `user` and `from` are reserved in Postgres. The table is `Users` (not `user`); the message
  sender column is `sender_id` (never `from`). Naming a table/column with a reserved word yields
  `syntax error at or near "..."`.
- **`ddl-auto=update` is additive only.** It ADDS columns/constraints but never DROPS them. Removing `unique=true`
  from an entity does NOT remove the existing DB constraint — you must `ALTER TABLE ... DROP CONSTRAINT` by hand.
  This bit the project before (leftover unique constraints on `user.name` and on `messages.room_id`). Now that
  `ddl-auto` is back to `update` (not `create`), this risk is live again — a schema wipe won't happen for you anymore
  between iterations.
- **`@ManyToOne` join columns must not be `unique`.** A `unique=true` on `room_id` turned "many messages per room"
  into "one message per room" and caused duplicate-key violations on the 2nd message. Same risk on `sender_id`.
- **Derived query property paths.** To filter by room name use `findByRoomNameOrderBySentAtAsc(String)` —
  `RoomName` traverses `room.name` and takes a `String`. `findByRoom...(String)` tries to match the whole `Room`
  entity against a string and throws `not assignable to ...Room`.
- **REST needs `@RestController`, not `@Controller`.** A `@GetMapping` on a plain `@Controller` treats the return
  value as a view name and 404s to static-resource handling. Keep STOMP (`@MessageMapping`) and HTTP endpoints in
  separate beans (`MessageWSController` vs `MessageRestController` — this split is already correct, keep it).
- **`@DestinationVariable` name must match the template**, or bind explicitly:
  `@DestinationVariable("room") String roomName`. Prefer the explicit form — it survives compilation without
  `-parameters`.
- **Frontend module boot order.** `app.js` is now `type="module"`, loaded after the SockJS/stomp.js CDN `<script>`
  tags in `index.html`. All DOM-touching code still needs `cacheEls()` to have run first inside `initializeApp()`
  (unchanged rule from the IIFE days, just now inside a module instead of a top-level function).
- **`UUID.equals(Object)` silently returns `false` for any non-`UUID` argument — it's not a compile error, so the
  bug hides in plain sight.** This is exactly what broke `PATCH /v1/users/{userId}` (see Known gaps #1): comparing
  a `UUID` path variable against a `String` provider id compiles fine and just always evaluates to "not equal."
  Any future ownership/self check must compare same-typed identifiers — `authUser.getUserId()` (`UUID`) against the
  path/body `UUID`, never against `getProviderId()` (`String`).
- **Narrowing an OAuth2 `scope` list can silently change which claims come back, even if the login still
  "succeeds."** Dropping `openid` from Google's scope (see Auth model) doesn't necessarily break the redirect/login
  UX — it can just make the OIDC-only `sub` claim disappear from the userinfo response, which only shows up later
  as a `provider_id NOT NULL` failure when provisioning the user. If you ever change a registration's `scope`,
  re-test the full login → provisioning path live, not just "does it redirect and come back."

---

## Frontend specifics

- **No more localStorage-based identity gate.** The old `arichat.name`/`arichat.userId` keys and the "check
  localStorage, else show name screen" boot logic are gone. `initializeApp()` (`app.js`) always calls
  `GET /v1/users/me` first; `state.user` (in `state.js`) holds the `UserDTO` for the session, nothing is cached
  client-side across reloads anymore.
- **Module structure:** `config.js` (API base URLs, STOMP destinations — `STORE` constants for the old localStorage
  keys are still defined here but appear unused now, verify before deleting), `state.js` (shared mutable `state`/`el`
  objects), `api.js` (`apiFetch` with CSRF-cookie handling, `normalizeRooms`), `ws.js` (STOMP connect/send/subscribe),
  `ui.js` (DOM rendering), `utils.js` (formatting helpers), `app.js` (screen flow + event wiring, the entry point).
- **`ui.js`'s `renderMessage` still reads `state.userId`/`state.username`**, which don't exist on `state` (state has
  `state.user.id` etc, per `state.js`) — **still unfixed, confirmed unchanged this pass** (no frontend file has been
  touched since the last review; `git status` shows the static assets untracked-clean). "Mine" message detection is
  currently always falling through to the `msg.from === state.username` branch, which compares against `undefined`
  and will never match — every message, including your own, is rendered as "not mine" right now. Worth confirming
  in a live browser, but the code reads unambiguously broken.
- **Display name flow — currently a dead end.** `screen-login` → (if `displayName` is null) `screen-name`, which
  does `PATCH /v1/users/{state.user.id}` → `screen-rooms`. That PATCH now always 403s (see Known gaps #1), so any
  first-time user who reaches `screen-name` cannot get past it — `submitName()`'s error path fires every time. The
  old `POST /v1/users` create-a-user flow is gone entirely; `CustomOAuth2UserService` provisions the row
  server-side on first login, which still works — it's only the *display name* step that's stuck.
- **`ws.js`'s `sendMessage` sends `{content}` only now** — matches the backend's `InboundMessageDTO`. Already
  updated, no action needed.
- **`ws.js` already subscribes to `/user/queue/errors`** on connect and renders whatever `ErrorDTO.error` says as a
  notice — the frontend side of the new error-feedback path is already wired up and doesn't need touching.

---

## Known gaps (NOT production-ready)

Ranked by how much they'd hurt right now, not just eventually. Items resolved since the last pass are called out
so nobody re-does finished work.

1. **`PATCH /v1/users/{userId}` always returns 403 — breaks onboarding for every new user.** See HTTP contract
   above for the exact bug (`UUID.equals(String)`). This is the single most urgent item: it's not a latent security
   gap, it's a broken core flow that currently prevents any first-time Google login from ever reaching the rooms
   screen. One-line fix: compare against `authUser.getUserId()`, not `authUser.getProviderId()`.
2. **GitHub OAuth2 is broken (see Auth model above), not just unimplemented.** `CustomOAuth2UserService` hardcodes
   OIDC claim names (`sub`, `given_name`, `family_name`) that don't exist in GitHub's attribute map. A GitHub login
   attempt will fail to provision a `User` row (`provider_id` NOT NULL violation) or throw during attribute
   extraction. Needs `registrationId`-based branching before it's usable at all. Unchanged since last review.
3. **Google's narrowed OAuth scope needs a live re-test.** See the "New risk" note under Auth model — `openid` was
   dropped from the requested scope while `CustomOAuth2UserService` still depends on the OIDC-only `sub` claim.
   Unverified either way without running a real login; flagging so it gets checked rather than assumed fine.
4. **`ui.js`'s "mine" message detection is broken.** Reads `state.userId`/`state.username`, which don't exist on
   `state` anymore (see Frontend specifics). Every message currently renders as not-yours. This predates the
   security-layers work and hasn't been touched.
5. **`GET /v1/users` and `GET /v1/users/{id}` expose every user's email address to any authenticated caller, with
   no filtering or pagination.** Reframed from the old gap: the impersonation angle that made this dangerous is
   closed now (chat identity resolves server-side, not from a client-suppliable UUID), but this is still a
   straightforward PII leak on its own — any logged-in user can enumerate everyone else's email.
6. **No rate limiting yet on WebSocket message sends.** Still nothing in `MessageWSController`/`MessageService`/
   `WebSocketConfig` that throttles frame rate. **This is the current active work item** — see Suggested next
   steps. The prerequisite work (trustworthy per-user identity via `Principal`, and a feedback channel via
   `/user/queue/errors`) is now done, so this can be built on solid ground.
7. **`SimpleUrlAuthenticationSuccessHandler` is hardcoded to `"http://localhost:8080"`.** Breaks the moment this is
   deployed anywhere else. Needs to come from config (a property) rather than a string literal in `SecurityConfig`.
8. **HTTP + hardcoded localhost.** Must be deployed with HTTPS/WSS and a real host before anyone off-machine can use
   it. Plaintext otherwise. (OAuth2 redirect URIs will also need updating for a real domain — Google/GitHub app
   configs are currently registered for `localhost:8080` callbacks only.)
9. **No backups.** Add at least a nightly `pg_dump`.
10. **No server-side validation on message/room content.** Frontend caps message length at 1000 chars and sanitizes
    room names, but the backend accepts anything for either. The frontend is not a security boundary.
11. **Operational blind spots.** No health check, no monitoring, `open-in-view` is on.

**Resolved since the last pass — do not redo:**
- ~~Root `.env` not gitignored~~ — fixed, `.gitignore` now has a root-level `.env` entry.
- ~~`InboundMessageDTO.senderId` client-supplied and trusted~~ — fixed, sender now resolved from the STOMP
  `Principal` server-side.
- ~~Silent send failure~~ — fixed, `@MessageExceptionHandler` now routes failures to `/user/queue/errors`, and the
  frontend already renders them.
- ~~`WebSocketConfig` wildcard origin~~ — fixed, STOMP endpoint now locked to `http://localhost:8080` only.

---

## Suggested next steps (roughly ordered)

**Immediate, before anything else:** fix the `PATCH /v1/users/{userId}` bug (gap #1). It's one line
(`authUser.getUserId()` instead of `authUser.getProviderId()`), and until it's fixed, no new user can complete
onboarding — every other item below is moot for anyone who isn't already sitting in the DB with a `displayName`
set. Re-test the Google login flow live at the same time, both to confirm the PATCH fix and to settle the scope
question (gap #3).

**Current work in progress (per project owner, 2026-08-13):** rate limiting for WebSocket message sends, to prevent
one client from overloading the server (gap #6). Now that sender identity is resolved from the authenticated
`Principal` (no longer client-supplied) and there's a private error queue to report rejections on, both
prerequisites this depended on are done. Things to weigh when implementing:
- **Where to enforce it:** a `ChannelInterceptor` on the STOMP inbound channel (`configureClientInboundChannel` in
  `WebSocketConfig`) is the natural fit — it sees every frame before it reaches `MessageWSController`, and can reject
  by throwing (or silently dropping) without touching the controller/service at all.
- **What key to rate-limit on:** per authenticated user, via the same `Principal` → `AuthUser` → `getUserId()` path
  `MessageWSController.sendToRoom` already uses. Don't rate-limit by session or IP; the trustworthy per-user
  identity is already there for the taking.
- **Feedback on rejection:** reuse `/user/queue/errors` (`ErrorDTO`) — it already exists and the frontend already
  subscribes to it. A rate-limited message should tell the sender it was dropped, not just vanish.
- **Scope of the limiter:** in-memory (e.g. a simple token bucket per user id in a `ConcurrentHashMap`) is fine at
  this project's scale — no need for Redis/distributed rate limiting for a single-instance learning app.

After that, roughly in order:
1. Fix GitHub OAuth2 attribute extraction (gap #2) — the other provider shouldn't stay broken indefinitely.
2. Fix `ui.js`'s stale `state.userId`/`state.username` read (gap #4) — quick, and "every message looks like it's
   not yours" is a bad first impression for anyone testing the app right now.
3. Narrow `GET /v1/users`/`GET /v1/users/{id}` to not leak email to arbitrary authenticated callers (gap #5) — a
   lighter list DTO without `email`, or scope it down to room-mates only.
4. Server-side content validation (non-empty, max length) on inbound messages and room names (gap #10).
5. Move the OAuth2 success-handler URL and redirect URIs to config, ahead of any real deployment (gap #7).
6. Flyway migrations; flip `ddl-auto` to `validate`.
7. Deploy target: managed Postgres (gets you TLS + backups) behind HTTPS/WSS.
8. Optional/learning: virtual-thread executor on the STOMP inbound channel for cheaper blocking-JDBC concurrency —
   note this can reorder within-room messages; consider `setPreservePublishOrder(true)`. Only meaningful at
   hundreds of concurrent handlers, which this is nowhere near.

---

## How to work with the project owner

These are standing instructions from the owner — follow them:

- **Be direct. No cheerleading.** No "great question", no praise padding. If an idea is bad, say so and explain why.
- **Challenge assumptions and point out problems** before implementing. Ask hard questions. If something won't work,
  say so directly.
- **Verify before claiming success.** Don't assert something works without evidence; test assertions where possible.
  If a claim can't be verified, state exactly why. Show failures and errors honestly rather than papering over them.
- **Keep the project directory clean.** Do NOT create `test-*` or `debug-*` files unless explicitly asked.
- Minimal-formatting prose is preferred for explanations; don't over-format.
- **Frontend-only changes unless explicitly told otherwise (standing, as of 2026-08-12).** Only touch
  `src/main/resources/static/*` (`index.html`, `app.js` and the other JS modules, `styles.css`) and other pure-frontend
  assets. Do not modify backend Java code, `application.properties`, `build.gradle`, `SecurityConfig`, or any other
  backend/config file — even if a task appears to need it — without the owner explicitly asking for backend changes.
  If a frontend task is blocked by something only fixable on the backend, say so and stop; do not go fix it yourself.
  (Documentation updates to this file itself, when the owner explicitly asks for them, are not covered by this
  restriction.)
