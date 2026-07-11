--liquibase formatted sql

--changeset qqrayzqq:course-004-add-materials-course-index
CREATE INDEX idx_materials_course ON materials(course_id, status);
