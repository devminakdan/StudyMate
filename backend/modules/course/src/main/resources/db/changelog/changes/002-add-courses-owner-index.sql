--liquibase formatted sql

--changeset qqrayzqq:course-002-add-courses-owner-index
CREATE INDEX idx_courses_owner ON courses(owner_id);
