// Friendly aliases over the generated OpenAPI types.
//
// Reference these from JSDoc as `import('@api').UserDTO` — the `@api` path alias is
// mapped in tsconfig.json, so the generated file's location appears exactly once
// (right below) instead of at every call site.
//
// Regenerate api.d.ts with `npm run gen:api` (needs the app running with
// SWAGGER_ENABLED=true). Do not hand-edit it.

import type { components } from './api';

type Schemas = components['schemas'];

// ---------------------------------------------------------------------------
// Generated from the springdoc spec (REST). Source of truth for field NAMES:
// the Java records in src/main/java/com/chattychat/dto/.
//
// Why Required<> is wrapped around everything:
//
// springdoc marks every property optional (`id?`, `createdAt?`) because Java
// records carry no nullability annotations, so it has nothing to infer from.
// That is a tooling artifact, not the contract. Jackson's default inclusion is
// ALWAYS and there is no @JsonInclude anywhere in src/main/java, so a record
// always serializes every component — the key is always present, it is only the
// value that may be null.
//
// Taking the `?` at face value would push `string | undefined` through every
// read site and force either a null check or a `?? ''` at each one, i.e. real
// runtime changes to satisfy a false positive. Required<> models presence
// correctly; genuinely-nullable values are spelled out as `| null` below.
// ---------------------------------------------------------------------------

/**
 * email and displayName are the two genuinely nullable fields:
 *  - displayName is null until onboarding completes (the whole screen-name flow
 *    keys off exactly that).
 *  - email is null for GitHub accounts that never granted a public address.
 * firstName always has a value ("Unknown" is the fallback in
 * CustomOAuth2UserService); lastName is "" rather than null for GitHub.
 */
export type UserDTO = Omit<Required<Schemas['UserDTO']>, 'email' | 'displayName'> & {
    email: string | null;
    displayName: string | null;
};

export type RoomDTO = Required<Schemas['RoomDTO']>;
export type RoomInviteDTO = Omit<Required<Schemas['RoomInviteDTO']>, 'inviter' | 'invitee'> & {
    inviter: UserDTO;
    invitee: UserDTO;
};

/** `from` is never null — MessageService falls back to firstName when displayName is null. */
export type OutboundMessageDTO = Required<Schemas['OutboundMessageDTO']>;

export type CreateInviteRequestDTO = Required<Schemas['CreateInviteRequestDTO']>;
export type UpdateNameRequestDTO = Required<Schemas['UpdateNameRequestDTO']>;

// ---------------------------------------------------------------------------
// Hand-written: STOMP-only payloads.
//
// These are NOT in the generated file and never will be. springdoc only sees
// HTTP handler mappings, and these two DTOs cross the wire exclusively over
// WebSocket destinations, so there is no OpenAPI schema to generate them from.
//
// Keep in sync with com.chattychat.dto.ErrorDTO and
// com.chattychat.dto.InboundMessageDTO by hand — the CI drift check does not
// cover them.
// ---------------------------------------------------------------------------

/** Payload on /user/queue/errors. Mirrors com.chattychat.dto.ErrorDTO. */
export type ErrorDTO = {
    error: string;
};

/** Payload sent to /app/chat.send/{room}. Mirrors com.chattychat.dto.InboundMessageDTO. */
export type InboundMessageDTO = {
    content: string;
};
