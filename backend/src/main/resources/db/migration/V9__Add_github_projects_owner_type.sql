-- Add ownerType column to github_projects
ALTER TABLE github_projects ADD COLUMN owner_type VARCHAR(50);

-- Set default value for existing records (assume they were all owner repos)
UPDATE github_projects SET owner_type = 'owner' WHERE owner_type IS NULL;

-- Make the column non-nullable
ALTER TABLE github_projects ALTER COLUMN owner_type SET NOT NULL;
