ALTER TABLE finmate_synthetic_public_profile
    ADD COLUMN owner_user_id UUID UNIQUE REFERENCES finmate_user(id) ON DELETE SET NULL;
