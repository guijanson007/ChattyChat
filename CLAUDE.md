# CLAUDE.md — ChattyChat

Context for continuing this project in Claude Code. Rewritten 2026-08-19 from a full read of the current tree, after
the testing/CI/exception-handling/schema.sql work landed and after the room-membership & invites feature's frontend
was built (backend for that feature is not started — see its own section below). **Where it says "verify", check
the actual code before trusting it** — this file drifts from the code the moment someone edits without updating it.
Re-read the *whole* tree on every pass, not just the files that seem relevant — this file has been wrong before from
partial re-reads.

---

## What this is

A small real-time chat app: Spring Boot backend + a vanilla-JS single-page frontend (branded "AriChat" in the UI).
Users log in with Google or GitHub, see/create rooms, and exchange messages in a room over WebSocket/STOMP. Messages
are persisted to Postgres and loaded as history when a user enters a room.

Purpose is **learning**, doubling as a **portfolio project meant to demonstrate backend infrastructure knowledge**
(explicit goal as of 2026-08-19) — treat "does this look production-grade" as a real evaluation axis, not just
"does it work." It is **not production-ready yet** (see "Known gaps").

Auth status: **Google OAuth2 works end-to-end**, including onboarding (the old PATCH-display-name 403 bug is fixed).
**GitHub OAuth2 is still broken** — see Known gaps #1, unchanged across many review passes now.

A **room-membership & invites feature is in progress**: rooms will be either open (self-joinable) or invite-only,
and users will only see rooms they belong to. The frontend for this is fully built; the backend is entirely
unstarted. See "Room membership & invites (in progress)" below before touching either side of it.

---

## Stack

- Java 21, Spring Boot (Spring Framework 7.x, Spring Data JPA 4.x, Hibernate 7.4).
- PostgreSQL (via HikariCP). Driver `postgresql-42.7.x`.
- Auth: Spring Security `oauth2Login`, session-cookie based (not resource-server/JWT). Sessions are stored in
  Postgres via `spring-session-jdbc` (`spring.session.store-type=jdbc`), not in-memory — survives app restarts.
  `@EnableMethodSecurity` is on (`ChattyChatApplication.java`), so `@PreAuthorize` is genuinely enforced, not
  decorative — verified by reading the annotation and by the integration test that exercises it end-to-end.
- CSRF: enabled (not disabled), cookie-based token (`CookieCsrfTokenRepository.withHttpOnlyFalse()` +
  `CsrfTokenRequestAttributeHandler`) so the frontend JS can read the `XSRF-TOKEN` cookie and echo it back as
  `X-XSRF-TOKEN` on state-changing requests.
- Messaging: STOMP over SockJS, in-memory `SimpleBroker`. STOMP endpoint origins are locked to
  `http://localhost:8080` and the prod domain (`WebSocketConfig`).
- **Schema management moved off Hibernate `ddl-auto` entirely.** `application.properties` no longer sets
  `spring.jpa.hibernate.ddl-auto` at all. Instead: `src/main/resources/schema.sql` + `spring.sql.init.mode=always`
  is the schema source of truth, run on every boot. It's idempotent for creation (`CREATE TABLE IF NOT EXISTS`) and
  defines real `ON DELETE CASCADE` on `messages.sender_id`/`messages.room_id` and on `room_members` — this is what
  fixed the old "deleting a user with message history throws a raw FK violation" bug. **It is not a real migration
  system** — no versioning, no `ALTER TABLE` support for evolving an existing database. Flyway/Liquibase is still
  the natural next step; this is real progress toward that, not a replacement for it.
- **Global REST exception handling exists now.** `com.chattychat.Exception` package: custom unchecked exceptions
  (`InvalidUserException`, `InvalidRoomException`, `InvalidMessageException`, `AuthenticationException`), each with
  its own `@ControllerAdvice` handler class mapping to a clean HTTP status. This fixed real bugs — see "Key design
  decisions" and "Gotchas."
- **Real test coverage exists now**, a first for this project: JUnit 5 + Mockito + AssertJ unit tests for all four
  controllers (`UserUnitTest`, `RoomUnitTest`, `MessageRestUnitTest`, `MessageWSUnitTest`), plus a genuine
  Testcontainers-backed integration test (`UserIntegrationIT`) that spins up real Postgres and exercises the actual
  `@PreAuthorize` path through `MockMvc` with Spring Security test support
  (`oauth2Login().oauth2User(...)`) — this is what actually proves method security is wired up, not just present.
- **CI exists now**: `.github/workflows/workflow.yaml` runs `./gradlew build` on push/PR to `master`, with a real
  Postgres service container and dummy OAuth2 env vars so the Spring context loads in CI.
- **API docs exist now**: springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`), `/api-docs` and
  `/swagger-ui.html`. Controllers are annotated with `@Tag`/`@Operation`/`@ApiResponses`. Worth knowing: the docs
  are only as accurate as whoever wrote the annotations — verify a documented response code against the actual
  handler before trusting it (this bit a previous pass: two endpoints promised a 404 the code didn't deliver, now
  fixed, see Known gaps history).
- Debug logging (`spring.jpa.show-sql`, Spring Security `DEBUG`) is commented out by default in
  `application.properties` — good, don't re-enable it in what ships.
- Frontend: plain JS, ES modules (`config.js`, `state.js`, `api.js`, `ws.js`, `ui.js`, `utils.js`, `app.js` entry
  point), no build step. SockJS + stomp.js from CDN. `API_BASE` is `window.location.origin` (same-origin,
  works on both localhost and the real domain).

---

## Package / layout (read from current tree)

Backend package root `com.chattychat`, layered controller → service → repository:

- `com.chattychat.Config`
    - `SecurityConfig` — CSRF, authorization rules, `oauth2Login` wiring, success handler
    - `WebSocketConfig` — STOMP/SockJS registration
- `com.chattychat.Controller`
    - `UserController` — REST, `/v1/users` (list, get-by-id, `/me`, PATCH display name, DELETE). PATCH/DELETE use
      `@PreAuthorize("#authUser.userId() == #userId")` for self-scoping — this is the fix for the old 403 bug.
    - `RoomController` — REST, `/v1/rooms` (list, create). Room creation no longer trusts a client-supplied id.
    - `MessageWSController` — STOMP `@Controller`. `@MessageMapping` resolves sender from the STOMP `Principal`;
      `@MessageExceptionHandler` routes any failure to the sender's private `/user/queue/errors` queue.
    - `MessageRestController` — REST, `/v1/rooms/{room}/messages` history endpoint.
- `com.chattychat.Services` — `UserService`, `RoomService`, `MessageService`, `CustomOAuth2UserService`
- `com.chattychat.Repositories` — `UserRepository`, `RoomRepository`, `MessageRepository`. **No `RoomMemberRepository`
  yet** — see the membership feature section below, this is the actual current blocker for that work.
- `com.chattychat.Entities` — `User`, `Room`, `ChatMessage`, `RoomMember` + `RoomMemberId` (composite-key
  embeddable). `RoomMember` now has a real constructor (`RoomMember(User, Room)`) and the `room_members` table in
  `schema.sql` has proper `ON DELETE CASCADE` — more scaffolding than before, but **still fully unused**: no
  repository, no service, no controller ever creates or reads one. This is the entity the in-progress membership
  feature will finally wire up.
- `com.chattychat.Exception` — new package. `InvalidUserException`, `InvalidRoomException`, `InvalidMessageException`
  (defined with a registered handler but **never thrown anywhere** — dead scaffolding, presumably meant for message
  content validation that isn't implemented yet), `AuthenticationException` (⚠️ shadows
  `org.springframework.security.core.AuthenticationException`'s simple name — see Gotchas, rename before it causes
  a wrong-import bug). One `@ControllerAdvice` handler class per exception type
  (`UserExceptionHandler`, `RoomExceptionHandler`, `MessageExceptionHandler`, `AuthenticationExceptionHandler`).
- `com.chattychat.dto` — `InboundMessageDTO` (`{content}` only), `OutboundMessageDTO` (`id`, `senderId`, `from`,
  `content`, `createdAt` — no `room` field, `sentAt` was renamed to `createdAt` in the DB-revamp pass), `UserDTO`,
  `RoomDTO`, `UpdateNameRequestDTO`, `ErrorDTO` (`{error}`, payload for `/user/queue/errors`), `AuthUser` (the
  OAuth2 principal type)

Note the package names are capitalized (`Controller`, `Services`, `Entities`, `Config`, `Exception`) — unusual for
Java convention (normally lowercase), but that's the existing choice; stay consistent.

---

## Data model (read from `schema.sql` + entities)

`schema.sql` is now the actual source of truth (see Stack). Tables, all lowercase now:

- `users`: `id` (UUID PK, `gen_random_uuid()`), `provider_id`, `provider`, `first_name`, `last_name`, `email`
  (nullable), `display_name` (nullable). `UNIQUE (provider_id, provider)` — the real identity anchor; the surrogate
  `id` is what every other table's FK points at.
- `rooms`: `id` (UUID), `name`, `created_at`, `last_updated_at` (both stamped via `@PrePersist`/`@PreUpdate` on
  `Room` — don't set them manually from a service).
- `messages`: `id` (UUID), `sender_id` (FK → `users`, `ON DELETE CASCADE`), `room_id` (FK → `rooms`,
  `ON DELETE CASCADE`), `content`, `created_at` (renamed from `sent_at`).
- `room_members`: `user_id` (FK → `users`, `ON DELETE CASCADE`), `room_id` (FK → `rooms`, `ON DELETE CASCADE`),
  `joined_at`, `PRIMARY KEY (user_id, room_id)`. **Table exists and is schema-correct; nothing in the Java code
  writes to it yet.**

`ChatMessage.sender`/`.room` are still plain `@ManyToOne` (EAGER by default).

---

## Auth model — how login actually works

**Flow (Google, the one that works):**
1. Frontend renders two plain `<a href>` buttons on `screen-login`: `/oauth2/authorization/google` and
   `/oauth2/authorization/github`. Real browser navigation, no JS.
2. Provider redirects back to `/login/oauth2/code/{google|github}?code=...&state=...` — the only endpoint that
   receives anything directly from the provider in the browser-facing sense (just an auth code).
3. Server-to-server: Spring exchanges the code for tokens, then calls
   `CustomOAuth2UserService.loadUser()` ([CustomOAuth2UserService.java](src/main/java/com/chattychat/Services/CustomOAuth2UserService.java)).
   `super.loadUser()` is now wrapped in a try/catch that rethrows failures as the custom `AuthenticationException`
   — but this only covers failures in the token/attribute-fetch step itself, see below for why that doesn't fix
   GitHub.
4. `SecurityConfig` wires this via `.oauth2Login(...).successHandler(new SimpleUrlAuthenticationSuccessHandler("/"))`
   — success redirects to `/` (relative now, fixed from the old hardcoded `localhost:8080` string).
5. Session (`spring-session-jdbc`) holds the `AuthUser` principal; browser gets a session cookie + `XSRF-TOKEN`.
6. Frontend's `initializeApp()` calls `GET /v1/users/me` on boot. 401 → `screen-login`. 200 with `displayName ==
   null` → `screen-name` (`PATCH /v1/users/{id}` — **this now works**, see below). 200 with a `displayName` →
   straight to rooms.

**Why GitHub is still broken — confirmed unchanged across many review passes.**
`CustomOAuth2UserService.loadUser()` still unconditionally reads `oAuth2User.getAttribute("sub")` for `providerId`,
and `"given_name"`/`"family_name"` for names. All OIDC-only claims — GitHub isn't OIDC and sends none of them. The
new `AuthenticationException` wrapper doesn't help: `getAttribute("sub")` doesn't throw for a missing claim, it just
returns `null`, which then hits `provider_id NOT NULL` at insert time — a `DataIntegrityViolationException` that
isn't any of the four types the new `@ControllerAdvice` handlers know about, so it still surfaces as a raw error.

What GitHub actually sends (from `GET https://api.github.com/user`, confirmed against GitHub's API): `id` (a JSON
**number**, not a string — `oAuth2User.getAttribute("id")` cannot be assigned directly to a `String` variable,
that's a runtime `ClassCastException`, not a compile error, so convert via `String.valueOf(...)`), `login` (always
present, good display-name fallback), `name` (nullable — unlike `login`), `avatar_url`, `email` (usually `null`
even with `user:email` scope granted; a reliable email needs a separate call to `GET /user/emails`). No `sub`, no
`given_name`/`family_name`, no ID token — GitHub is plain OAuth2, not OIDC.

**Google's OAuth scope is `profile,email`** (no `openid`) in `application.properties`. This was flagged several
passes ago as a risk since `CustomOAuth2UserService` depends on the OIDC-only `sub` claim, and Google's userinfo
endpoint typically requires `openid` to return it. **Still not confirmed either way with a live login** — nobody
has reported it broken, but nobody has explicitly re-tested it since the scope was narrowed either. Low-priority
now that PATCH/onboarding works for whoever's actually testing, but worth a real check before relying on it.

**Sender identity resolution and error feedback on the STOMP path are both fixed** (confirmed in earlier passes,
unchanged since): `InboundMessageDTO` is `{content}` only, `MessageWSController.sendToRoom` resolves the sender
from the authenticated `Principal`, and `@MessageExceptionHandler` routes failures to `/user/queue/errors`. Don't
regress either of these.

---

## HTTP + messaging contract

REST:

- `GET  /v1/users` → all users. Still no filtering/pagination — see Known gaps (email PII leak).
- `GET  /v1/users/{userId}` → `UserDTO`, now correctly 404s via `InvalidUserException` if missing (was a silent
  `200` with a null body in an earlier pass — fixed).
- `GET  /v1/users/me` → current session's `UserDTO`.
- `PATCH /v1/users/{userId}` body `{ "displayName": "..." }` → **works now.** `@PreAuthorize("#authUser.userId() ==
  #userId")` replaced the old manually-miscompared check. Covered by both unit and integration tests.
- `DELETE /v1/users/{userId}` → new endpoint, same `@PreAuthorize` self-scoping. Safe to call even for a user with
  message history now — `ON DELETE CASCADE` in `schema.sql` handles it (worth knowing: this means deleting your
  account **hard-deletes every message you ever sent**, including in rooms other people are still using — a real
  product decision, not obviously the right one for a group chat; revisit if this ever matters).
- `GET  /v1/rooms` → **currently still "all rooms."** This is the endpoint that needs to change to "rooms I'm a
  member of" once the membership feature lands — see that section.
- `POST /v1/rooms` body `{ "name": "..." }` → created room. Client-supplied `id` is now ignored (`Room(String
  name)` constructor) — old smell fixed.
- `GET  /v1/rooms/{room}/messages` → `OutboundMessageDTO[]`, oldest-first. Now correctly 404s via
  `InvalidRoomException` + `roomRepository.existsByName()` for an unknown room, distinct from "known room, empty
  history" (was a dead null-check that could never fire — fixed).

STOMP:

- Connect endpoint: `/ws` (SockJS, origins locked to localhost + prod domain).
- Send destination: `/app/chat.send/{room}`. Inbound payload: `{ content: "..." }` only.
- Broadcast topic: `/topic/chat/{room}`.
- Private queue: `/user/queue/errors` — `ErrorDTO { error }` on send failure.

Authorization in `SecurityConfig`: only `/v1/**` and `/ws/**` require authentication; everything else is
`permitAll()`. Still "public unless listed," unchanged posture from earlier reviews.

**Not yet enforced anywhere**: room membership. Any authenticated user can currently read history for and post
messages to any room by name, regardless of whether they're "registered" to it — the whole point of the in-progress
membership feature is moot until this is added alongside it.

---

## Key design decisions

- **Surrogate UUID PK on `User`, `(provider, providerId)` as the separate unique lookup key.** Unchanged, still the
  right call — don't regress it.
- **`@PreAuthorize` + `@EnableMethodSecurity` for self-scoping, not manual comparisons.** This is the pattern that
  replaced the buggy hand-rolled `UUID`/`String` comparison from an earlier pass. **Use this pattern for the
  upcoming room-membership authorization too** (e.g. "must be a member to read/post" checks) rather than reinventing
  manual checks in each controller method.
- **One custom unchecked exception + one `@ControllerAdvice` handler per domain**, living in `com.chattychat.Exception`.
  Consistent, and it's what fixed the dead-404-branch bugs from earlier reviews. Slightly verbose (a separate
  handler class per exception rather than one `@RestControllerAdvice` with several `@ExceptionHandler` methods) —
  a style choice, not a bug; follow the existing pattern for new domains (e.g. a future `InvalidInviteException`)
  rather than introducing a different shape.
- **`schema.sql` + `spring.sql.init.mode=always` instead of Hibernate `ddl-auto`.** Deliberate move away from
  implicit DDL. Keep evolving schema changes here, but know its limits — see Stack and Gotchas. Flyway/Liquibase is
  the natural graduation path once schema changes need to survive an already-populated database.
- **`AuthUser.getName()` always returns `userId.toString()`**, never a display name — required for
  `convertAndSendToUser` to route correctly by a value that's actually unique. Keep it this way.
- **Sender identity resolved server-side from the STOMP `Principal`**, never trusted from the message body. Don't
  regress by adding `senderId` back to `InboundMessageDTO`.
- **Mapping lives in entity `toDTO()` methods**, not a separate mapper layer. Follow this for new entities.
- **Persist-then-broadcast** for messages; a DB failure means no broadcast.
- **History loads over REST, then the client subscribes for live updates** — same pattern proposed for the
  membership feature's invites (REST for catch-up/source-of-truth, STOMP `/user/queue/*` for live push). Reuse this
  shape rather than inventing something new (e.g. webhooks) for future real-time features.

---

## Room membership & invites (in progress)

Decided 2026-08-19: **hybrid access model.** A room is either `isPublic` (discoverable, self-joinable by any
authenticated user) or invite-only (membership only via an existing member inviting you). Chosen over "fully open"
(status quo, doesn't satisfy "users only see rooms they're registered to") and "invite-only everywhere" (simpler,
but no public discovery at all).

**Frontend is fully built** (this pass), **backend is entirely unstarted.** Every endpoint below currently 404s;
the frontend degrades gracefully (empty panels, caught errors) rather than crashing, same pattern as the OAuth2
buttons before that backend existed.

**What's built, frontend side:**
- `index.html`: "Convites pendentes" panel and "Salas abertas para entrar" (discover) panel on `screen-rooms`; an
  "open room" checkbox on the create-room form; a "Convidar" button in the chat header; an invite modal
  (`#invite-modal`) with search + candidate list.
- `state.js`: `state.discoverRooms`, `state.invites`, `state.roomMembers`.
- `ui.js`: `renderDiscoverRooms`, `renderInvites`, `renderInviteUserList`, `openInviteModal`/`closeInviteModal`.
- `app.js`: `loadDiscoverRooms`/`handleRegisterRoom`, `loadInvites`/`handleAcceptInvite`/`handleDeclineInvite`,
  `openInvite`/`filterInviteCandidates`/`handleInviteUser`. Room creation now sends `isPublic` in the POST body.
- `ws.js`: subscribes to `/user/queue/invites` on connect, shows a live notice on push.

**Proposed backend contract** (design only — see chat history 2026-08-19 for the full reasoning):
- `Room.isPublic` (boolean), set at creation.
- `GET /v1/rooms` → repurpose to "rooms the authenticated user is a member of" (via a new
  `RoomMemberRepository.findByMember_Id`). **This is the actual mechanism that enforces "only see rooms you're
  registered to"** — it's not a separate visibility flag, it's what the room-list query reads from.
- `GET /v1/rooms/discover` → open rooms (`isPublic = true`) the caller hasn't joined yet.
- `POST /v1/rooms/{room}/members` → self-register. Only valid if the room is open. Frontend already calls this
  (`API.registerRoom`).
- `GET /v1/rooms/{room}/members` → list members (frontend already calls this to filter the invite picker).
- `POST /v1/rooms/{room}/invites` `{invitedUserId}` → only callable by existing members. Frontend already calls
  this (`API.roomInvites`).
- New `Invite`/`RoomInvite` entity + table (not yet in `schema.sql`) + `GET /v1/invites`,
  `POST /v1/invites/{id}/accept`, `POST /v1/invites/{id}/decline`. Frontend already calls all three
  (`API.invites`/`acceptInvite`/`declineInvite`).
- Real-time: `/user/queue/invites` STOMP destination via `convertAndSendToUser`, mirroring the existing
  `/user/queue/errors` mechanism. **Deliberately not webhooks** — a webhook needs the receiver to expose a public
  HTTP endpoint (server-to-server delivery model); a logged-in browser tab can't do that, but it already has an
  open WebSocket, which is strictly the right tool for "push an event to a specific logged-in user" here. REST
  (`GET /v1/invites`) stays authoritative for catching up on invites received while offline.
- **Known limitation to revisit, not solved now:** STOMP only connects once a user enters a room (`connect()` fires
  from `loadHistory()`), so a live invite push never reaches someone sitting on the room-list screen — that screen's
  invite panel relies on a REST refresh instead. Connecting to STOMP right after login instead of on room-entry
  would fix this properly; that's a connection-lifecycle change, out of scope for the membership feature itself.

**Backend work still needed, in dependency order:**
1. `RoomMemberRepository` — `findByMember_Id(UUID)`, `findByRoom_Id(UUID)`, `existsByMember_IdAndRoom_Id(UUID, UUID)`.
   The entity and `room_members` table already exist (see Package/layout) — this repository is the actual current
   blocker.
2. `RoomService.createRoom` needs `@AuthenticationPrincipal AuthUser` added, to auto-register the creator as a
   member on creation.
3. New `RoomController` endpoints for register/discover/list-members, per the contract above.
4. `Room.isPublic` column + entity field + `schema.sql` update.
5. New `Invite` entity, repository, service, controller, and `schema.sql` table.
6. **Authorization enforcement on the message read/send paths** — without this, membership is cosmetic. Anyone can
   still read/post to any room by name today regardless of membership.
7. `schema.sql`: idempotency check when adding the `invites` table — remember this file has no versioning, so an
   already-running database needs the new table added by hand, not just by editing the file (see Gotchas).

---

## Gotchas learned the hard way (do not re-introduce)

- **`sub` is an OIDC-only claim; GitHub's `id` is a JSON number, not a string.** See Auth model. Any
  provider-agnostic code must branch on `registrationId`, and must convert GitHub's numeric `id` via
  `String.valueOf(...)` rather than casting — a direct `String providerId = oAuth2User.getAttribute("id")` compiles
  fine and throws `ClassCastException` at runtime.
- **`UUID.equals(Object)` silently returns `false` for any non-`UUID` argument.** Not a compile error — this is
  exactly what caused the old PATCH-403 bug (comparing a `UUID` path variable against a `String` provider id). Now
  fixed via `@PreAuthorize`, but the lesson generalizes: always compare same-typed identifiers.
- **Don't name your own exception the same simple name as a framework class you depend on.**
  `com.chattychat.Exception.AuthenticationException` shadows `org.springframework.security.core.AuthenticationException`.
  Compiles fine (Java doesn't enforce `throws`-clause matching for unchecked exceptions), but it's a live
  readability trap in a Spring-Security-based codebase — rename it (e.g. `OAuth2ProvisioningException`) before it
  causes a wrong-import bug.
- **`schema.sql` + `spring.sql.init.mode=always` is not idempotent for schema *evolution*, only creation.**
  `CREATE TABLE IF NOT EXISTS` protects re-runs against a database that already has the tables, but adding a new
  table/column later means editing this file AND manually applying that change to any database that already ran an
  older version — the file has no version history and won't retroactively apply anything. Don't treat it as
  "migrations."
- **`@MapsId` requires the mapped-id field's type to exactly match the referenced entity's `@Id` type.**
  `RoomMemberId.userId`/`.roomId` are `UUID`, matching `User`/`Room`'s `@Id` — consistent now, but this broke
  transiently earlier when `User`'s PK was briefly a `String`. Keep them in sync if either PK type ever changes.
- **Reserved SQL words.** `user` and `from` are reserved in Postgres — table is `users`, sender column is
  `sender_id`, never `from`.
- **`@ManyToOne` join columns must not be `unique`.** Turns "many per parent" into "one per parent."
- **Derived query property paths traverse associations, not FK columns** — e.g.
  `findByRoomNameOrderByCreatedAtAsc` walks `ChatMessage.room.name`. Follow this convention for new repository
  methods (e.g. `RoomMemberRepository.findByMember_Id`, not something keyed off the raw FK column name).
- **REST needs `@RestController`, not `@Controller`.** Keep STOMP and HTTP endpoints in separate beans (already the
  pattern — keep it for any future controllers).
- **`@DestinationVariable` name must match the template**, or bind explicitly.
- **Frontend module boot order.** `app.js` is `type="module"`; all DOM-touching code needs `cacheEls()` to have run
  first inside `initializeApp()`.
- **A field rename on the wire (`sentAt` → `createdAt`) has to be applied everywhere it's read, not just where it's
  produced.** The DB-revamp pass updated `ws.js`, `app.js`, and `utils.js` together but missed `ui.js`'s
  `renderMessage`, which kept reading `msg.sentAt` — undefined for every message, so every timestamp literally
  rendered the text "undefined." Fixed this pass. When renaming a field that crosses the backend/frontend boundary,
  grep for every reader, not just the obvious call sites.

---

## Frontend specifics

- **No localStorage-based identity gate.** `initializeApp()` always calls `GET /v1/users/me` first;
  `state.user` holds the session's `UserDTO`, nothing cached client-side across reloads.
- **Module structure:** `config.js` (API endpoint builders, STOMP destinations — `STORE` constants for old
  localStorage keys are still defined but appear unused, verify before deleting), `state.js` (shared `state`/`el`),
  `api.js` (`apiFetch` with CSRF-cookie handling, `normalizeRooms`), `ws.js` (STOMP connect/send/subscribe +
  optimistic-send tracking), `ui.js` (DOM rendering, including the new membership/invite UI), `utils.js`
  (formatting helpers), `app.js` (screen flow + event wiring, entry point).
- **"Mine" message detection is fixed.** `ui.js`'s `renderMessage` correctly compares `msg.senderId ===
  state.user.id` now (was reading nonexistent `state.userId`/`state.username` in an earlier pass).
- **Optimistic send + failure UI, WhatsApp-style.** `sendMessage()` in `ws.js` renders the outgoing bubble
  immediately (dimmed, "enviando…"), confirms it in place when the broadcast echoes back (matched FIFO via
  `state.pending`, since the server doesn't echo a client-side correlation id — correct given messages are sent
  one at a time from a single composer, would need a real correlation id if that ever changes), and marks it failed
  with a tap-to-retry affordance if the server rejects it or the connection drops mid-flight.
- **Display name flow works end-to-end now.** `screen-login` → (if `displayName` is null) `screen-name` → `PATCH
  /v1/users/{state.user.id}` (now succeeds) → `screen-rooms`.

---

## Known gaps (NOT production-ready)

Ranked by how much they'd hurt right now. Fixed items are listed separately below so nobody re-does finished work.

1. **GitHub OAuth2 is broken — unchanged across many review passes now.** See Auth model for the exact claims
   GitHub actually sends and the type gotcha (`id` is numeric). If this isn't getting fixed soon, pull the GitHub
   button rather than leave a visibly broken login option live.
2. **Room membership has no backend at all yet.** See its own section above — this is the current focus.
3. **`com.chattychat.Exception.AuthenticationException` shadows Spring Security's own class of the same name.**
   Rename it — see Gotchas.
4. **`InvalidMessageException` is dead code** — defined with a registered handler, never thrown. Presumably meant
   for message-content validation (empty/oversized) that still isn't implemented.
5. **`MessageService.save()` didn't get the same exception-type upgrade as `history()` in the same class.** Still
   throws bare `IllegalArgumentException` for unknown user/room. Harmless today (the STOMP
   `@MessageExceptionHandler` catches any `Throwable`), but inconsistent, and blocks reusing the same
   exception/handler pair if a REST equivalent of "send a message" is ever added.
6. **No message/room read-or-write access control tied to membership** — see the membership section; anyone
   authenticated can read/post to any room by name today.
7. **`GET /v1/users`/`GET /v1/users/{id}` expose every user's email, unfiltered, to any authenticated caller.**
   Unchanged from earlier reviews.
8. **No rate limiting on WebSocket message sends.** Was the active work item before the room-membership feature
   took priority — still nothing implemented in `MessageWSController`/`MessageService`/`WebSocketConfig`.
9. **`SimpleUrlAuthenticationSuccessHandler`'s redirect target and `WebSocketConfig`'s allowed origins are
   hardcoded strings**, not config-driven. Fine at one fixed prod domain, brittle if that ever changes.
10. **No backups. No server-side validation on message/room content** (frontend caps length; backend accepts
    anything). **No README, no Actuator (`/health`/`/metrics`), no Bean Validation starter** — the biggest levers
    still open for the "production-ready portfolio piece" framing specifically, now that tests/CI/exception-handling
    have landed and made everything else look comparatively more polished.
11. **`schema.sql` has no versioning** — see Gotchas. Flyway/Liquibase is still the natural graduation path.

**Resolved since earlier passes — do not redo:**
- ~~`PATCH /v1/users/{userId}` always 403~~ — fixed via `@PreAuthorize`.
- ~~GET endpoints returning 200-with-null or a dead 404 branch~~ — fixed via the new exception/`@ControllerAdvice`
  layer.
- ~~Room creation trusting a client-supplied id~~ — fixed.
- ~~"Mine" message detection reading nonexistent `state` fields~~ — fixed.
- ~~Message timestamps rendering "undefined" after the `sentAt`→`createdAt` rename~~ — fixed this pass.
- ~~Root `.env` not gitignored~~, ~~client-trusted `senderId` on the STOMP send path~~, ~~silent send failure~~,
  ~~WebSocket wildcard origin~~ — all fixed in earlier passes, still holding.
- ~~Zero test coverage, no CI~~ — fixed, see Stack.

---

## Suggested next steps (roughly ordered)

1. **Room membership backend** — current focus, see its own section above for the full ordered list
   (`RoomMemberRepository` first, it's the actual blocker).
2. **GitHub OAuth2** — same fix that's been queued for many passes now; the attribute-shape work is fully scoped
   in the Auth model section above.
3. **Rename `AuthenticationException`** — quick, avoids a real future confusion bug.
4. **Wire up `InvalidMessageException`** (content validation) and bring `MessageService.save()` onto the same
   custom-exception pattern as `history()`.
5. **Rate limiting** — was next in line before the membership feature took priority; prerequisites (trustworthy
   per-user identity, an error-feedback channel) are already in place.
6. **Enforce membership on the message read/send paths** once the membership backend exists — otherwise it's
   cosmetic.
7. README, Actuator, Bean Validation starter, Flyway/Liquibase — the portfolio-maturity backlog; biggest remaining
   levers now that testing/CI/exception-handling are done.
8. Deploy hardening: config-driven success-handler URL and CORS origins, backups.

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
- **This is also a portfolio piece demonstrating backend infrastructure knowledge (as of 2026-08-19).** When
  reviewing or suggesting work, weigh "does this look production-grade to a reviewer" alongside "does it work" —
  testing, CI, migrations, observability, and clean API contracts carry real weight here, not just features.
