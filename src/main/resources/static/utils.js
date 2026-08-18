import {STORE} from './config.js';

export function loadUserId() {
    try {
        return localStorage.getItem(STORE.userId) || null;
    } catch (e) {
        return null;
    }
}

export function saveUserId(v) {
    try {
        localStorage.setItem(STORE.userId, v);
    } catch (e) {
    }
}

export function loadName() {
    try {
        return localStorage.getItem(STORE.name) || '';
    } catch (e) {
        return '';
    }
}

export function saveName(v) {
    try {
        localStorage.setItem(STORE.name, v);
    } catch (e) {
    }
}

export function initials(name) {
    return name.trim().slice(0, 1).toUpperCase() || '?';
}

export function sanitizeRoom(v) {
    return v.trim().toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9._-]/g, '');
}

export function nowTime() {
    return new Date().toTimeString().slice(0, 8);
}

export function formatCreatedAt(iso) {
    if (!iso) return nowTime();
    const d = new Date(iso);
    if (isNaN(d.getTime())) return nowTime();
    return d.toTimeString().slice(0, 8);
}
