--liquibase formatted sql

--changeset devminakdan:iam-004-inline-role-into-users
ALTER TABLE users ADD COLUMN role varchar(50);

UPDATE users SET role = (
    SELECT ur.role FROM user_roles ur WHERE ur.user_id = users.id LIMIT 1
);

UPDATE users SET role = 'USER' WHERE role IS NULL;

ALTER TABLE users ALTER COLUMN role SET NOT NULL;

DROP TABLE user_roles;
