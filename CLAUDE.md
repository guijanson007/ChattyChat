# CLAUDE.md — ChattyChat

Context for continuing this project in Claude Code. This file was assembled from a prior working session; some details
are inferred from conversation rather than read from the current tree. **Where it says "verify", check the actual code
before trusting it.**

---

## What this is

A small real-time chat app: Spring Boot backend + a vanilla-JS single-page frontend (branded "AriChat" in the UI). Users
pick a display name, see/create rooms, and exchange messages in a room over WebSocket/STOMP. Messages are persisted to
Postgres and loaded as history when a user enters a room.

Purpose is **learning**, with a stretch goal of possibly running it as a small friends-and-family tool. It is **not
production-ready** (see "Known gaps").

---

## Stack

- Java 21, Spring Boot (Spring Framework 7.x, Spring Data JPA 4.x, Hibernate 7.4).
- PostgreSQL (via HikariCP). Driver `postgresql-42.7.x`.
- Messaging: STOMP over SockJS, in-memory `SimpleBroker`.
- Frontend: plain HTML/CSS/JS, no build step. SockJS + stomp.js from CDN. Split into `index.html`, `styles.css`,
  `app.js`. `app.js` is one IIFE.
- Frontend talks to `http://localhost:8080` (hardcoded `API_BASE` / `WS_URL`).

---

## Package / layout (verify against tree)

Backend package root `com.chattychat`, layered controller → service → repository:

- `com.chattychat.Controller`
    - `UserController` — REST, `/v1/users`
    - `RoomController` — REST, `/v1/rooms` (assumed; not fully seen)
    - `MessageController` — STOMP `@Controller`, `@MessageMapping` only
    - `MessageRestController` — REST `@RestController` `/v1/rooms`, history endpoint
- `com.chattychat.Services` — `UserService`, `RoomService` (assumed), `MessageService`
- `com.chattychat.Repositories` — `UserRepository`, `RoomRepository`, `MessageRepository`
- `com.chattychat.Entities` — `User`, `Room`, `ChatMessage`
- `com.chattychat.dto` — `InboundMessageDTO`, `OutboundMessageDTO`, plus user/room DTOs

Note the package names are capitalized (`Controller`, `Services`, `Entities`) — unusual for Java convention (normally
lowercase), but that's the existing choice; stay consistent.

---

## Data model (as revealed by SQL logs — verify)

Tables: `users`, `room`, `messages`. (Naming is inconsistent — two plural, one singular. Not worth churning now, but be
aware.)

- `users`: `id` (UUID, `@GeneratedValue(strategy = UUID)`), `name`.
  `name` is **not** unique (see decision below).
- `room`: `id` (UUID), `name`, `created_at`. `name` **is** unique.
- `messages`: `id` (UUID), `content`, `room_id` (FK → room), `sender_id` (FK → users),
  `sent_at`. Both FKs are plain `@ManyToOne` (NOT unique — see gotcha).

`ChatMessage` uses `@ManyToOne` for `sender` and `room` (EAGER by default).

---

## HTTP + messaging contract

REST:

- `POST /v1/users` body `{ "name": "..." }` → `User { id, name }`.
- `GET  /v1/rooms` → array of rooms (`{id,name}` or `["name"]`; frontend normalizes).
- `POST /v1/rooms` body `{ "name": "..." }` → created room.
- `GET  /v1/rooms/{room}/messages` → `OutboundMessageDTO[]`, oldest-first (history).

STOMP:

- Connect endpoint: `/ws` (SockJS).
- App prefix `/app`; send destination `/app/chat.send/{room}`.
- Topic (broadcast): `/topic/chat/{room}`.
- Inbound payload from client: `{ senderId: <UUID>, content: "..." }`.
- `OutboundMessageDTO` carries: `id`, `senderId`, `from` (name), `room` (name),
  `content`, `sentAt`. The frontend needs `senderId` to decide message ownership.

---

## Key design decisions

- **Send `senderId` (UUID), not the display name.** Identity is the stable UUID; the name is a free-to-collide display
  label. Backend resolves the user via `findById`.
- **User `name` is NOT unique; room `name` IS unique.** Room uniqueness is required because STOMP topics route by room
  name (`/topic/chat/{room}`) and `RoomRepository`
  looks rooms up by name. User uniqueness was deliberately dropped so two people can share a display name.
- **Separate inbound/outbound message DTOs.** Inbound = `{senderId, content}` (what the browser sends; room comes from
  the STOMP destination, not the body). Outbound keeps
  `from` as a plain string so the frontend renders names without unpacking entities. DTOs never embed JPA entities.
- **Mapping lives in the service, not on the entity.** No `toDTO()` on entities (avoids entities importing DTOs).
  Entities are built via an explicit constructor that omits
  `id` so `@GeneratedValue` owns the key — no `@AllArgsConstructor` positional-arg trap.
- **Persist-then-broadcast.** `MessageService.save` writes the row, then the handler's return value is broadcast via
  `@SendTo`. A DB failure means no broadcast (usually desired), but see "silent send failure" below.
- **History loads on room entry, then subscribe.** Frontend GETs history, renders it, and only *then* subscribes to the
  live topic, so live messages append in order. Small message-loss window exists between the history query and the
  subscription.
- **Timestamps:** backend sends `LocalDateTime` as ISO-8601 strings; frontend formats them to `HH:MM:SS` via
  `new Date(iso)`.

---

## Gotchas learned the hard way (do not re-introduce)

- **Reserved SQL words.** `user` and `from` are reserved in Postgres. The table is
  `users` (not `user`); the message sender column is `sender_id` (never `from`). Naming a table/column with a reserved
  word yields `syntax error at or near "..."`.
- **`ddl-auto=update` is additive only.** It ADDS columns/constraints but never DROPS them. Removing `unique=true` from
  an entity does NOT remove the existing DB constraint — you must `ALTER TABLE ... DROP CONSTRAINT` by hand. This bit
  the project twice (leftover unique constraints on `user.name` and on `messages.room_id`).
- **`@ManyToOne` join columns must not be `unique`.** A `unique=true` on `room_id`
  turned "many messages per room" into "one message per room" and caused duplicate-key violations on the 2nd message.
  Same risk on `sender_id`.
- **Derived query property paths.** To filter by room name use
  `findByRoomNameOrderBySentAtAsc(String)` — `RoomName` traverses `room.name` and takes a `String`.
  `findByRoom...(String)` tries to match the whole `Room` entity against a string and throws
  `not assignable to ...Room`.
- **REST needs `@RestController`, not `@Controller`.** A `@GetMapping` on a plain
  `@Controller` treats the return value as a view name and 404s to static-resource handling. Keep STOMP
  (`@MessageMapping`) and HTTP endpoints in separate beans.
- **`@DestinationVariable` name must match the template**, or bind explicitly:
  `@DestinationVariable("room") String roomName`. Prefer the explicit form — it survives compilation without
  `-parameters`.
- **Duplicate rows + `Optional` finder = `NonUniqueResultException`.** `findByName`
  returning `Optional` blows up if two rows share the name. This is why the id-based lookup replaced name-based for
  users.
- **Frontend boot order.** All DOM-touching code must run after `cacheEls()` inside the
  `DOMContentLoaded` handler (`initializeApp`). Code placed at the top level of the IIFE runs before `el` is populated,
  throws, and halts the rest of the script (which is why the "Continue" button once became permanently un-clickable —
  the bottom
  `addEventListener` never ran).

---

## Frontend specifics

- `localStorage` keys: `arichat.name`, `arichat.userId`. Boot requires BOTH; otherwise it sends the user back through
  the name screen (which re-POSTs to create the user).
- **After any DB wipe, clear both localStorage keys** — a stale `userId` makes sends fail silently (backend `findById`
  misses; STOMP gives no error back to the client).
- "Mine" rendering compares `senderId === state.userId`, falling back to name only if id is absent. Comparing by name
  would misattribute messages once names collide.

---

## Known gaps (NOT production-ready)

Ranked by how much they'd hurt a real friends-and-family deployment:

1. **No authentication.** Identity is an unverified name + a client-supplied `senderId`. Anyone can impersonate anyone
   via dev tools. Pure honor system.
2. **HTTP + hardcoded localhost.** Must be deployed with HTTPS/WSS and a real host before anyone off-machine can use it.
   Plaintext otherwise.
3. **`ddl-auto=update` against real data.** Switch to Flyway/Liquibase migrations +
   `ddl-auto=validate` before it holds anything you care about.
4. **No backups.** Add at least a nightly `pg_dump`.
5. **Silent send failure.** No `@MessageExceptionHandler` — a failed persist drops the message with zero feedback to the
   sender. Add one that sends an error frame back.
6. **No server-side validation / rate limiting.** Frontend caps length at 1000 chars, but the backend accepts anything.
   The frontend is not a security boundary.
7. **Operational blind spots.** No health check, no monitoring, `open-in-view` is on.

---

## Suggested next steps (roughly ordered)

1. `@MessageExceptionHandler` in `MessageController` → send failures to the sender.
2. Server-side content validation (non-empty, max length) on inbound messages.
3. Flyway migrations; flip `ddl-auto` to `validate`.
4. Minimal auth (even a shared passphrase or per-user password).
5. Deploy target: managed Postgres (gets you TLS + backups) behind HTTPS/WSS.
6. Optional/learning: virtual-thread executor on the STOMP inbound channel (`configureClientInboundChannel`) for cheaper
   blocking-JDBC concurrency — note this can reorder within-room messages; consider `setPreservePublishOrder(true)`.
   Only meaningful at hundreds of concurrent handlers, which this is nowhere near.

---

## How to work with the project owner

These are standing instructions from the owner — follow them:

- **Be direct. No cheerleading.** No "great question", no praise padding. If an idea is bad, say so and explain why.
- **Challenge assumptions and point out problems** before implementing. Ask hard questions. If something won't work, say
  so directly.
- **Verify before claiming success.** Don't assert something works without evidence; test assertions where possible. If
  a claim can't be verified, state exactly why. Show failures and errors honestly rather than papering over them.
- **Keep the project directory clean.** Do NOT create `test-*` or `debug-*` files unless explicitly asked.
- Minimal-formatting prose is preferred for explanations; don't over-format.
- **Frontend-only changes unless explicitly told otherwise (standing, as of 2026-08-12).** Only touch
  `src/main/resources/static/*` (`index.html`, `app.js`, `styles.css`) and other pure-frontend assets. Do not modify
  backend Java code, `application.properties`, `build.gradle`, `SecurityConfig`, or any other backend/config file —
  even if a task appears to need it — without the owner explicitly asking for backend changes. If a frontend task is
  blocked by something only fixable on the backend (e.g., `SecurityConfig`'s `anyRequest().authenticated()` blocking
  static assets for logged-out users), say so and stop; do not go fix it yourself.
