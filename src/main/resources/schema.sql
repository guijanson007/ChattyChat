CREATE TABLE IF NOT EXISTS USERS
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id  VARCHAR(255) NOT NULL,
    provider     VARCHAR(255) NOT NULL,
    first_name   VARCHAR(255),
    last_name    VARCHAR(255),
    email        VARCHAR(255),
    display_name VARCHAR(255),
    CONSTRAINT unique_provider_user UNIQUE (provider_id, provider)
    );

CREATE TABLE IF NOT EXISTS ROOMS
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    last_updated_at TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    is_public       BOOLEAN          DEFAULT TRUE
    );

CREATE TABLE IF NOT EXISTS MESSAGES
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id  UUID REFERENCES USERS (id) ON DELETE CASCADE,
    room_id    UUID REFERENCES ROOMS (id) ON DELETE CASCADE,
    content    TEXT NOT NULL,
    sent_at TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS ROOM_MEMBERS
(
    user_id   UUID REFERENCES USERS (id) ON DELETE CASCADE,
    room_id   UUID REFERENCES ROOMS (id) ON DELETE CASCADE,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, room_id)
    );

CREATE TABLE IF NOT EXISTS ROOM_INVITES
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id    UUID REFERENCES ROOMS (id) ON DELETE CASCADE,
    inviter_id UUID REFERENCES USERS (id) ON DELETE CASCADE,
    invitee_id UUID REFERENCES USERS (id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
    );