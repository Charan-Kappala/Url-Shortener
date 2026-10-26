CREATE TABLE url_mappings (
    id            BIGSERIAL PRIMARY KEY,
    short_code    VARCHAR(10)  NOT NULL UNIQUE,
    original_url  TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by_ip VARCHAR(45),
    click_count   BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_short_code ON url_mappings(short_code);
CREATE INDEX idx_expires_at ON url_mappings(expires_at) WHERE expires_at IS NOT NULL;
