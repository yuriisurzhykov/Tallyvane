create table identity.authentication_policy_schemes
(
    id              text primary key check (length(id) between 1 and 80),
    action          text not null check (action in (
        'SIGN_IN', 'CHANGE_PRIMARY_CREDENTIAL', 'MANAGE_SECOND_FACTORS'
    )),
    required_tokens text[] not null check (cardinality(required_tokens) > 0),
    assurance_rank  integer not null check (assurance_rank > 0),
    enabled         boolean not null
);

insert into identity.authentication_policy_schemes (id, action, required_tokens, assurance_rank, enabled)
select 'signin-' || lower(primary_method) || '-primary',
       'SIGN_IN',
       array[case primary_method
           when 'PASSWORD' then 'PASSWORD'
           when 'GOOGLE' then 'GOOGLE'
           when 'EMAIL_CODE' then 'EMAIL_SIGN_IN_CODE'
       end],
       1,
       true
from identity.authentication_policy_rules
where enabled;

insert into identity.authentication_policy_schemes (id, action, required_tokens, assurance_rank, enabled)
select 'signin-' || lower(r.primary_method) || '-' || lower(factor.token_kind),
       'SIGN_IN',
       array[
           case r.primary_method
               when 'PASSWORD' then 'PASSWORD'
               when 'GOOGLE' then 'GOOGLE'
               when 'EMAIL_CODE' then 'EMAIL_SIGN_IN_CODE'
           end,
           factor.token_kind
       ],
       2,
       true
from identity.authentication_policy_rules r
cross join lateral unnest(r.allowed_methods) as allowed(method)
cross join lateral (values
    ('TOTP', 'TOTP'),
    ('EMAIL_OTP', 'EMAIL_FACTOR_CODE'),
    ('BACKUP_CODE', 'BACKUP_CODE')
) as factor(legacy_kind, token_kind)
where r.enabled
  and allowed.method = factor.legacy_kind
  and (r.primary_method <> 'EMAIL_CODE'
       or factor.token_kind <> 'EMAIL_FACTOR_CODE'
       or (select advanced_acknowledged from identity.authentication_policy where id = 1));

with primary_tokens as (
    select primary_method,
           case primary_method
               when 'PASSWORD' then 'PASSWORD'
               when 'GOOGLE' then 'GOOGLE'
               when 'EMAIL_CODE' then 'EMAIL_SIGN_IN_CODE'
           end as token_kind
    from identity.authentication_policy_rules
    where enabled
)
insert into identity.authentication_policy_schemes (id, action, required_tokens, assurance_rank, enabled)
select action.slug || '-' || lower(primary_tokens.primary_method) || '-primary',
       action.kind,
       array[primary_tokens.token_kind],
       1,
       true
from primary_tokens
cross join (values
    ('change-primary', 'CHANGE_PRIMARY_CREDENTIAL'),
    ('manage-factors', 'MANAGE_SECOND_FACTORS')
) as action(slug, kind);

with primary_tokens as (
    select primary_method,
           case primary_method
               when 'PASSWORD' then 'PASSWORD'
               when 'GOOGLE' then 'GOOGLE'
               when 'EMAIL_CODE' then 'EMAIL_SIGN_IN_CODE'
           end as token_kind
    from identity.authentication_policy_rules
    where enabled
)
insert into identity.authentication_policy_schemes (id, action, required_tokens, assurance_rank, enabled)
select action.slug || '-' || lower(primary_tokens.primary_method) || '-' || lower(factor.token_kind),
       action.kind,
       array[primary_tokens.token_kind, factor.token_kind],
       2,
       true
from primary_tokens
cross join (values
    ('change-primary', 'CHANGE_PRIMARY_CREDENTIAL'),
    ('manage-factors', 'MANAGE_SECOND_FACTORS')
) as action(slug, kind)
cross join (values
    ('TOTP'),
    ('EMAIL_FACTOR_CODE'),
    ('BACKUP_CODE')
) as factor(token_kind)
where primary_tokens.token_kind <> 'EMAIL_SIGN_IN_CODE'
   or factor.token_kind <> 'EMAIL_FACTOR_CODE'
   or (select advanced_acknowledged from identity.authentication_policy where id = 1);

insert into identity.authentication_policy_schemes (id, action, required_tokens, assurance_rank, enabled)
select action.slug || '-' || lower(factor.token_kind) || '-factor',
       action.kind,
       array[factor.token_kind],
       2,
       true
from (values ('TOTP'), ('EMAIL_FACTOR_CODE'), ('BACKUP_CODE')) as factor(token_kind)
cross join (values
    ('change-primary', 'CHANGE_PRIMARY_CREDENTIAL'),
    ('manage-factors', 'MANAGE_SECOND_FACTORS')
) as action(slug, kind);
