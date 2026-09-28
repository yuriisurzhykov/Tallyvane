alter table identity.users
    add column security_emails_enabled boolean not null default false;
