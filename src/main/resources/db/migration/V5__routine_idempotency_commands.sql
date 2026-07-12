CREATE TABLE finmate_routine_idempotency_command (
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    operation VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    original_status INTEGER NOT NULL,
    original_body TEXT NOT NULL,
    result_build_id UUID REFERENCES finmate_routine_build(id),
    archived_build_id UUID REFERENCES finmate_routine_build(id),
    active_build_id UUID REFERENCES finmate_routine_build(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, operation, idempotency_key),
    CHECK (operation IN ('IMPORT', 'REPLACE')),
    CHECK (char_length(idempotency_key) BETWEEN 16 AND 128),
    CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CHECK (
        (operation = 'IMPORT' AND original_status = 201 AND result_build_id IS NOT NULL
            AND archived_build_id IS NULL AND active_build_id IS NULL)
        OR
        (operation = 'REPLACE' AND original_status = 200 AND result_build_id IS NULL
            AND archived_build_id IS NOT NULL AND active_build_id IS NOT NULL)
    )
);

CREATE FUNCTION finmate_reject_routine_idempotency_command_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'routine idempotency command records are immutable'
        USING ERRCODE = '23000';
END;
$$;

CREATE TRIGGER finmate_routine_idempotency_command_immutable
BEFORE UPDATE OR DELETE ON finmate_routine_idempotency_command
FOR EACH ROW EXECUTE FUNCTION finmate_reject_routine_idempotency_command_mutation();
