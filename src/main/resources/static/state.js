export const state = {
    user: null, // Will hold the UserDTO: { id, firstName, lastName, email, displayName }
    room: null,
    rooms: [], // room names the current user is a member of
    discoverRooms: [], // open room names not yet joined
    invites: [], // pending invites for the current user: { id, room, invitedBy }
    roomMembers: [], // member UserDTOs of the room currently open in the invite modal
    client: null,
    subscription: null,
    connected: false,
    pending: [] // FIFO queue of { node, content } for optimistically-rendered outgoing messages awaiting confirmation
};

export const el = {};

export function $(id) {
    return document.getElementById(id);
}
