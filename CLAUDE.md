# CLAUDE.md — ChattyChat

Context for continuing this project in Claude Code. Rewritten 2026-08-21 from a full read of the current tree, on
branch `Invites` (20 commits ahead of `master`, nothing merged or deployed yet), after the room-membership &
invites **backend** landed. **Where it says "verify", check the actual code before trusting it** — this file drifts
from the code the moment someone edits without updating it. Re-read the *whole* tree on every pass, not just the
files that seem relevant — this file has been wrong before from partial re-reads, and was badly stale before this
pass (it described the entire invites backend as "unstarted" while 20 commits of it sat in the tree).

---

## What this is

A small real-time chat app: Spring Boot backend + a vanilla-JS single-page frontend (branded "AriChat" in the UI).
Users log in with Google or GitHub, see/create rooms, and exchange messages in a room over WebSocket/STOMP. Messages
are persisted to Postgres and loaded as history when a user enters a room.

Purpose is **learning**, doubling as a **portfolio project meant to demonstrate backend infrastructure knowledge**
(explicit goal as of 2026-08-19) — treat "does this look production-grade" as a real evaluation axis, not just
"does it work." It is **not production-ready yet** (see "Known gaps").

Auth status: **Google OAuth2 works end-to-end**, including onboarding (the old PATCH-display-name 403 bug is fixed).
**GitHub OAuth2 is implemented** — `CustomOAuth2UserService` now branches per provider. Not confirmed with a live
login; verify before claiming it works.

A **room-membership & invites feature is built on both sides but does not work end-to-end.** Rooms are either open
(self-joinable) or invite-only, and users only see rooms they belong to. Every endpoint exists; several are broken.
See "Room membership & invites (built, defective)" below before touching either side of it.

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
- **Schema management is profile-dependent — read this carefully, CLAUDE.md was wrong about it before.**
  `src/main/resources/schema.sql` is the DDL source of truth, but *when it runs* depends on the profile:
    - `application-test.properties`: `spring.sql.init.mode=always` — `schema.sql` runs on every boot.
    - `application-prod.properties`: `spring.sql.init.mode=never` **plus `spring.jpa.hibernate.ddl-auto=validate`**
      — nothing creates or evolves the prod schema; Hibernate only validates that it matches the entities.
    - `application.properties` sets no `ddl-auto`, and hardcodes `spring.profiles.active=test` (see Known gaps).
  `schema.sql` is idempotent for creation (`CREATE TABLE IF NOT EXISTS`) and defines real `ON DELETE CASCADE` on
  `messages.sender_id`/`messages.room_id`, `room_members`, and `room_invites` — this is what fixed the old
  "deleting a user with message history throws a raw FK violation" bug. **It is not a migration system** — no
  versioning, no `ALTER TABLE` path. With prod on `validate`, this branch's new `rooms.is_public` column and
  `room_invites` table make Flyway/Liquibase a deployment blocker rather than a nice-to-have.
- **Global REST exception handling.** `com.chattychat.Exception` package: custom unchecked exceptions
  (`InvalidUserException`, `InvalidRoomException`, `InvalidMessageException`, `InvalidInviteException`,
  `OAuth2ProvisioningException`), each with its own `@ControllerAdvice` handler class mapping to a clean HTTP
  status. `InvalidUserException`/`InvalidRoomException`/`InvalidMessageException` → 404, `InvalidInviteException`
  → 403, `OAuth2ProvisioningException` → 401.
- **Test coverage exists but has holes.** JUnit 5 + Mockito + AssertJ. `UserUnitTest` (13 tests) and
  `MessageRestUnitTest`/`MessageWSUnitTest` (2 each) are real; **`RoomUnitTest` has both of its tests commented
  out and asserts nothing**, and there are zero tests for `RoomInviteController`/`RoomInviteService`/`RoomService`.
  `UserIntegrationIT` is a genuine Testcontainers-backed integration test that spins up real Postgres and
  exercises the actual `@PreAuthorize` path through `MockMvc` with Spring Security test support
  (`oauth2Login().oauth2User(...)`) — this is what actually proves method security is wired up, not just present.
- **CI exists but is broken**: `.github/workflows/workflow.yaml` runs `./gradlew build` on push/PR to `master`
  with a Postgres service container, but it exports `DB_URL` while the active `test` profile reads `TEST_DB_URL`.
  The placeholder can't resolve, so `contextLoads` fails. See Known gaps.
- **Deploy pipeline**: `.github/workflows/deploy.yml` fires on every push to `master` — SSHes to the VPS,
  `git reset --hard origin/master`, `docker compose -f compose.yaml -f compose.prod.yaml up -d`,
  `./gradlew clean bootJar -x test`, `systemctl restart chattychat.service`. **Tests are skipped on deploy**, and
  the deploy is automatic on merge — so a broken merge to `master` goes straight to prod.
- **Compose files**: `compose.yaml` (base Postgres), `compose.override.yaml` (local, auto-applied by Docker but
  *not* by Spring Boot's docker-compose support), `compose.prod.yaml` (VPS, adds log rotation).
- **API docs exist now**: springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`), `/api-docs` and
  `/swagger-ui.html`. Controllers are annotated with `@Tag`/`@Operation`/`@ApiResponses`. Worth knowing: the docs
  are only as accurate as whoever wrote the annotations — verify a documented response code against the actual
  handler before trusting it (this bit a previous pass: two endpoints promised a 404 the code didn't deliver, now
  fixed, see Known gaps history).
- Debug logging: `spring.jpa.show-sql=true` is **on** in `application-test.properties` and off in prod. Since
  `test` is the hardcoded default profile, a plain `bootRun` logs every query.
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
    - `RoomController` — REST, `/v1/rooms` (list-mine, create, join, discover, self-register, list-members).
      ⚠️ Two of its methods map to the same route — see "Room membership & invites" below.
    - `RoomInviteController` — invites. Annotated `@Controller` (not `@RestController`); it works only because
      every method returns `ResponseEntity`, which `HttpEntityMethodProcessor` handles regardless. Inconsistent
      with the rest of the codebase — see Gotchas.
    - `MessageWSController` — STOMP `@Controller`. `@MessageMapping` resolves sender from the STOMP `Principal`;
      `@MessageExceptionHandler` routes any failure to the sender's private `/user/queue/errors` queue.
    - `MessageRestController` — REST, `/v1/rooms/{room}/messages` history endpoint. Takes no `Principal` — see
      Known gaps (no membership enforcement).
- `com.chattychat.Services` — `UserService`, `RoomService`, `RoomInviteService`, `MessageService`,
  `CustomOAuth2UserService`
- `com.chattychat.Repositories` — `UserRepository`, `RoomRepository`, `MessageRepository`, `RoomMemberRepository`,
  `RoomInviteRepository`. Worth knowing: **`RoomMemberRepository` is an empty `JpaRepository`** — the membership
  queries actually live on `RoomRepository` as `@Query` JPQL (`findAllByUserId`, `findAllByIsPublicNotJoined`,
  `getAllMembers`), not as derived methods on the member repository as originally planned.
- `com.chattychat.Entities` — `User`, `Room`, `ChatMessage`, `RoomMember` + `RoomMemberId` (composite-key
  embeddable), `RoomInvite`. `RoomMember(User, Room)` syncs the composite id manually so it's set before flush.
  `RoomInvite` stamps `createdAt` and a `+7 days` `expiresAt` in `@PrePersist` — **nothing reads `expiresAt`**,
  so expiry is decorative.
- `com.chattychat.Exception` — `InvalidUserException`, `InvalidRoomException`, `InvalidInviteException`,
  `InvalidMessageException` (defined with a registered handler but **never thrown anywhere** — dead scaffolding,
  presumably meant for message content validation that isn't implemented yet), `OAuth2ProvisioningException`.
  One `@ControllerAdvice` handler class per exception type (`UserExceptionHandler`, `RoomExceptionHandler`,
  `RoomInviteExceptionHandler`, `MessageExceptionHandler`, `AuthenticationExceptionHandler`).
- `com.chattychat.dto` — `InboundMessageDTO` (`{content}` only), `OutboundMessageDTO` (`id`, `senderId`, `from`,
  `content`, `createdAt` — no `room` field, `sentAt` was renamed to `createdAt` in the DB-revamp pass), `UserDTO`,
  `RoomDTO` (`id`, `name`, `createdAt`, `isPublic`), `RoomInviteDTO` (`id`, `roomName`, `inviter`, `invitee`,
  `createdAt`, `expiresAt`), `UpdateNameRequestDTO`, `ErrorDTO` (`{error}`, payload for `/user/queue/errors`),
  `AuthUser` (the OAuth2 principal type)

Note the package names are capitalized (`Controller`, `Services`, `Entities`, `Config`, `Exception`) — unusual for
Java convention (normally lowercase), but that's the existing choice; stay consistent.

---

## Data model (read from `schema.sql` + entities)

`schema.sql` is the DDL source of truth, but only the `test` profile actually runs it (see Stack). Tables:

- `users`: `id` (UUID PK, `gen_random_uuid()`), `provider_id`, `provider`, `first_name`, `last_name`, `email`
  (nullable), `display_name` (nullable). `UNIQUE (provider_id, provider)` — the real identity anchor; the surrogate
  `id` is what every other table's FK points at.
- `rooms`: `id` (UUID), `name`, `created_at`, `last_updated_at` (both stamped via `@PrePersist`/`@PreUpdate` on
  `Room` — don't set them manually from a service), `is_public` (`DEFAULT TRUE` in SQL, but Hibernate always
  writes an explicit value and `Room.isPublic` defaults to Java's `false` — see Known gaps).
- `messages`: `id` (UUID), `sender_id` (FK → `users`, `ON DELETE CASCADE`), `room_id` (FK → `rooms`,
  `ON DELETE CASCADE`), `content`, `created_at` (renamed from `sent_at`).
- `room_members`: `user_id` (FK → `users`, `ON DELETE CASCADE`), `room_id` (FK → `rooms`, `ON DELETE CASCADE`),
  `joined_at`, `PRIMARY KEY (user_id, room_id)`. Written by `RoomService.joinRoom`/`joinPublicRoom` and
  `RoomInviteService.acceptInvite`.
- `room_invites`: `id` (UUID), `room_id`, `inviter_id`, `invitee_id` (all FK, all `ON DELETE CASCADE`),
  `created_at`, `expires_at NOT NULL`. **No uniqueness constraint** — the same user can be invited to the same
  room repeatedly, producing duplicate rows.

`rooms.name` has **no unique constraint**, but `RoomRepository.findByName` returns `Optional<Room>` and every
room lookup in the app is by name. Two rooms with the same name will throw a
`NonUniqueResultException` on lookup. Nothing prevents creating them today.

`ChatMessage.sender`/`.room` are still plain `@ManyToOne` (EAGER by default).

---

## Auth model — how login actually works

**Flow:**
1. Frontend renders two plain `<a href>` buttons on `screen-login`: `/oauth2/authorization/google` and
   `/oauth2/authorization/github`. Real browser navigation, no JS.
2. Provider redirects back to `/login/oauth2/code/{google|github}?code=...&state=...` — the only endpoint that
   receives anything directly from the provider in the browser-facing sense (just an auth code).
3. Server-to-server: Spring exchanges the code for tokens, then calls
   `CustomOAuth2UserService.loadUser()` ([CustomOAuth2UserService.java](src/main/java/com/chattychat/Services/CustomOAuth2UserService.java)).
   `super.loadUser()` is wrapped in a try/catch that rethrows failures as `OAuth2ProvisioningException`, then a
   `switch` on `registrationId` dispatches to `googleRegistration` or `githubRegistration`. An unknown
   `registrationId` throws rather than silently producing a null-id user.
4. `SecurityConfig` wires this via `.oauth2Login(...).successHandler(new SimpleUrlAuthenticationSuccessHandler("/"))`
   — success redirects to `/` (relative now, fixed from the old hardcoded `localhost:8080` string).
5. Session (`spring-session-jdbc`) holds the `AuthUser` principal; browser gets a session cookie + `XSRF-TOKEN`.
6. Frontend's `initializeApp()` calls `GET /v1/users/me` on boot. 401 → `screen-login`. 200 with `displayName ==
   null` → `screen-name` (`PATCH /v1/users/{id}` — **this now works**, see below). 200 with a `displayName` →
   straight to rooms.

**GitHub is implemented but unverified against a live login.** `githubRegistration` reads
`Objects.requireNonNull(oAuth2User.getAttribute("id")).toString()` for `providerId` — correct, since GitHub's `id`
is a JSON **number** and a direct `String providerId = getAttribute("id")` compiles but throws
`ClassCastException` at runtime. It reads `name` for the first name and passes `""` for the last.

Remaining rough edge: GitHub's `name` is **nullable** while `login` is always present, but `login` is never read —
a GitHub user with no profile name gets `firstName = "Unknown"` instead of their handle. `email` is usually `null`
even with `user:email` granted (a reliable email needs a separate `GET /user/emails` call), so those accounts
persist with a null email. Neither is fatal; both are worth fixing when someone actually tests this path.

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
- `GET  /v1/rooms` → **rooms the caller is a member of** (`RoomRepository.findAllByUserId`). This is the actual
  mechanism enforcing "only see rooms you're registered to" — not a visibility flag, just what the list query
  reads from.
- `POST /v1/rooms` body `{ "name": "...", "isPublic": bool }` → created room; the creator is auto-registered as a
  member. Client-supplied `id` is ignored (`Room(String name)` constructor). ⚠️ **`isPublic` is silently dropped**
  by `RoomService.createRoom` — see Known gaps.
- `GET  /v1/rooms/discover` → public rooms the caller has not joined (`findAllByIsPublicNotJoined`). Empty in
  practice, because of the `isPublic` bug above.
- `POST /v1/rooms/{room}/members` → self-register into a public room. ⚠️ **Two handlers map to this route**
  (`joinRoom` and `selfRegister`) — see Known gaps.
- `GET  /v1/rooms/{room}/members` → `UserDTO[]`. Throws `InvalidUserException` (404) if the caller isn't a member;
  semantically that should be a 403.
- `POST /v1/rooms/{room}/invites` → create an invite. ⚠️ Declares `@RequestBody UUID` while the frontend sends
  `{invitedUserId}` — see Known gaps.
- `GET  /v1/invites` → `RoomInviteDTO[]` for the caller (as invitee). Expired invites are **not** filtered out.
- `POST /v1/invites/{id}/accept` → join the room. ⚠️ No ownership check, invite never deleted — see Known gaps.
- `POST /v1/invites/{id}/decline` → deletes the invite; correctly checks the caller is the invitee.
- `GET  /v1/rooms/{room}/messages` → `OutboundMessageDTO[]`, oldest-first. Correctly 404s via
  `InvalidRoomException` + `roomRepository.existsByName()` for an unknown room, distinct from "known room, empty
  history."

STOMP:

- Connect endpoint: `/ws` (SockJS, origins locked to localhost + prod domain).
- Send destination: `/app/chat.send/{room}`. Inbound payload: `{ content: "..." }` only.
- Broadcast topic: `/topic/chat/{room}`.
- Private queue: `/user/queue/errors` — `ErrorDTO { error }` on send failure.
- Private queue: `/user/queue/invites` — `RoomInviteDTO` pushed on invite creation. ⚠️ Currently never delivered:
  the server passes `"/user/queue/invites"` to `convertAndSendToUser`, which adds the `/user` prefix itself. See
  Gotchas.

Authorization in `SecurityConfig`: only `/v1/**` and `/ws/**` require authentication; everything else is
`permitAll()`. Still "public unless listed," unchanged posture from earlier reviews.

**Not yet enforced anywhere**: room membership on the *message* paths. Any authenticated user can still read
history for and post messages to any room by name, regardless of membership. Membership currently only changes
which rooms get *listed* — until this lands, the whole feature is cosmetic.

---

## Key design decisions

- **Surrogate UUID PK on `User`, `(provider, providerId)` as the separate unique lookup key.** Unchanged, still the
  right call — don't regress it.
- **`@PreAuthorize` + `@EnableMethodSecurity` for self-scoping, not manual comparisons.** This is the pattern that
  replaced the buggy hand-rolled `UUID`/`String` comparison from an earlier pass. **The membership/invite code did
  not follow it** — it hand-rolls authorization inside service methods instead, and three of those hand-rolled
  checks are wrong (inverted, missing, or the wrong status code). Use `@PreAuthorize` with a membership-check bean
  for the read/post enforcement work rather than adding a fourth hand-rolled check.
- **One custom unchecked exception + one `@ControllerAdvice` handler per domain**, living in `com.chattychat.Exception`.
  Consistent, and it's what fixed the dead-404-branch bugs from earlier reviews. Slightly verbose (a separate
  handler class per exception rather than one `@RestControllerAdvice` with several `@ExceptionHandler` methods) —
  a style choice, not a bug; `InvalidInviteException` + `RoomInviteExceptionHandler` followed it correctly. Keep
  the shape for new domains.
- **`schema.sql` instead of Hibernate `ddl-auto`.** Deliberate move away from implicit DDL. Keep evolving schema
  changes here, but know its limits — see Stack and Gotchas. Flyway/Liquibase is no longer "the natural graduation
  path," it's a blocker: prod runs `ddl-auto=validate` with `sql.init.mode=never`, so this branch's schema
  additions cannot reach a deployed database at all.
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

## Room membership & invites (built, defective)

**Design rationale — unchanged, still the decision.** Decided 2026-08-19: **hybrid access model.** A room is either
`isPublic` (discoverable, self-joinable by any authenticated user) or invite-only (membership only via an existing
member inviting you). Chosen over "fully open" (doesn't satisfy "users only see rooms they're registered to") and
"invite-only everywhere" (simpler, but no public discovery at all).

**Real-time delivery is deliberately STOMP, not webhooks** — a webhook needs the receiver to expose a public HTTP
endpoint (server-to-server delivery model); a logged-in browser tab can't do that, but it already has an open
WebSocket, which is the right tool for "push an event to a specific logged-in user." REST (`GET /v1/invites`)
stays authoritative for catching up on invites received while offline. Reuse this shape (REST for
catch-up/source-of-truth, STOMP `/user/queue/*` for live push) for future real-time features.

**Status as of 2026-08-21: both sides are written; the feature does not work end-to-end.** The frontend (built
2026-08-19) and the backend (built across the 20 commits on `Invites`) were developed against each other without
an integration test, and they disagree in three places. All of this is filed as GitHub issues — check those before
re-diagnosing:

- **Inviting always 400s.** `RoomInviteController.createInvite` declares `@RequestBody UUID`; `app.js` sends
  `{invitedUserId}`.
- **The invite membership check is inverted.** `RoomInviteService.createInvite` throws when the inviter *is* a
  member, so non-members can invite and members cannot.
- **`acceptInvite` is unguarded and non-consuming.** No check that the caller is the invitee (`declineInvite` has
  one), and the "delete the invite" step is an empty comment.
- **The WS push never arrives.** `"/user/queue/invites"` passed to `convertAndSendToUser`, which adds `/user`
  itself. Compare `MessageWSController`, which correctly passes `"/queue/errors"`.
- **Every room is created private.** `RoomService.createRoom` calls `new Room(room.name())` and drops `isPublic`,
  so `/discover` is always empty.
- **`POST /v1/rooms/{room}/members` has two handlers** (`joinRoom`, `selfRegister`) on the same path+method.
- **The invite panel renders `undefined`.** `ui.js`/`ws.js`/`app.js` read `invite.room` and `invite.invitedBy`;
  `RoomInviteDTO` ships `roomName` and `inviter`.

**Frontend pieces** (all present, unchanged since 2026-08-19):
- `index.html`: "Convites pendentes" panel and "Salas abertas para entrar" (discover) panel on `screen-rooms`; an
  "open room" checkbox on the create-room form; a "Convidar" button in the chat header; an invite modal
  (`#invite-modal`) with search + candidate list.
- `state.js`: `state.discoverRooms`, `state.invites`, `state.roomMembers`.
- `ui.js`: `renderDiscoverRooms`, `renderInvites`, `renderInviteUserList`, `openInviteModal`/`closeInviteModal`.
- `app.js`: `loadDiscoverRooms`/`handleRegisterRoom`, `loadInvites`/`handleAcceptInvite`/`handleDeclineInvite`,
  `openInvite`/`filterInviteCandidates`/`handleInviteUser`.
- `ws.js`: subscribes to `/user/queue/invites` on connect, shows a live notice on push.

**Still genuinely unbuilt:**
1. **Authorization enforcement on the message read/send paths** — without this, membership is cosmetic. Anyone can
   still read/post to any room by name regardless of membership.
2. **Invite expiry enforcement.** `RoomInvite.expiresAt` is stamped at `+7 days` and never read.
3. **Duplicate-invite prevention.** No unique constraint on `(room_id, invitee_id)`, no check before insert.

**Known limitation to revisit, not solved:** STOMP only connects once a user enters a room (`connect()` fires from
`loadHistory()`), so a live invite push never reaches someone sitting on the room-list screen — that screen's
invite panel relies on a REST refresh instead. Connecting to STOMP right after login instead of on room-entry
would fix this properly; that's a connection-lifecycle change, out of scope for the membership feature itself.
Note this limitation currently masks the broken-destination bug above: nobody would have noticed the push failing.

---

## Gotchas learned the hard way (do not re-introduce)

- **`sub` is an OIDC-only claim; GitHub's `id` is a JSON number, not a string.** See Auth model. Any
  provider-agnostic code must branch on `registrationId`, and must convert GitHub's numeric `id` via
  `String.valueOf(...)` rather than casting — a direct `String providerId = oAuth2User.getAttribute("id")` compiles
  fine and throws `ClassCastException` at runtime.
- **`UUID.equals(Object)` silently returns `false` for any non-`UUID` argument.** Not a compile error — this is
  exactly what caused the old PATCH-403 bug (comparing a `UUID` path variable against a `String` provider id). Now
  fixed via `@PreAuthorize`, but the lesson generalizes: always compare same-typed identifiers.
- **Don't name your own exception the same simple name as a framework class you depend on.** The old
  `com.chattychat.Exception.AuthenticationException` shadowed `org.springframework.security.core.AuthenticationException`;
  it compiled fine but was a live wrong-import trap in a Spring-Security-based codebase. Renamed to
  `OAuth2ProvisioningException` — keep it that way, and apply the same rule to any new exception.
- **`convertAndSendToUser` adds the `/user` prefix itself.** Pass `"/queue/invites"`, never
  `"/user/queue/invites"` — the latter resolves to `/user/{id}/user/queue/invites` and the client subscribed to
  `/user/queue/invites` silently never receives it. No error is raised anywhere; the message is just dropped.
  `MessageWSController`'s `"/queue/errors"` is the correct reference; `RoomInviteController` got it wrong.
- **Two `@PostMapping`s with differently-named path variables on the same path are NOT caught at startup.**
  `@PostMapping("/{roomName}/members")` and `@PostMapping("/{room}/members")` produce distinct pattern *strings*,
  so Spring registers both without complaint — then throws `IllegalStateException: Ambiguous handler methods
  mapped` on the first matching request. A 500 at runtime, not a startup failure, and nothing in the build catches
  it. `RoomController` has exactly this today.
- **`schema.sql` is not idempotent for schema *evolution*, only creation, and prod doesn't run it at all.**
  `CREATE TABLE IF NOT EXISTS` protects re-runs against a database that already has the tables, but adding a new
  table/column later means editing this file AND manually applying that change to any database that already ran an
  older version — the file has no version history and won't retroactively apply anything. Prod additionally runs
  `sql.init.mode=never` + `ddl-auto=validate`, so it will refuse to start rather than self-heal. Don't treat any
  of this as "migrations."
- **`@MapsId` requires the mapped-id field's type to exactly match the referenced entity's `@Id` type.**
  `RoomMemberId.userId`/`.roomId` are `UUID`, matching `User`/`Room`'s `@Id` — consistent now, but this broke
  transiently earlier when `User`'s PK was briefly a `String`. Keep them in sync if either PK type ever changes.
- **Reserved SQL words.** `user` and `from` are reserved in Postgres — table is `users`, sender column is
  `sender_id`, never `from`.
- **`@ManyToOne` join columns must not be `unique`.** Turns "many per parent" into "one per parent."
- **Derived query property paths traverse associations, not FK columns** — e.g.
  `findByRoomNameOrderByCreatedAtAsc` walks `ChatMessage.room.name`. Follow this convention for new repository
  methods (e.g. `RoomMemberRepository.findByMember_Id`, not something keyed off the raw FK column name).
- **REST needs `@RestController`, not `@Controller`.** Keep STOMP and HTTP endpoints in separate beans.
  `RoomInviteController` breaks this — it's a plain `@Controller` holding only HTTP endpoints, and works purely
  because every method returns `ResponseEntity` (which `HttpEntityMethodProcessor` handles without `@ResponseBody`).
  Return a bare DTO from any method there and it will try to resolve a *view name* instead. Fix the annotation
  rather than relying on the accident.
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
Items 1-8 were found in the 2026-08-21 audit and are filed as GitHub issues; the rest are carried over.

**Blocking correctness bugs in the shipped membership/invite code:**

1. **`POST /v1/rooms/{room}/members` has two handlers.** `RoomController.joinRoom` (`/{roomName}/members`) and
   `selfRegister` (`/{room}/members`) both match; Spring throws `Ambiguous handler methods mapped` at request
   time. Joining a room is a 500. See Gotchas — this is not caught at startup.
2. **Inverted membership check in `RoomInviteService.createInvite`.** Throws when the inviter *is* a member, so
   non-members can invite anyone into any room and actual members can't invite at all.
3. **`acceptInvite` has no ownership check and never deletes the invite.** Any authenticated user can accept any
   invite by id and gain membership; accepted invites stay pending and are replayable.
4. **The `/user/queue/invites` push is never delivered.** Double `/user` prefix — see Gotchas.
5. **Invite creation body mismatch.** `@RequestBody UUID` vs the frontend's `{invitedUserId}` → 400 every time.
6. **The invite panel renders `undefined`.** Frontend reads `invite.room`/`invite.invitedBy`; the DTO ships
   `roomName`/`inviter`. Exactly the `sentAt`→`createdAt` failure mode again — see Gotchas.
7. **`RoomService.createRoom` drops `isPublic`.** `new Room(room.name())` leaves it at Java's `false`, so no room
   is ever public and `/discover` is permanently empty.

**Infrastructure:**

8. **CI is broken.** The workflow exports `DB_URL`; the active `test` profile reads `TEST_DB_URL`. The placeholder
   can't resolve and `contextLoads` fails, so `./gradlew build` fails on every push and PR to `master`.
9. **`schema.sql` has no versioning, and prod can't apply it.** Prod runs `ddl-auto=validate` +
   `sql.init.mode=never`, so `rooms.is_public` and `room_invites` cannot reach a deployed database — merging this
   branch to `master` auto-deploys and fails Hibernate validation at startup. Flyway is now a blocker, not a
   nice-to-have.
10. **`spring.profiles.active=test` is hardcoded** in `application.properties`. An unset `SPRING_PROFILES_ACTIVE`
    in prod silently falls back to test config, including `sql.init.mode=always` and `show-sql`.
11. **No test coverage for rooms, membership, or invites.** `RoomUnitTest` is entirely commented out; there are no
    tests for `RoomInviteService`/`RoomInviteController`/`RoomService`. Gaps 2, 3, and 7 are all bugs one
    happy-path test would have caught.

**Carried over from earlier passes:**

12. **No message/room read-or-write access control tied to membership** — anyone authenticated can read/post to
    any room by name. Until this lands the membership feature only changes which rooms are *listed*.
13. **`GET /v1/users`/`GET /v1/users/{id}` expose every user's email, unfiltered, to any authenticated caller.**
    The invite picker consumes the list endpoint and only needs id + display name.
14. **No rate limiting on WebSocket message sends.** Nothing in
    `MessageWSController`/`MessageService`/`WebSocketConfig`.
15. **`InvalidMessageException` is dead code** — defined with a registered handler, never thrown. No server-side
    validation on message/room content at all (the frontend caps length; the backend accepts anything).
    `spring-boot-starter-validation` is absent, so the `@Valid` on `RoomController.createRoom` is inert — it
    resolves only because springdoc pulls `jakarta.validation-api` transitively, and the documented "400 Invalid
    room data" can never fire.
16. **`rooms.name` has no unique constraint** but every lookup is `findByName` returning `Optional` — two
    same-named rooms make `NonUniqueResultException` on every access to either. Nothing prevents creating them.
17. **`WebSocketConfig`'s allowed origins are hardcoded strings**, not config-driven. Fine at one fixed prod
    domain, brittle if it changes. (The success-handler URL half of this gap is fixed — it's relative now.)
18. **No backups. No README, no Actuator (`/health`/`/metrics`)** — the biggest remaining levers for the
    "production-grade portfolio piece" framing, especially with a live VPS deploy and no health endpoint.
19. **Invite expiry is decorative** (`expiresAt` stamped, never read) and **duplicate invites are unconstrained**.

**Resolved since earlier passes — do not redo:**
- ~~GitHub OAuth2 broken~~ — fixed; `CustomOAuth2UserService` branches on `registrationId` and converts GitHub's
  numeric `id` via `.toString()`. Still unverified with a live login.
- ~~`AuthenticationException` shadows Spring Security's class~~ — renamed to `OAuth2ProvisioningException`.
- ~~`MessageService.save()` throws bare `IllegalArgumentException`~~ — now throws `InvalidUserException`/
  `InvalidRoomException`, matching `history()`. No `IllegalArgumentException` remains in `src/main/java`.
- ~~Room membership has no backend~~ — built; see its own section for what's broken in it.
- ~~`RoomMemberRepository` missing~~ — exists (empty; the queries live on `RoomRepository`).
- ~~Room creator not auto-registered as a member~~ — done in `RoomController.createRoom`.
- ~~`Room.isPublic` column/entity/schema missing~~ — all present (the value is dropped on create, see gap 7).
- ~~`Invite` entity/repo/service/controller/table missing~~ — all present.
- ~~Hardcoded `localhost:8080` success-handler redirect~~ — relative `"/"` now.
- ~~`PATCH /v1/users/{userId}` always 403~~ — fixed via `@PreAuthorize`.
- ~~GET endpoints returning 200-with-null or a dead 404 branch~~ — fixed via the new exception/`@ControllerAdvice`
  layer.
- ~~Room creation trusting a client-supplied id~~ — fixed.
- ~~"Mine" message detection reading nonexistent `state` fields~~ — fixed.
- ~~Message timestamps rendering "undefined" after the `sentAt`→`createdAt` rename~~ — fixed.
- ~~Root `.env` not gitignored~~, ~~client-trusted `senderId` on the STOMP send path~~, ~~silent send failure~~,
  ~~WebSocket wildcard origin~~ — all fixed in earlier passes, still holding.
- ~~Zero test coverage, no CI~~ — both exist, both have holes; see gaps 8 and 11.

---

## Suggested next steps (roughly ordered)

**Do not merge `Invites` to `master` before at least steps 1-3.** The deploy workflow fires automatically on
every push to `master`, builds with `-x test`, and restarts the service — a broken merge goes straight to prod.

1. **Fix CI** (gap 8). Everything else is unverifiable while the build is red, and it's a one-line env fix.
2. **Fix the seven membership/invite bugs** (gaps 1-7). They're independent and small; 1, 5, and 7 are the ones
   that make the feature non-functional at all.
3. **Flyway** (gap 9). Required before this branch can reach the VPS, not optional cleanup.
4. **Tests for rooms/membership/invites** (gap 11) — un-comment `RoomUnitTest` against the current signatures and
   add an invite→accept→listed integration test. Bugs 2, 3, and 7 argue for this better than any principle does.
5. **Enforce membership on the message read/send paths** (gap 12) — until this lands the whole feature is
   cosmetic. Use `@PreAuthorize` + a membership-check bean, not another hand-rolled service check.
6. **Trim the user list DTO** (gap 13) — small, and the invite picker made it load-bearing.
7. **Rate limiting** (gap 14); prerequisites (trustworthy per-user identity, an error-feedback channel) are in
   place.
8. **Wire up `InvalidMessageException`** + add `spring-boot-starter-validation` (gap 15).
9. README, Actuator, backups, config-driven CORS origins — the portfolio-maturity backlog.

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
