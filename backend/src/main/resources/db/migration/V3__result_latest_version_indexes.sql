CREATE INDEX idx_github_analyses_user_version_created_at
    ON github_analyses (user_id, version DESC, created_at DESC);

CREATE INDEX idx_capability_diagnoses_user_version_created_at
    ON capability_diagnoses (user_id, version DESC, created_at DESC);

CREATE INDEX idx_learning_roadmaps_user_version_created_at
    ON learning_roadmaps (user_id, version DESC, created_at DESC);
