alter table blueprints add column if not exists blueprint_type varchar(32) not null default 'BLUEPRINT';
