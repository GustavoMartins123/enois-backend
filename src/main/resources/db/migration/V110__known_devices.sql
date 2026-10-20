CREATE TABLE IF NOT EXISTS known_devices (
    id            VARCHAR(64)  NOT NULL,
    user_id       VARCHAR(64)  NOT NULL,
    fingerprint   VARCHAR(128) NOT NULL,
    first_seen_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (user_id, fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_known_devices_user ON known_devices (user_id);
