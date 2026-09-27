alter table identity.authentication_policy_audit
    drop constraint authentication_policy_audit_action_check;

alter table identity.authentication_policy_audit
    add constraint authentication_policy_audit_action_check
        check (
            action in (
                'POLICY_READ_DENIED', 'POLICY_UPDATE_DENIED', 'POLICY_UPDATE_CONFLICT',
                'POLICY_UPDATE_INVALID', 'POLICY_UPDATED'
            )
            or action ~ '^MFA_RESET:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
        );
