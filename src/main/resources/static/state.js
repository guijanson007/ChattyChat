export const state = {
    username: '',
    userId: null,
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
