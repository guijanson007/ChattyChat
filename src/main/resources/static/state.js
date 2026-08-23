// @ts-check

/**
 * @typedef {import('@api').UserDTO} UserDTO
 * @typedef {import('@api').RoomInviteDTO} RoomInviteDTO
 */

/**
 * One optimistically-rendered outgoing message awaiting server confirmation.
 * @typedef {{ node: HTMLElement, content: string }} PendingMessage
 */

/**
 * DOM element cache, populated by cacheEls() in app.js before any render runs.
 *
 * Typed non-null even though $() can return null: every consumer assumes
 * cacheEls() found its ids, and a missing id is a template bug that should blow
 * up loudly at first use rather than be null-checked at ~40 call sites.
 *
 * The named keys are the ones read as something more specific than HTMLElement
 * (.value, .checked, .disabled). Everything else falls through to the index
 * signature.
 *
 * @typedef {Record<string, HTMLElement> & {
 *   'input-name': HTMLInputElement,
 *   'input-room': HTMLInputElement,
 *   'input-room-open': HTMLInputElement,
 *   'input-msg': HTMLInputElement,
 *   'invite-search': HTMLInputElement,
 *   'form-name': HTMLFormElement,
 *   'form-room': HTMLFormElement,
 *   'form-msg': HTMLFormElement,
 *   'btn-name': HTMLButtonElement,
 *   'btn-send': HTMLButtonElement,
 * }} ElementCache
 */

/**
 * @typedef {object} AppState
 * @property {UserDTO | null} user            current session's user, from GET /v1/users/me
 * @property {string | null} room             name of the room currently open
 * @property {string[]} rooms                 room names the current user is a member of
 * @property {string[]} discoverRooms         open room names not yet joined
 * @property {RoomInviteDTO[]} invites        pending invites for the current user
 * @property {UserDTO[]} roomMembers          members of the room open in the invite modal
 * @property {any} client                     STOMP client (untyped: stompjs is a CDN global)
 * @property {any} subscription               active STOMP subscription for the open room
 * @property {boolean} connected
 * @property {PendingMessage[]} pending       FIFO queue of unconfirmed outgoing messages
 */

/** @type {AppState} */
export const state = {
    user: null,
    room: null,
    rooms: [],
    discoverRooms: [],
    invites: [],
    roomMembers: [],
    client: null,
    subscription: null,
    connected: false,
    pending: []
};

/** @type {ElementCache} */
export const el = /** @type {ElementCache} */ ({});

/**
 * @param {string} id
 * @returns {HTMLElement | null}
 */
export function $(id) {
    return document.getElementById(id);
}
