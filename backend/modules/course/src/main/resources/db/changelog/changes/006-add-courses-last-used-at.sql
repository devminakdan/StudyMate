--liquibase formatted sql
--changeset qqrayzqq:course-006-add-courses-last-used-at
ALTER TABLE courses ADD COLUMN last_used_at timestamptz;
