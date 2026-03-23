create table if not exists blueprints (
    uuid                varchar(36) primary key,
    name                varchar(255),
    display_name        varchar(255),
    description         text,
    created_at          timestamp default now(),
    updated_at          timestamp default now()
);
