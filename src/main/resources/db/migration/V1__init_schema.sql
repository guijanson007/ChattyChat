CREATE TABLE users (
    id UUID NOT NULL PRIMARY KEY,
    provider_id VARCHAR(255) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255),
    display_name VARCHAR(255)
);

CREATE TABLE rooms (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_updated_at TIMESTAMP
);

CREATE TABLE messages (
    id UUID NOT NULL PRIMARY KEY,
    sender_id UUID NOT NULL REFERENCES users(id),
    room_id UUID NOT NULL REFERENCES rooms(id),
    content VARCHAR(1000) NOT NULL,
    sent_at TIMESTAMP NOT NULL
);

CREATE TABLE room_members (
    user_id UUID NOT NULL,
    room_id UUID NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, room_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (room_id) REFERENCES rooms(id)
);
