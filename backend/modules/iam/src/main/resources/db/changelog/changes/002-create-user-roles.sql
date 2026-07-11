--liquibase formatted sql

--changeset devminakdan:iam-002-create-user-roles
CREATE TABLE user_roles (
    user_id uuid NOT NULL,
    role varchar(50) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
