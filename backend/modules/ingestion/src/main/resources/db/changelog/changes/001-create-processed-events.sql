--liquibase formatted sql

--changeset studymate:ingestion-001-create-processed-events
CREATE TABLE ingestion_processed_events (
    event_id uuid PRIMARY KEY,
    processed_at timestamp with time zone NOT NULL DEFAULT now()
);
