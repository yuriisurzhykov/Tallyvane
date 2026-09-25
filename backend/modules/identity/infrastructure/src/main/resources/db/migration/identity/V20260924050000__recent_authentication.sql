alter table identity.sessions
    add column reauthenticated_at timestamptz;
