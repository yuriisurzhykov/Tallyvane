ALTER TABLE identity.pending_authentications
    ADD COLUMN requires_enrollment boolean NOT NULL DEFAULT false,
    ADD COLUMN primary_method text,
    ADD COLUMN policy_version bigint NOT NULL DEFAULT 1;

ALTER TABLE identity.pending_authentications
    ADD CONSTRAINT pending_authentications_primary_method_check
        CHECK (primary_method IS NULL OR primary_method IN ('PASSWORD', 'GOOGLE', 'EMAIL_CODE')),
    ADD CONSTRAINT pending_authentications_policy_version_check
        CHECK (policy_version > 0),
    ADD CONSTRAINT pending_authentications_enrollment_metadata_check
        CHECK (NOT requires_enrollment OR primary_method IS NOT NULL);
