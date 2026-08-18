// Same-origin: works on http://localhost:8080 and https://chattychat.guilhermetests.com alike.
export const API_BASE = window.location.origin;

export const API = {
    me: API_BASE + '/v1/users/me',
    users: (id) => API_BASE + '/v1/users/' + id,
    allUsers: API_BASE + '/v1/users',
    rooms: API_BASE + '/v1/rooms',
    discoverRooms: API_BASE + '/v1/rooms/discover',
    roomMembers: (room) => API_BASE + '/v1/rooms/' + encodeURIComponent(room) + '/members',
    registerRoom: (room) => API_BASE + '/v1/rooms/' + encodeURIComponent(room) + '/members',
    roomInvites: (room) => API_BASE + '/v1/rooms/' + encodeURIComponent(room) + '/invites',
    invites: API_BASE + '/v1/invites',
    acceptInvite: (inviteId) => API_BASE + '/v1/invites/' + inviteId + '/accept',
    declineInvite: (inviteId) => API_BASE + '/v1/invites/' + inviteId + '/decline'
};

export const WS_URL = API_BASE + '/ws';
export const TOPIC = (room) => '/topic/chat/' + room;
export const DEST = (room) => '/app/chat.send/' + room;

export const STORE = {
    name: 'arichat.name',
    id: 'arichat.id'
};
