export const API_BASE = 'http://localhost:8080';
export const API = {
    me: API_BASE + '/v1/users/me',
    users: API_BASE + '/v1/users',
    rooms: API_BASE + '/v1/rooms'
};

export const WS_URL = API_BASE + '/ws';
export const TOPIC = (room) => '/topic/chat/' + room;
export const DEST = (room) => '/app/chat.send/' + room;

export const STORE = {
    name: 'arichat.name',
    userId: 'arichat.userId'
};
