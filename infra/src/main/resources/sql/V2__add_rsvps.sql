ALTER TABLE groups
    ADD COLUMN location VARCHAR(4096);
ALTER TABLE groups
    ADD COLUMN location_lat DOUBLE PRECISION;
ALTER TABLE groups
    ADD COLUMN location_lng DOUBLE PRECISION;
ALTER TABLE groups
    ADD COLUMN location_country VARCHAR(30);

ALTER TABLE events
    ADD COLUMN max_attendee INT;

CREATE TABLE group_members
(
    group_id     CHAR(36)    NOT NULL REFERENCES groups (id),
    user_id      CHAR(36)    NOT NULL REFERENCES users (id),
    role         VARCHAR(10) NOT NULL, -- Owner, Member
    presentation VARCHAR(4096),
    joined_at    TIMESTAMP   NOT NULL,
    leaved_at    TIMESTAMP,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE event_rsvps
(
    event_id    CHAR(36)    NOT NULL REFERENCES events (id),
    user_id     CHAR(36)    NOT NULL REFERENCES users (id),
    answer      VARCHAR(10) NOT NULL, -- Yes, No, Wait
    answered_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (event_id, user_id)
);
