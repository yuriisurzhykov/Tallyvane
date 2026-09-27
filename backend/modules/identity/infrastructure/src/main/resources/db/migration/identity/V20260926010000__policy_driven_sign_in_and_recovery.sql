delete from identity.pending_authentications;

alter table identity.pending_authentications
    add column recommended_method text not null
        check (recommended_method in ('TOTP', 'EMAIL_OTP'));

delete from identity.authentication_policy_schemes
where required_tokens @> array['BACKUP_CODE']::text[];

update identity.authentication_policy_rules
set allowed_methods = array_remove(allowed_methods, 'BACKUP_CODE')
where allowed_methods @> array['BACKUP_CODE']::text[];

update identity.authentication_policy
set version = version + 1
where id = 1;
