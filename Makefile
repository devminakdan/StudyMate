.PHONY: up down build run migrate jooq jooq-iam clean

up:
	docker compose up -d

down:
	docker compose down

down-v:
	docker compose down -v

build:
	cd backend && ./gradlew build -x test

clean:
	cd backend && ./gradlew clean

run:
	cd backend && ./gradlew :app:bootRun

# --- Database ---
migrate: up
	cd backend && ./gradlew :app:bootRun &
	@echo "Waiting for migrations to apply..."
	@sleep 20
	@kill $$(lsof -ti:8080) 2>/dev/null || true
	@echo "Migrations applied."

# --- jOOQ codegen ---
jooq-iam:
	cd backend && ./gradlew :iam:generateJooq

jooq-all:
	cd backend && ./gradlew generateJooq

# --- Shortcut: migrate + regenerate jOOQ for iam ---
db-update: migrate jooq-iam
	@echo "Migration applied and jOOQ regenerated."

# --- Logs ---
db-tables:
	docker exec studymate-postgres psql -U postgres -d studymate -c "\dt"

db-psql:
	docker exec -it studymate-postgres psql -U postgres -d studymate
