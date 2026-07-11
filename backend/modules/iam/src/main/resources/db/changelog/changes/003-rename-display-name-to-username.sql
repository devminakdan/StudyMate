--liquibase formatted sql

--changeset devminakdan:iam-003-rename-display-name-to-username
ALTER TABLE users RENAME COLUMN display_name TO username;
