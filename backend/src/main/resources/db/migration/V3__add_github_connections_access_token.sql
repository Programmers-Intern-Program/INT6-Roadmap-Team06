ALTER TABLE github_connections
    ADD COLUMN access_token VARCHAR(500);

-- TODO(security): encrypt at rest. Plaintext acceptable for v1 MVP only.
