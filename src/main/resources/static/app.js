(function () {
    'use strict';

    /* ---------------- config ---------------- */
    var API_BASE = 'http://localhost:8080';
    var API = {
        users: API_BASE + '/v1/users',
        rooms: API_BASE + '/v1/rooms'
    };

    var WS_URL = API_BASE + '/ws';
    var TOPIC = function (room) { return '/topic/chat/' + room; };
    var DEST  = function (room) { return '/app/chat.send/' + room; };

    var STORE = { name: 'arichat.name', userId: 'arichat.userId' };

    function loadUserId() { try { return localStorage.getItem(STORE.userId) || null; } catch (e) { return null; } }
    function saveUserId(v) { try { localStorage.setItem(STORE.userId, v); } catch (e) {} }


    /* ---------------- state ---------------- */
    var state = {
        username: '',
        userId: null,
        room: null,
        rooms: [],        // in-memory cache of names from GET /v1/rooms
        client: null,
        subscription: null,
        connected: false
    };

    var el = {};
    function $(id) { return document.getElementById(id); }

    /* ---------------- api ---------------- */
    // Resolves to parsed JSON (or null for 204). Rejects with a real message on non-2xx.
    function apiFetch(url, options) {
        return fetch(url, options).then(function (res) {
            if (!res.ok) {
                return res.text().then(function (body) {
                    throw new Error('HTTP ' + res.status + (body ? ': ' + body.slice(0, 200) : ''));
                });
            }
            if (res.status === 204) return null;
            return res.text().then(function (t) { return t ? JSON.parse(t) : null; });
        });
    }

    // Backend room shape is unverified. Accept [{id,name}] or ["name"], drop anything else.
    function normalizeRooms(rooms) {
        if (!Array.isArray(rooms)) return [];
        return rooms.map(function (r) {
            if (typeof r === 'string') return r;
            return r && r.name ? r.name : null;
        }).filter(Boolean);
    }

    /* ---------------- storage (name only) ---------------- */
    function loadName() {
        try { return localStorage.getItem(STORE.name) || ''; } catch (e) { return ''; }
    }
    function saveName(v) { try { localStorage.setItem(STORE.name, v); } catch (e) {} }

    /* ---------------- helpers ---------------- */
    function showScreen(id) {
        ['screen-login', 'screen-name', 'screen-rooms', 'screen-chat'].forEach(function (s) {
            el[s].classList.toggle('is-active', s === id);
        });
    }
    function initials(name) {
        return name.trim().slice(0, 1).toUpperCase() || '?';
    }
    function sanitizeRoom(v) {
        return v.trim().toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9._-]/g, '');
    }
    function nowTime() {
        return new Date().toTimeString().slice(0, 8);
    }
    function setHint(node, msg) { node.textContent = msg || ''; }

    /* ---------------- screen 1: login ---------------- */
    function showLoginScreen() {
        showScreen('screen-login');
    }

    /* ---------------- screen 2: username ---------------- */
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
        if (!validateName()) { el['input-name'].focus(); return; }
        var name = el['input-name'].value.trim();

        el['btn-name'].disabled = true;
        el['btn-name'].textContent = 'Enviando…';
        setHint(el['hint-name'], '');

        apiFetch(API.users, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: name })
        }).then(function (user) {
            state.username = name;
            if (user && user.id) { state.userId = user.id; saveUserId(user.id); }
            saveName(name);
            el['btn-name'].textContent = 'Continuar';
            showRoomSelection();
        }).catch(function (err) {
            setHint(el['hint-name'], 'Não foi possível salvar seu nome: ' + err.message);
            el['btn-name'].disabled = false;
            el['btn-name'].textContent = 'Continuar';
        });
    }

    /* ---------------- screen 2: rooms ---------------- */
    function showRoomSelection() {
        disconnect();
        el['who-name'].textContent = state.username;
        el.avatar.textContent = initials(state.username);
        showScreen('screen-rooms');
        el['input-room'].value = '';
        setHint(el['hint-room'], '');
        loadRoomsFromServer();
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

        apiFetch(API.rooms, { method: 'GET' })
            .then(function (rooms) {
                state.rooms = normalizeRooms(rooms);
                renderRoomList();
            })
            .catch(function (err) {
                list.innerHTML = '';
                el['rooms-empty'].hidden = true;
                setHint(el['hint-room'], 'Não foi possível carregar as salas: ' + err.message);
            });
    }

    function renderRoomList() {
        var list = el['room-list'];
        list.innerHTML = '';
        state.rooms.forEach(function (room) {
            var li = document.createElement('li');
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'room-item';
            btn.innerHTML = '<span class="hash">#</span><span class="rn"></span><span class="go">&#8594;</span>';
            btn.querySelector('.rn').textContent = room;
            btn.addEventListener('click', function () { joinRoom(room); });
            li.appendChild(btn);
            list.appendChild(li);
        });
        el['rooms-empty'].hidden = state.rooms.length > 0;
    }

    function submitRoom(e) {
        e.preventDefault();
        var room = sanitizeRoom(el['input-room'].value);
        if (!room) { setHint(el['hint-room'], 'Informe um nome de sala válido.'); return; }
        setHint(el['hint-room'], '');

        var btn = el['form-room'].querySelector('button[type="submit"]');
        if (btn) btn.disabled = true;

        apiFetch(API.rooms, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: room })
        }).then(function () {
            // Refresh from the server so the list matches backend truth (and picks up the new id).
            return apiFetch(API.rooms, { method: 'GET' });
        }).then(function (rooms) {
            state.rooms = normalizeRooms(rooms);
            if (btn) btn.disabled = false;
            joinRoom(room);
        }).catch(function (err) {
            if (btn) btn.disabled = false;
            setHint(el['hint-room'], 'Não foi possível criar a sala: ' + err.message);
        });
    }

    /* ---------------- screen 3: chat ---------------- */
    function formatSentAt(iso) {
        if (!iso) return nowTime();
        var d = new Date(iso);
        if (isNaN(d.getTime())) return nowTime();
        return d.toTimeString().slice(0, 8);
    }

    function joinRoom(room) {
        state.room = room;
        el['chat-title'].textContent = '# ' + room;
        el['chat-meta'].textContent = 'como ' + state.username;
        el.messages.innerHTML = '';
        showScreen('screen-chat');
        el['input-msg'].focus();
        loadHistory(room);
    }

    function loadHistory(room) {
        apiFetch(API_BASE + '/v1/rooms/' + encodeURIComponent(room) + '/messages', { method: 'GET' })
            .then(function (msgs) {
                if (state.room !== room) return;          // user switched rooms mid-load
                (msgs || []).forEach(function (m) {
                    renderMessage({
                        senderId: m.senderId || null,
                        from: m.from || 'anon',
                        content: m.content || '',
                        sentAt: formatSentAt(m.sentAt)
                    });
                });
            })
            .catch(function (err) {
                if (state.room !== room) return;
                renderNotice('Não foi possível carregar o histórico: ' + err.message, 'error');
            })
            .then(function () {
                // Subscribe AFTER history renders, so live messages append below it in order.
                if (state.room !== room) return;
                if (state.connected) subscribeToRoom(room);
                else connect();
            });
    }

    function setStatus(stateName, text) {
        el.status.setAttribute('data-state', stateName);
        el['status-text'].textContent = text;
    }

    function connect() {
        setStatus('connecting', 'conectando…');
        try {
            var socket = new SockJS(WS_URL);
            state.client = Stomp.over(socket);
            state.client.debug = null;
            state.client.connect({}, onConnected, onConnectError);
        } catch (err) {
            onConnectError(err);
        }
    }

    function onConnected() {
        state.connected = true;
        setStatus('online', 'online');
        subscribeToRoom(state.room);
    }

    function onConnectError(err) {
        state.connected = false;
        state.subscription = null;
        setStatus('error', 'offline');
        renderNotice('Não foi possível conectar ao servidor. ' +
            'Verifique se o backend está rodando e tente novamente.', 'error');
    }

    function subscribeToRoom(room) {
        if (!state.client || !state.connected) return;
        if (state.subscription) {
            try { state.subscription.unsubscribe(); } catch (e) {}
            state.subscription = null;
        }
        state.subscription = state.client.subscribe(TOPIC(room), onFrame);
        renderNotice('Você entrou em #' + room);
    }

    function onFrame(frame) {
        var m;
        try { m = JSON.parse(frame.body); } catch (e) { return; }
        renderMessage({
            senderId: m.senderId || null,
            from: m.from || 'anon',
            content: m.content || '',
            sentAt: formatSentAt(m.sentAt)
        });
    }

    function disconnect() {
        if (state.subscription) {
            try { state.subscription.unsubscribe(); } catch (e) {}
            state.subscription = null;
        }
        if (state.client) {
            try { state.client.disconnect(); } catch (e) {}
            state.client = null;
        }
        state.connected = false;
        setStatus('offline', 'offline');
    }

    function sendMessage(e) {
        if (e) e.preventDefault();
        var content = el['input-msg'].value.trim();
        if (!content) return;
        if (!state.client || !state.connected) {
            renderNotice('Você está offline — mensagem não enviada.', 'error');
            return;
        }
        if (!state.userId) {
            renderNotice('Sessão sem ID de usuário. Volte e informe seu nome novamente.', 'error');
            return;
        }
        state.client.send(DEST(state.room), {}, JSON.stringify({
            senderId: state.userId,
            content: content
        }));
        el['input-msg'].value = '';
        el['input-msg'].focus();
    }

    /* ---------------- rendering ---------------- */
    function isAtBottom() {
        var m = el.messages;
        return m.scrollHeight - m.scrollTop - m.clientHeight < 80;
    }
    function scrollToLatest(force) {
        var m = el.messages;
        if (force || isAtBottom()) m.scrollTop = m.scrollHeight;
    }

    function renderMessage(msg) {
        var mine = (msg.senderId && state.userId)
            ? msg.senderId === state.userId
            : msg.from === state.username;   // fallback if id absent
        var stick = isAtBottom() || mine;

        var wrap = document.createElement('div');
        wrap.className = 'msg' + (mine ? ' msg--mine' : '');

        var head = document.createElement('div');
        head.className = 'msg-head';
        var from = document.createElement('span');
        from.className = 'msg-from';
        from.textContent = mine ? 'você' : msg.from;
        var time = document.createElement('span');
        time.className = 'msg-time';
        time.textContent = msg.sentAt;
        head.appendChild(from);
        head.appendChild(time);

        var bubble = document.createElement('div');
        bubble.className = 'bubble';
        bubble.textContent = msg.content;

        wrap.appendChild(head);
        wrap.appendChild(bubble);
        el.messages.appendChild(wrap);
        scrollToLatest(stick);
    }

    function renderNotice(text, kind) {
        var stick = isAtBottom();
        var n = document.createElement('div');
        n.className = 'notice';
        if (kind) n.setAttribute('data-kind', kind);
        n.textContent = text;
        el.messages.appendChild(n);
        scrollToLatest(stick);
    }

    /* ---------------- boot ---------------- */
    function cacheEls() {
        ['screen-login', 'screen-name', 'screen-rooms', 'screen-chat', 'form-name', 'input-name', 'hint-name',
            'btn-name', 'avatar', 'who-name', 'btn-change-name', 'room-list', 'rooms-empty',
            'form-room', 'input-room', 'hint-room', 'btn-back', 'chat-title', 'chat-meta',
            'status', 'status-text', 'messages', 'form-msg', 'input-msg', 'btn-send']
            .forEach(function (id) { el[id] = $(id); });
    }

    function bindEvents() {
        el['form-name'].addEventListener('submit', submitName);
        el['input-name'].addEventListener('input', validateName);
        el['btn-change-name'].addEventListener('click', showUsernameScreen);
        el['form-room'].addEventListener('submit', submitRoom);
        el['btn-back'].addEventListener('click', showRoomSelection);
        el['form-msg'].addEventListener('submit', sendMessage);
        window.addEventListener('beforeunload', disconnect);
    }

    function initializeApp() {
        cacheEls();
        bindEvents();
        state.username = loadName();
        state.userId = loadUserId();
        if (state.username && state.userId) showRoomSelection();
        else showLoginScreen();
    }

    document.addEventListener('DOMContentLoaded', initializeApp);
})();