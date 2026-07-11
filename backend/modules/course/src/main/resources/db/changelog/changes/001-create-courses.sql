--liquibase formatted sql

--changeset qqrayzqq:course-001-create-courses
CREATE TABLE courses (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    name varchar(200) NOT NULL,
    code varchar(50),
    description text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT courses_pkey PRIMARY KEY (id),
    CONSTRAINT fk_courses_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);
