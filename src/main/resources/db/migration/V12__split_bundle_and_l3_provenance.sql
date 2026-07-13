ALTER TABLE finmate_dataset_release
    RENAME COLUMN source_commit TO bundle_source_commit;

ALTER TABLE finmate_dataset_release
    ADD COLUMN l3_source_commit VARCHAR(40),
    ADD COLUMN l3_tree_sha256 VARCHAR(64);

-- A payload-derived natural key cannot reconcile the corrected snapshot with older L3 rows.
-- Remove the stale synthetic snapshot before recording the corrected provenance; the importer
-- repopulates all runtime L3 tables atomically on the next load.
DELETE FROM finmate_import_l3_record
WHERE release_version = 'v1.0.0';

UPDATE finmate_dataset_release
SET bundle_source_commit = '63ca3d046eba9ec510e377a28a0083233aefff61',
    l3_source_commit = '22243bce34131737fc762675f0817ead08bc165a',
    l3_tree_sha256 = 'dd30ae91f5e517f2a502ce46b2dfe3666107562e2dc52edb25fec48b893c3c33'
WHERE release_version = 'v1.0.0';

ALTER TABLE finmate_dataset_release
    ALTER COLUMN bundle_source_commit SET NOT NULL,
    ALTER COLUMN l3_source_commit SET NOT NULL,
    ALTER COLUMN l3_tree_sha256 SET NOT NULL,
    ADD CONSTRAINT finmate_dataset_release_bundle_commit_format
        CHECK (bundle_source_commit ~ '^[0-9a-f]{40}$'),
    ADD CONSTRAINT finmate_dataset_release_l3_commit_format
        CHECK (l3_source_commit ~ '^[0-9a-f]{40}$'),
    ADD CONSTRAINT finmate_dataset_release_l3_tree_sha256_format
        CHECK (l3_tree_sha256 ~ '^[0-9a-f]{64}$');
