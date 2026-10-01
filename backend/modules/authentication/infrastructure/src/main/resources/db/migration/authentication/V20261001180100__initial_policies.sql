-- The policies a system starts with: the table in ADR-078, as version 1 of each purpose, in force.
--
-- Written as data rather than created by code at startup, so there is no moment after a deploy when
-- signing in has no policy to be judged by. An administrator changes them by adding versions
-- (ADR-078); this file is never edited, and the spec InitialPoliciesIntegrationSpec reads these rows back through
-- the domain, so a value that falls outside today's bounds fails a build.

insert into authentication.policy_versions (purpose, number, attempt_lifetime_millis, max_failures, first_delay_millis, created_at)
values ('registration', 1, 300000, 5, 1000, now()),
       ('login', 1, 300000, 5, 1000, now()),
       ('admin_login', 1, 300000, 5, 1000, now()),
       ('step_up', 1, 300000, 5, 1000, now());

-- Every purpose starts with Google, for every account.
insert into authentication.policy_version_steps (purpose, number, position, necessity)
values ('registration', 1, 1, 'always'),
       ('login', 1, 1, 'always'),
       ('admin_login', 1, 1, 'always'),
       ('step_up', 1, 1, 'always');

insert into authentication.policy_version_step_kinds (purpose, number, position, kind)
values ('registration', 1, 1, 'google'),
       ('login', 1, 1, 'google'),
       ('admin_login', 1, 1, 'google'),
       ('step_up', 1, 1, 'google');

-- Then, as a second step, TOTP or a recovery code: from whoever turned it on for login and step-up,
-- and from every administrator without exception.
insert into authentication.policy_version_steps (purpose, number, position, necessity)
values ('login', 1, 2, 'when_enrolled'),
       ('admin_login', 1, 2, 'always'),
       ('step_up', 1, 2, 'when_enrolled');

insert into authentication.policy_version_step_kinds (purpose, number, position, kind)
values ('login', 1, 2, 'totp'),
       ('login', 1, 2, 'recovery_code'),
       ('admin_login', 1, 2, 'totp'),
       ('admin_login', 1, 2, 'recovery_code'),
       ('step_up', 1, 2, 'totp'),
       ('step_up', 1, 2, 'recovery_code');

insert into authentication.policy_activations (purpose, number, activated_at)
values ('registration', 1, now()),
       ('login', 1, now()),
       ('admin_login', 1, now()),
       ('step_up', 1, now());
