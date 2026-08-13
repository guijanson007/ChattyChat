export function apiFetch(url, options) {
    return fetch(url, options).then(function (res) {
        if (!res.ok) {
            return res.text().then(function (body) {
                throw new Error('HTTP ' + res.status + (body ? ': ' + body.slice(0, 200) : ''));
            });
        }
        if (res.status === 204) return null;
        return res.text().then(function (t) {
            return t ? JSON.parse(t) : null;
        });
    });
}

export function normalizeRooms(rooms) {
    if (!Array.isArray(rooms)) return [];
    return rooms.map(function (r) {
        if (typeof r === 'string') return r;
        return r && r.name ? r.name : null;
    }).filter(Boolean);
}
