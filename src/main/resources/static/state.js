export const state = {
    user: null, // Will hold the UserDTO: { id, firstName, lastName, email, displayName }
    room: null,
    rooms: [],
    client: null,
    subscription: null,
    connected: false,
    pending: [] // FIFO queue of { node, content } for optimistically-rendered outgoing messages awaiting confirmation
};

export const el = {};

export function $(id) {
    return document.getElementById(id);
}
