export const state = {
    user: null, // Will hold the UserDTO: { id, firstName, lastName, email, displayName }
    room: null,
    rooms: [],
    client: null,
    subscription: null,
    connected: false
};

export const el = {};

export function $(id) {
    return document.getElementById(id);
}
