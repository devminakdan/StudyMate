--liquibase formatted sql

--changeset qqrayzqq:retrieval-001-create-retrieval-chunks
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE retrieval_chunks (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    material_id uuid NOT NULL,
    course_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    chunk_index integer NOT NULL,
    chunk_text text NOT NULL,
    embedding vector(1024) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT retrieval_chunks_pkey PRIMARY KEY (id),
    CONSTRAINT uq_retrieval_chunks_material_index UNIQUE (material_id, chunk_index),
    CONSTRAINT fk_retrieval_chunks_material FOREIGN KEY (material_id) REFERENCES materials(id) ON DELETE CASCADE,
    CONSTRAINT fk_retrieval_chunks_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_retrieval_chunks_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_retrieval_chunks_course ON retrieval_chunks(course_id);
CREATE INDEX idx_retrieval_chunks_material ON retrieval_chunks(material_id);
CREATE INDEX idx_retrieval_chunks_embedding_hnsw
    ON retrieval_chunks USING hnsw (embedding vector_cosine_ops);
