create table if not exists blueprints (
    uuid                varchar(36) primary key,
    name                varchar(255),
    display_name        varchar(255),
    description         text,
    created_at          timestamp,
    updated_at          timestamp
);

create table if not exists blueprint_repositories (
    uuid                    varchar(36) primary key,
    external_identifier     varchar(255),
    name                    varchar(255),
    description             text,
    manifest_root_path      text,
    descriptor_template_path text,
    remote_url_http         text,
    remote_url_ssh          text,
    default_branch          varchar(255),
    provider_type           varchar(255),
    provider_base_url       text,
    owner_id                varchar(255),
    owner_type              varchar(255),
    blueprint_uuid          varchar(36) references blueprints(uuid) on delete cascade
);

create table if not exists blueprint_versions (
    uuid                varchar(36) primary key,
    blueprint_uuid      varchar(36) references blueprints (uuid) on delete cascade,
    name                varchar(255),
    description         text,
    tag                 varchar(255),
    spec                varchar(255),
    spec_version        varchar(255),
    version_number      varchar(255),
    content             jsonb,
    created_by          varchar(255),
    updated_by          varchar(255),
    created_at          timestamp,
    updated_at          timestamp
);