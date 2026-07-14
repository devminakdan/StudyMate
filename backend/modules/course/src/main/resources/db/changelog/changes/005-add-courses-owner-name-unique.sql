--liquibase formatted sql
--changeset qqrayzqq:course-005-add-owner-name-unique
ALTER TABLE courses
    ADD CONSTRAINT uq_courses_owner_id_name UNIQUE (owner_id, name);
