# CLAUDE.md — ChattyChat

Context for continuing this project in Claude Code. Rewritten 2026-08-13 from a full read of the current tree
(backend + frontend). **Where it says "verify", check the actual code before trusting it** — this file drifts from
the code the moment someone edits without updating it.

---

## What this is

A small real-time chat app: Spring Boot backend + a vanilla-JS single-page frontend (branded "AriChat" in the UI).
Users log in with Google or GitHub, see/create rooms, and exchange messages in a room over WebSocket/STOMP. Messages
are persisted to Postgres and loaded as history when a user enters a room.

Purpose is **learning**, with a stretch goal of possibly running it as a small friends-and-family tool. It is **not
production-ready** (see "Known gaps").

Auth status: **Google OAuth2 login works end-to-end** (session created, `User` row provisioned, CSRF cookie issued).
**GitHub OAuth2 is broken, not just unfinished** — see "Known gaps" #1, it's a concrete bug, not a TODO.

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
- Messaging: STOMP over SockJS, in-memory `SimpleBroker`.
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
    - `UserController` — REST, `/v1/users` (list, get-by-id, `/me`, PATCH display name)
    - `RoomController` — REST, `/v1/rooms` (list, create)
    - `MessageWSController` — STOMP `@Controller`, `@MessageMapping` only
    - `MessageRestController` — REST `@RestController`, `/v1/rooms/{room}/messages` history endpoint
- `com.chattychat.Services` — `UserService`, `RoomService`, `MessageService`, `CustomOAuth2UserService`
- `com.chattychat.Repositories` — `UserRepository`, `RoomRepository`, `MessageRepository`
- `com.chattychat.Entities` — `User`, `Room`, `ChatMessage`, `RoomMember` + `RoomMemberId` (composite-key embeddable;
  **`RoomMember` is currently unused** — no repository, service, or controller references it. Looks like scaffolding
  for a future "room membership" feature; safe to ignore until something actually wires it up)
- `com.chattychat.dto` — `InboundMessageDTO`, `OutboundMessageDTO`, `UserDTO`, `RoomDTO`, `UpdateNameRequestDTO`,
  `AuthUser` (the OAuth2 principal type, see Auth section)

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
"complete."

**Chat/STOMP is still on the old trust model.** `InboundMessageDTO` still carries a client-supplied `senderId`
(now typed `UUID` instead of the old raw string) that `MessageService.save()` trusts via `findById`. None of the
OAuth2 work above has reached `MessageWSController` yet — the STOMP session *does* carry the authenticated
`Principal` (Spring's WebSocket support propagates it automatically from the HTTP handshake), but nothing reads it.
See Known gaps #3 — this is the actual security-relevant gap left over from the OAuth2 work.

---

## HTTP + messaging contract

REST:

- `GET  /v1/users` → all users (no auth check visible beyond the blanket `/v1/**` rule — mostly a debug endpoint).
- `GET  /v1/users/{userId}` → `UserDTO` by surrogate UUID.
- `GET  /v1/users/me` → current session's `UserDTO`, 401 if not authenticated or if the `AuthUser` can't be
  re-resolved to a `User` row.
- `PATCH /v1/users/{userId}` body `{ "displayName": "..." }` → updates and returns the `UserDTO`. **Not currently
  scoped to "your own user"** — any authenticated caller can PATCH any `userId` they can guess (see Known gaps).
- `GET  /v1/rooms` → array of rooms (`{id,name}` or `["name"]`; frontend normalizes).
- `POST /v1/rooms` body `{ "name": "..." }` → created room.
- `GET  /v1/rooms/{room}/messages` → `OutboundMessageDTO[]`, oldest-first (history).

STOMP:

- Connect endpoint: `/ws` (SockJS).
- App prefix `/app`; send destination `/app/chat.send/{room}`.
- Topic (broadcast): `/topic/chat/{room}`.
- Inbound payload from client: `{ senderId: <UUID>, content: "..." }` — **still client-supplied, see Auth model above.**
- `OutboundMessageDTO` carries: `id`, `senderId`, `from` (sender's `firstName`, not `displayName` — see gotcha),
  `room` (name), `content`, `sentAt`.

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
- **Send `senderId` (UUID), not the display name — but this decision now needs revisiting.** It made sense when
  identity was entirely client-asserted. Now that the server can resolve identity from the session, the STOMP path
  keeping a client-supplied `senderId` is the one place the old trust model survived the OAuth2 migration. See
  Known gaps #3.
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
- **`ui.js`'s `renderMessage` still reads `state.userId`/`state.username`**, which no longer exist on `state` (state
  now has `state.user.id` etc, per `state.js`). This looks like a leftover from before the `state.user` refactor —
  "mine" message detection is likely broken or silently falling through to the name-comparison fallback. Worth
  checking directly in a browser before relying on it.
- **Display name flow:** `screen-login` → (if `displayName` is null) `screen-name`, which does `PATCH
  /v1/users/{state.user.id}` → `screen-rooms`. The old `POST /v1/users` create-a-user flow is gone entirely; there's
  no more "create user" REST call from the frontend, since `CustomOAuth2UserService` provisions the row server-side
  on first login.

---

## Known gaps (NOT production-ready)

Ranked by how much they'd hurt right now, not just eventually:

1. **GitHub OAuth2 is broken (see Auth model above), not just unimplemented.** `CustomOAuth2UserService` hardcodes
   OIDC claim names (`sub`, `given_name`, `family_name`) that don't exist in GitHub's attribute map. A GitHub login
   attempt will fail to provision a `User` row (`provider_id` NOT NULL violation) or throw during attribute
   extraction. Needs `registrationId`-based branching before it's usable at all.
2. **Root `.env` is not gitignored.** `.gitignore` only excludes `src/main/resources/.env`, but
   `application.properties` imports `spring.config.import=file:.env[.properties]`, which resolves relative to the
   process working directory — i.e. the project root when run via Gradle — not `src/main/resources/`. The actual
   `.env` in use (containing `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_SECRET`, DB credentials) sits at
   `C:\Projects\ChattyChat\.env` and is currently untracked-but-not-ignored: a `git add -A` or `git add .` would
   stage it. Fix: add a root-level `.env` entry (or `/.env`) to `.gitignore`. This is a live secret-leak risk, not a
   theoretical one — flag it before any bulk `git add`.
3. **`InboundMessageDTO.senderId` is still client-supplied and trusted.** The one piece of the old honor-system
   identity model that the OAuth2 migration hasn't reached yet. `MessageWSController` has access to the
   authenticated STOMP `Principal` (Spring wires it through automatically from the HTTP handshake) but doesn't use
   it — `MessageService.save()` still does `userRepository.findById(incoming.senderId())` on whatever UUID the
   client puts in the payload. A logged-in user can currently send messages as any other user whose UUID they can
   obtain (e.g. from `GET /v1/users`, which lists everyone with no filtering).
4. **`PATCH /v1/users/{userId}` isn't scoped to "yourself."** Any authenticated session can rename any user by UUID,
   not just their own. Should resolve the target user from the authenticated principal, not the path variable (or at
   minimum check `userId` matches the caller).
5. **`SimpleUrlAuthenticationSuccessHandler` is hardcoded to `"http://localhost:8080"`.** Breaks the moment this is
   deployed anywhere else. Needs to come from config (a property) rather than a string literal in `SecurityConfig`.
6. **HTTP + hardcoded localhost.** Must be deployed with HTTPS/WSS and a real host before anyone off-machine can use
   it. Plaintext otherwise. (OAuth2 redirect URIs will also need updating for a real domain — Google/GitHub app
   configs are currently registered for `localhost:8080` callbacks only.)
7. **No backups.** Add at least a nightly `pg_dump`.
8. **Silent send failure.** No `@MessageExceptionHandler` — a failed persist drops the message with zero feedback to
   the sender. Add one that sends an error frame back.
9. **No server-side validation or rate limiting on message sends.** Frontend caps length at 1000 chars, but the
   backend accepts anything, and nothing limits how fast a connected client can send. **This is the current active
   work item — see Suggested next steps.**
10. **`GET /v1/users` returns everyone with no pagination or filtering.** Minor now, but also the thing that makes
    gap #3 easy to exploit (it hands out every other user's UUID for free).
11. **Operational blind spots.** No health check, no monitoring, `open-in-view` is on.

---

## Suggested next steps (roughly ordered)

**Current work in progress (per project owner, 2026-08-13):** rate limiting for WebSocket message sends, to prevent
one client from overloading the server. Nothing in `MessageWSController`/`MessageService`/`WebSocketConfig` does any
throttling today — every `@MessageMapping`-handled frame is persisted and broadcast with no limit. Things to weigh
when implementing this:
- **Where to enforce it:** a `ChannelInterceptor` on the STOMP inbound channel (`configureClientInboundChannel` in
  `WebSocketConfig`) is the natural fit — it sees every frame before it reaches `MessageWSController`, and can reject
  by throwing (or silently dropping) without touching the controller/service at all.
- **What key to rate-limit on:** per authenticated user (via the STOMP `Principal`, same one gap #3 above needs
  wired up) is more correct than per-session or per-IP, and this is a good forcing function to finally read that
  `Principal` instead of trusting `senderId` from the payload — the rate limiter needs a trustworthy identity anyway.
- **Feedback on rejection:** ties into gap #8 (silent send failure) — a rate-limited message should tell the sender
  it was dropped (e.g. a `/user/queue/errors` frame), not just vanish, or debugging "why didn't my message send"
  becomes painful.
- **Scope of the limiter:** in-memory (e.g. a simple token bucket per user id in a `ConcurrentHashMap`) is fine at
  this project's scale — no need for Redis/distributed rate limiting for a single-instance learning app.

After that, roughly in order:
1. Fix GitHub OAuth2 attribute extraction (gap #1) — the other provider shouldn't stay broken indefinitely.
2. Fix the root `.env` gitignore gap (gap #2) — quick, and it's a live leak risk.
3. Wire `MessageWSController` to resolve `senderId` from the authenticated `Principal` instead of the DTO (gap #3) —
   naturally pairs with the rate-limiting work above since both need the same trustworthy identity source.
4. Scope `PATCH /v1/users/{userId}` to the caller's own id (gap #4).
5. `@MessageExceptionHandler` in `MessageWSController` → send failures (including future rate-limit rejections) back
   to the sender.
6. Server-side content validation (non-empty, max length) on inbound messages.
7. Move the OAuth2 success-handler URL and redirect URIs to config, ahead of any real deployment.
8. Flyway migrations; flip `ddl-auto` to `validate`.
9. Deploy target: managed Postgres (gets you TLS + backups) behind HTTPS/WSS.
10. Optional/learning: virtual-thread executor on the STOMP inbound channel for cheaper blocking-JDBC concurrency —
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
