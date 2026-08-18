import {API, API_BASE} from './config.js';
import {$, el, state} from './state.js';
import {apiFetch, normalizeRooms} from './api.js';
import {connect, disconnect, sendMessage, subscribeToRoom} from './ws.js';
import {
    closeInviteModal,
    openInviteModal,
    renderDiscoverRooms,
    renderInvites,
    renderInviteUserList,
    renderMessage,
    renderNotice,
    renderRoomList,
    setHint,
    showScreen
} from './ui.js';
import {formatCreatedAt, initials, loadName, sanitizeRoom} from './utils.js';

function showLoginScreen() {
    showScreen('screen-login');
}

function showUsernameScreen() {
    showScreen('screen-name');
    el['input-name'].value = state.username || loadName();
    validateName();
    el['input-name'].focus();
}

function validateName() {
    var v = el['input-name'].value.trim();
    var ok = v.length >= 2;
    el['btn-name'].disabled = !ok;
    if (v.length === 0) setHint(el['hint-name'], '');
    else if (!ok) setHint(el['hint-name'], 'Use pelo menos 2 caracteres.');
    else setHint(el['hint-name'], '');
    el['input-name'].setAttribute('aria-invalid', v.length > 0 && !ok ? 'true' : 'false');
    return ok;
}

function submitName(e) {
    e.preventDefault();
    if (!validateName()) {
        el['input-name'].focus();
        return;
    }
    var newDisplayName = el['input-name'].value.trim();

    el['btn-name'].disabled = true;
    el['btn-name'].textContent = 'Enviando…';
    setHint(el['hint-name'], '');

    // Assuming you use PATCH for partial updates.
    // If your backend strictly requires PUT, change the method to 'PUT'.
    apiFetch(API.users(state.user.id), {
        method: 'PATCH',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({displayName: newDisplayName})
    }).then(function (updatedUser) {
        state.user = updatedUser; // Update state with the returned DTO
        el['btn-name'].textContent = 'Continuar';
        showRoomSelection();
    }).catch(function (err) {
        setHint(el['hint-name'], 'Não foi possível atualizar o nome: ' + err.message);
        el['btn-name'].disabled = false;
        el['btn-name'].textContent = 'Continuar';
    });
}

function showRoomSelection() {
    disconnect();

    // Fallback to firstName if displayName is null
    const display = state.user.displayName || state.user.firstName;

    el['who-name'].textContent = display;
    el.avatar.textContent = initials(display);

    showScreen('screen-rooms');
    el['input-room'].value = '';
    setHint(el['hint-room'], '');
    loadRoomsFromServer();
    loadDiscoverRooms();
    loadInvites();
}

function loadRoomsFromServer() {
    var list = el['room-list'];
    list.innerHTML = '';
    el['rooms-empty'].hidden = true;

    var loading = document.createElement('li');
    loading.className = 'empty';
    loading.style.listStyle = 'none';
    loading.textContent = 'Carregando salas…';
    list.appendChild(loading);

    apiFetch(API.rooms, {method: 'GET'})
        .then(function (rooms) {
            state.rooms = normalizeRooms(rooms);
            renderRoomList(joinRoom);
        })
        .catch(function (err) {
            list.innerHTML = '';
            el['rooms-empty'].hidden = true;
            setHint(el['hint-room'], 'Não foi possível carregar as salas: ' + err.message);
        });
}

function loadDiscoverRooms() {
    apiFetch(API.discoverRooms, {method: 'GET'})
        .then(function (rooms) {
            state.discoverRooms = normalizeRooms(rooms);
            renderDiscoverRooms(state.discoverRooms, handleRegisterRoom);
        })
        .catch(function () {
            // Discover is a nice-to-have on this screen; fail quietly rather than
            // blocking the room list if the endpoint isn't available yet.
            state.discoverRooms = [];
            renderDiscoverRooms([], handleRegisterRoom);
        });
}

function handleRegisterRoom(room, btn) {
    if (btn) btn.disabled = true;
    apiFetch(API.registerRoom(room), {method: 'POST'})
        .then(function () {
            renderNotice('Você entrou em #' + room + '.');
            loadRoomsFromServer();
            loadDiscoverRooms();
        })
        .catch(function (err) {
            if (btn) btn.disabled = false;
            setHint(el['hint-room'], 'Não foi possível entrar em #' + room + ': ' + err.message);
        });
}

function loadInvites() {
    apiFetch(API.invites, {method: 'GET'})
        .then(function (invites) {
            state.invites = Array.isArray(invites) ? invites : [];
            renderInvites(state.invites, handleAcceptInvite, handleDeclineInvite);
        })
        .catch(function () {
            state.invites = [];
            renderInvites([], handleAcceptInvite, handleDeclineInvite);
        });
}

function handleAcceptInvite(invite) {
    apiFetch(API.acceptInvite(invite.id), {method: 'POST'})
        .then(function () {
            renderNotice('Você entrou em #' + invite.room + '.');
            loadInvites();
            loadRoomsFromServer();
            loadDiscoverRooms();
        })
        .catch(function (err) {
            setHint(el['hint-room'], 'Não foi possível aceitar o convite: ' + err.message);
        });
}

function handleDeclineInvite(invite) {
    apiFetch(API.declineInvite(invite.id), {method: 'POST'})
        .then(function () {
            loadInvites();
        })
        .catch(function (err) {
            setHint(el['hint-room'], 'Não foi possível recusar o convite: ' + err.message);
        });
}

function submitRoom(e) {
    e.preventDefault();
    var room = sanitizeRoom(el['input-room'].value);
    if (!room) {
        setHint(el['hint-room'], 'Informe um nome de sala válido.');
        return;
    }
    setHint(el['hint-room'], '');

    var btn = el['form-room'].querySelector('button[type="submit"]');
    if (btn) btn.disabled = true;

    apiFetch(API.rooms, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({name: room, isPublic: el['input-room-open'].checked})
    }).then(function () {
        return apiFetch(API.rooms, {method: 'GET'});
    }).then(function (rooms) {
        state.rooms = normalizeRooms(rooms);
        if (btn) btn.disabled = false;
        joinRoom(room);
    }).catch(function (err) {
        if (btn) btn.disabled = false;
        setHint(el['hint-room'], 'Não foi possível criar a sala: ' + err.message);
    });
}

function joinRoom(room) {
    state.room = room;
    var display = state.user.displayName || state.user.firstName;
    el['chat-title'].textContent = '# ' + room;
    el['chat-meta'].textContent = 'como ' + display;
    el.messages.innerHTML = '';
    state.pending = [];
    showScreen('screen-chat');
    el['input-msg'].focus();
    loadHistory(room);
}

function loadHistory(room) {
    apiFetch(API_BASE + '/v1/rooms/' + encodeURIComponent(room) + '/messages', {method: 'GET'})
        .then(function (msgs) {
            if (state.room !== room) return;
            (msgs || []).forEach(function (m) {
                renderMessage({
                    senderId: m.senderId || null,
                    from: m.from || 'anon',
                    content: m.content || '',
                    createdAt: formatCreatedAt(m.createdAt)
                });
            });
        })
        .catch(function (err) {
            if (state.room !== room) return;
            renderNotice('Não foi possível carregar o histórico: ' + err.message, 'error');
        })
        .then(function () {
            if (state.room !== room) return;
            if (state.connected) subscribeToRoom(room);
            else connect();
        });
}

var inviteCandidates = [];

function openInvite() {
    if (!state.room) return;
    openInviteModal(state.room);

    Promise.all([
        apiFetch(API.roomMembers(state.room), {method: 'GET'}).catch(function () { return []; }),
        apiFetch(API.allUsers, {method: 'GET'}).catch(function () { return []; })
    ]).then(function (results) {
        var members = Array.isArray(results[0]) ? results[0] : [];
        var users = Array.isArray(results[1]) ? results[1] : [];
        var memberIds = members.map(function (m) { return m.id; });
        inviteCandidates = users.filter(function (u) {
            return u.id !== state.user.id && memberIds.indexOf(u.id) === -1;
        });
        renderInviteUserList(inviteCandidates, handleInviteUser);
    });
}

function filterInviteCandidates() {
    var q = el['invite-search'].value.trim().toLowerCase();
    if (!q) {
        renderInviteUserList(inviteCandidates, handleInviteUser);
        return;
    }
    var filtered = inviteCandidates.filter(function (u) {
        var name = (u.displayName || u.firstName || '').toLowerCase();
        return name.indexOf(q) !== -1;
    });
    renderInviteUserList(filtered, handleInviteUser);
}

function handleInviteUser(user) {
    apiFetch(API.roomInvites(state.room), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({invitedUserId: user.id})
    }).then(function () {
        setHint(el['hint-invite'], '');
    }).catch(function (err) {
        setHint(el['hint-invite'], 'Não foi possível convidar: ' + err.message);
    });
}

function handleSendMessage(e) {
    if (e) e.preventDefault();
    var content = el['input-msg'].value.trim();
    if (!content) return;

    if (sendMessage(content)) {
        el['input-msg'].value = '';
        el['input-msg'].focus();
    }
}

function cacheEls() {
    ['screen-login', 'screen-name', 'screen-rooms', 'screen-chat', 'form-name', 'input-name', 'hint-name',
        'btn-name', 'avatar', 'who-name', 'btn-change-name', 'room-list', 'rooms-empty',
        'form-room', 'input-room', 'input-room-open', 'hint-room', 'btn-back', 'chat-title', 'chat-meta',
        'status', 'status-text', 'messages', 'form-msg', 'input-msg', 'btn-send',
        'invites-panel', 'invite-list', 'discover-list', 'discover-empty',
        'btn-invite', 'invite-modal', 'invite-room-name', 'invite-search',
        'invite-user-list', 'invite-user-empty', 'hint-invite', 'btn-close-invite']
        .forEach(function (id) {
            el[id] = $(id);
        });
}

function bindEvents() {
    el['form-name'].addEventListener('submit', submitName);
    el['input-name'].addEventListener('input', validateName);
    el['btn-change-name'].addEventListener('click', showUsernameScreen);
    el['form-room'].addEventListener('submit', submitRoom);
    el['btn-back'].addEventListener('click', showRoomSelection);
    el['form-msg'].addEventListener('submit', handleSendMessage);
    el['btn-invite'].addEventListener('click', openInvite);
    el['btn-close-invite'].addEventListener('click', closeInviteModal);
    el['invite-search'].addEventListener('input', filterInviteCandidates);
    window.addEventListener('beforeunload', disconnect);
}

function initializeApp() {
    cacheEls();
    bindEvents();

    // Fetch user session on boot
    apiFetch(API.me, {method: 'GET'})
        .then(function (userDTO) {
            if (!userDTO || !userDTO.id) {
                throw new Error("Invalid session data");
            }
            state.user = userDTO;

            // If they don't have a display name yet, force them to the naming screen
            if (!state.user.displayName) {
                showUsernameScreen();
            } else {
                showRoomSelection();
            }
        })
        .catch(function (err) {
            console.error("Session verification failed:", err);
            // Handle unauthenticated state here (e.g., redirect to OAuth2 login)
            showLoginScreen();
        });
}

document.addEventListener('DOMContentLoaded', initializeApp);
