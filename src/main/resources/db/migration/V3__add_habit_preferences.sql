ALTER TABLE habits ADD COLUMN color VARCHAR(7) DEFAULT '#FF3434' NOT NULL;
ALTER TABLE habits ADD COLUMN default_habit BOOLEAN DEFAULT FALSE NOT NULL;

CREATE INDEX habits_user_default_idx ON habits (user_id, default_habit);
