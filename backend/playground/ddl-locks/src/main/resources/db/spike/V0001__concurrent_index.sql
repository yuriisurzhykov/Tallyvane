create table if not exists widgets (id integer not null);

create index concurrently if not exists widgets_id on widgets (id);
