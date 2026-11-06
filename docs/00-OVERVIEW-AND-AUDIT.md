# StudyMate: Общий технический аудит и матрица готовности проекта

> **Цель проекта:** Создание платформы типа NotebookLLM для студентов университетов (в частности, FIT ČVUT), позволяющей загружать учебные материалы (PDF, PPTX лекции, семинары), индексировать их в PostgreSQL с расширением `pgvector` (HNSW индекс) и вести интерактивный RAG-диалог по материалам курса или конкретного документа, а также генерировать персонализированные тесты и анализировать слабые места студента с помощью автономного AI-агента.

> **Актуальность:** документ содержит исходный аудит проекта. Строка retrieval и его зависимости обновлены на 2026-11-05 после реализации базового поиска; оценки остальных модулей относятся к исходному аудиту. Текущий HTTP-контракт поиска описан в [P0-RAG-PIPELINE-AND-RETRIEVAL.md](P0-RAG-PIPELINE-AND-RETRIEVAL.md#реализованный-http-api-поиска).

---

## 1. Архитектурный срез кодовой базы

Проект реализован как модульный монолит на стеке:
- **Язык и среда:** Kotlin 2.0.20, JVM 21.
- **Фреймворк:** Spring Boot 3.3.4.
- **База данных:** PostgreSQL 16 с расширением `pgvector:pg16`.
- **Миграции и доступ к данным:** Liquibase, jOOQ 9.0 (генерация типобезопасных классов из схемы БД).
- **Очереди и кэш:** Apache Kafka 3.8.0, Redis 7 (Alpine).
- **Связь модулей:** In-process вызовы через API-интерфейсы + асинхронные события через Kafka.

### Структура модулей (`backend/modules/`)

```mermaid
graph TD
    App[":app (Spring Boot Entrypoint)"] --> IAM[":iam"]
    App --> Course[":course"]
    App --> Storage[":storage"]
    App --> Ingestion[":ingestion"]
    App --> Retrieval[":retrieval"]
    App --> AI[":ai"]
    App --> Chat[":chat (каркас)"]
    App --> Quiz[":quiz (каркас)"]

    Course --> Storage
    Course --> Common[":common"]
    Ingestion --> Course
    Ingestion --> Storage
    Ingestion --> Retrieval
    Ingestion --> AI
    Retrieval --> AI
    Retrieval --> Common
    Retrieval --> Course
    Retrieval --> IAM
    Chat -.-> Retrieval
    Chat -.-> AI
    Quiz -.-> Retrieval
    Quiz -.-> AI
```

---

## 2. Матрица готовности модулей

| Модуль | Назначение | Статус | Что реализовано | Что отсутствует / Требует доработки |
| :--- | :--- | :---: | :--- | :--- |
| **`:iam`** | Аутентификация и пользователи | **100%** | Регистрация, логин, JWT в HttpOnly cookies, сессии в БД, `Role` (USER, ADMIN), тесты. | Готово к интеграции. |
| **`:course`** | Управление курсами и материалами | **90%** | CRUD курсов, загрузка материалов, проверка квот пользователя (100MB), Tika валидация типов. | Заполнение `page_count`, учет структуры слайдов/страниц. |
| **`:storage`** | Хранилище бинарных файлов | **95%** | `LocalFileStorage` по путям `user_{id}/course_{id}/...`. | Поддержка S3/MinIO при переносе в облако. |
| **`:ingestion`** | Асинхронный конвейер обработки | **70%** | Kafka pipeline (Upload -> Parse -> Chunk -> Embed -> Index), идемпотентность, Redis трансляция статусов. | Пакетный эмбеддинг (batching), сохранение номеров страниц/слайдов, SSE-контроллер для UI. |
| **`:retrieval`** | Векторная база и поиск | **Базовый поиск готов** | Таблица `retrieval_chunks`, HNSW индекс, атомарная переиндексация, `RetrievalLookup`, поиск по курсу и материалу с проверкой владельца, HTTP API, модульные и интеграционные тесты. | Гибридный поиск, реранкинг, метаданные страниц/слайдов и обработка длинных вопросов остаются дальнейшими этапами. |
| **`:ai`** | Интеграция с LLM и эмбеддингами | **40%** | Самописные клиенты для Ollama (bge-m3), Groq, Anthropic с ретраями. | **Spring AI не подключен**, нет поддержки MCP, нет ReAct-агента, нет валидации структурированного JSON. |
| **`:chat`** | RAG-диалог со студентом | **0%** | Пустые папки с `.gitkeep`. | Схема БД для истории чата, контекстное переписывание запросов (Query Rewriting), SSE стриминг токенов, цитирование источников. |
| **`:quiz`** | Тестирование и оценка знаний | **0%** | Пустые папки с `.gitkeep`, один черновой промпт в `:ai`. | Схема БД квизов и попыток сдачи, логика начисления баллов, связка с материалами курса. |

---

## 3. Архитектурная карта приоритетов реализации

Все недостающие элементы разделены на три последовательных этапа по степени критичности:

```mermaid
flowchart TD
    subgraph P0["Приоритет 0: Ядро RAG и Поиск (Файл: P0-RAG-PIPELINE-AND-RETRIEVAL.md)"]
        P0_1["1. Сохранение структуры слайдов/страниц (PDF/PPTX)"]
        P0_2["2. Расширение таблицы retrieval_chunks (metadata jsonb)"]
        P0_3["3. Реализация RetrievalService (similarity search в pgvector)"]
        P0_4["4. Пакетная отправка (batching) в EmbeddingConsumer"]
        P0_1 --> P0_2 --> P0_3
    end

    subgraph P1["Приоритет 1: Spring AI, MCP и Learning Agent (Файл: P1-SPRING-AI-MCP-AND-LEARNING-AGENT.md)"]
        P1_1["1. Миграция на Spring AI BOM и mcp-server-webmvc"]
        P1_2["2. Инструменты MCP: lookupCourseMaterial & getQuizResults"]
        P1_3["3. ReAct Learning Agent (анализ паттернов ошибок)"]
        P1_4["4. Структурированный JSON (LearningRecommendationReport) + Валидация"]
        P1_5["5. Интеграционные тесты и защита от межпользовательского доступа (IDOR)"]
        P1_1 --> P1_2 --> P1_3 --> P1_4 --> P1_5
    end

    subgraph P2["Приоритет 2: Quiz, Chat и Realtime UI (Файл: P2-QUIZ-SYSTEM-AND-INTERACTIVE-CHAT.md)"]
        P2_1["1. Модуль :quiz (схема БД, попытки, аналитика ошибок)"]
        P2_2["2. Модуль :chat (сессии, сообщения, контекстное переписывание)"]
        P2_3["3. SSE стриминг ответов LLM в веб-интерфейс"]
        P2_4["4. SSE стриминг статусов парсинга документов из Redis Pub/Sub"]
        P2_1 --> P2_2 --> P2_3 --> P2_4
    end

    P0 --> P1 --> P2
```

---

## 4. Ссылки на детальные документы

- **[P0-RAG-PIPELINE-AND-RETRIEVAL.md](file:///Users/test/IdeaProjects/studymate/docs/P0-RAG-PIPELINE-AND-RETRIEVAL.md)** — Детальный план доработки RAG: слайдовый парсинг, схема `pgvector`, реализация `RetrievalService`, гибридный поиск (BM25 + Dense Vector), реранкинг и устранение узких мест в Kafka Ingestion.
- **[P1-SPRING-AI-MCP-AND-LEARNING-AGENT.md](file:///Users/test/IdeaProjects/studymate/docs/P1-SPRING-AI-MCP-AND-LEARNING-AGENT.md)** — Интеграция Spring AI, развертывание MCP сервера по протоколу JSON-RPC (HTTP/SSE), ReAct-агент, генерация рекомендаций, разграничение скоупов (User/Course), устойчивость к сбоям LLM.
- **[P2-QUIZ-SYSTEM-AND-INTERACTIVE-CHAT.md](file:///Users/test/IdeaProjects/studymate/docs/P2-QUIZ-SYSTEM-AND-INTERACTIVE-CHAT.md)** — Реализация модулей `quiz` и `chat` с нуля: схемы таблиц Liquibase, многошаговый диалог, цитаты со ссылками на слайды и потоковая передача данных (SSE).
