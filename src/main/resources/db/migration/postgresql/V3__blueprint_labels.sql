create table if not exists blueprints_labels (
    uuid                varchar(36) primary key,
    name                varchar(255),
    description         text,
    color               varchar(32),
    label_group         varchar(255),
    created_at          timestamp,
    updated_at          timestamp
);

create table if not exists blueprints_labels_rel (
    blueprint_uuid      varchar(36) not null references blueprints(uuid) on delete cascade,
    label_uuid          varchar(36) not null references blueprints_labels(uuid) on delete cascade,
    primary key (blueprint_uuid, label_uuid)
);
