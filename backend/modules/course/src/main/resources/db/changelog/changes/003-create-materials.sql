--liquibase formatted sql

--changeset qqrayzqq:course-003-create-materials
CREATE TABLE materials (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL,
    original_filename varchar(500) NOT NULL,
    storage_path varchar(1000) NOT NULL,
    mime_type varchar(100) NOT NULL,
    size_bytes bigint NOT NULL,
    status varchar(50) NOT NULL DEFAULT 'PENDING',
    error_message text,
    page_count int,
    uploaded_at timestamp with time zone NOT NULL DEFAULT now(),
    processed_at timestamp with time zone,
    CONSTRAINT materials_pkey PRIMARY KEY (id),
    CONSTRAINT fk_materials_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);
