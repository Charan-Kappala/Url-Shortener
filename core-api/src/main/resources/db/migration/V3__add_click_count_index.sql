-- Ensure click_count column exists with correct default (already created in V1, this is a no-op safety index)
CREATE INDEX IF NOT EXISTS idx_url_mappings_click_count ON url_mappings(click_count);
