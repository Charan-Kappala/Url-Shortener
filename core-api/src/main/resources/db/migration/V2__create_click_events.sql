CREATE TABLE click_events (
    id           BIGSERIAL PRIMARY KEY,
    short_code   VARCHAR(10)  NOT NULL,
    clicked_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    user_agent   TEXT,
    referer      TEXT,
    country_code VARCHAR(2),
    device_type  VARCHAR(20)
);

CREATE INDEX idx_click_short_code ON click_events(short_code);
CREATE INDEX idx_click_time       ON click_events(clicked_at);
