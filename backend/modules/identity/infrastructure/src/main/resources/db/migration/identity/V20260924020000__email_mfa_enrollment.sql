create table identity.email_mfa_enrollments
(
    user_id uuid primary key references identity.users(id) on delete cascade
);
