# CloudVault Backend — Secure Distributed File Storage & Document RAG System

A production-grade Spring Boot backend for a secure distributed cloud file storage and document intelligence platform. CloudVault combines **AES-256-GCM encryption**, **parallel chunked uploads/downloads**, **chunk deduplication**, **secure time-limited share links**, and an end-to-end **Retrieval-Augmented Generation (RAG)** pipeline powered by **Spring AI**, **Ollama**, and **PostgreSQL PGVector**.

---

## 🌟 Key Capabilities

### 1. Document Intelligence & Retrieval-Augmented Generation (RAG)
- **Document Text Extraction**: Automated content extraction from PDF documents using Apache PDFBox 3.x and raw text files.
- **Semantic Vector Embeddings**: Generates dense vector embeddings (768 dimensions) using Ollama with `nomic-embed-text`.
- **Vector Store with PGVector**: Stores chunk embeddings in PostgreSQL using the `pgvector` extension with **HNSW** indexing and **Cosine Distance** similarity.
- **Context-Augmented Querying**: Retrieves top-$k$ relevant document chunks filtered by `fileId` and user context to ground LLM inference.
- **Reactive Token Streaming**: Streams responses in real-time using Server-Sent Events (`text/event-stream`) with Project Reactor `Flux<String>`.
- **Three Intelligence Modes**:
  - `SUMMARY`: Instant executive summaries and topic breakdowns.
  - `DETAILED_NOTES`: In-depth analytical outlines and key takeaways.
  - `QUESTION`: Conversational question-answering grounded strictly in document content.

### 2. Persistent AI Interaction History
- **Conversation & Audit Persistence**: Automatically logs every AI request, query, generated response, and timestamp in the database via `AiHistory`.
- **Per-File & Per-User Isolation**: Secure query isolation ensuring users can only review and retrieve AI interactions for files they own or have access to.
- **Audit Retrieval API**: `GET /api/ai/documents/{fileId}/history` endpoint for loading previous conversation threads directly into the frontend.

### 3. Cryptographic & File Security
- **AES-256-GCM Authenticated Encryption**: Every uploaded file is encrypted with a unique key before persistence; zero plaintext is stored at rest.
- **Random IV per Chunk**: Cryptographically randomized Initialization Vector (IV) generated per chunk.
- **Content Deduplication**: SHA-256 chunk hashing and deduplication preventing redundant storage while maintaining individual file encryption.
- **Secure Share Links**: Cryptographically signed, expiring download links with access control.

### 4. Parallel Chunked Storage Architecture
- **Distributed Object Storage**: Powered by MinIO S3-compatible storage.
- **High-Throughput Parallel Pipeline**: Large files are segmented into configurable chunks (default: 2 MB / 8 MB) and processed concurrently using Java `CompletableFuture` and thread pools.
- **Resumable Uploads**: Robust progress tracking and chunk reassembly.

### 5. Authentication & Access Control
- **Stateless JWT Security**: Spring Security filter chain validates Bearer tokens on protected REST and SSE streaming endpoints.
- **Ownership-Enforced Authorization**: All file downloads, deletions, sharing, indexing, and AI interactions require ownership validation.

---

## 🏗️ System Architecture

```text
               +--------------------------------------------------+
               |             React + TypeScript Client            |
               +--------------------------------------------------+
                                  |                 |
                   REST (JWT / Chunk Upload)   SSE Flux (RAG Stream)
                                  v                 v
               +--------------------------------------------------+
               |              Spring Boot REST API Layer          |
               |  (SecurityConfig, AuthTokenFilter, Controllers)  |
               +--------------------------------------------------+
                    |                      |                    |
        +-----------v-----------+          |          +---------v-----------+
        |   File Service Engine |          |          | Spring AI / RAG Engine
        |  - AES-256-GCM Crypt  |          |          |  - DocumentExtraction
        |  - Parallel Chunking  |          |          |  - RagIngestionService
        |  - Deduplication      |          |          |  - RagService (Stream)
        +-----------------------+          |          |  - AiHistoryService |
                    |                      |          +---------------------+
                    v                      v                    |
        +-----------------------+ +------------------+          v
        |  MinIO Object Storage | | PostgreSQL 16+   | +--------------------+
        |  (Encrypted Chunks)   | | - Metadata       | |   Ollama Engine    |
        |                       | | - ai_history     | | - nomic-embed-text |
        |                       | | - pgvector (HNSW)| | - qwen3:8b (LLM)   |
        +-----------------------+ +------------------+ +--------------------+
```

---

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| **Java 25 / 21** | Core backend language |
| **Spring Boot 3.x** | Enterprise REST API and application framework |
| **Spring AI (2.0.1 BOM)** | Vector store and LLM integration abstraction |
| **Ollama** | Local LLM (`qwen3:8b`) & embedding model (`nomic-embed-text`) inference |
| **PostgreSQL + PGVector** | Relational metadata store & HNSW vector similarity search |
| **Apache PDFBox 3.0** | Text extraction and preprocessing from PDF documents |
| **MinIO** | High-performance distributed S3-compatible object storage |
| **Project Reactor (Flux)** | Reactive streaming for Server-Sent Events (SSE) |
| **Spring Security & JJWT** | Stateless authentication and RBAC filters |
| **AES-256-GCM** | Authenticated symmetric encryption at rest |
| **CompletableFuture** | Concurrent thread-pool chunk encryption and uploads |
| **Maven & Lombok** | Build tooling, dependency management, and boilerplate reduction |

---

## 📁 Project Structure

```text
src/main/java/com/cloudvault
├── chunking/          # Parallel chunk segmentation & reassembly
├── config/            # SecurityConfig, CorsConfig, StorageConfig
├── controller/        # REST & SSE Controllers
│   ├── AiController.java           # AI diagnostics & testing
│   ├── AuthController.java         # User registration & login
│   ├── DocumentAiController.java   # Document analysis SSE stream & history
│   ├── FileController.java         # File upload, download, metadata, share
│   └── RagController.java          # Vector indexing & RAG queries
├── dto/               # Request & response payloads (DocumentAiRequest, etc.)
├── encryption/        # AES-256-GCM cryptographic routines & key generation
├── entity/            # JPA entities (User, FileMetadata, AiHistory, etc.)
├── filter/            # JWT authentication filter
├── model/             # Domain models & enums (AiIndexStatus)
├── repository/        # Spring Data JPA repositories (AiHistoryRepository, etc.)
├── security/          # JWT token provider & security contexts
├── service/           # Core business & processing services
│   ├── AiHistoryService.java       # Interaction history persistence & queries
│   ├── AiService.java              # Direct LLM invocation
│   ├── DocumentExtractionService.java # Apache PDFBox document parsing
│   ├── FileService.java            # File lifecycle & encryption management
│   ├── RagIngestionService.java    # Text chunking & PGVector store ingestion
│   └── RagService.java             # RAG similarity retrieval & streaming synthesis
└── util/              # Common helpers
```

---

## 📡 API Reference

### 🤖 Document Intelligence & RAG Endpoints

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/ai/documents/{fileId}` | Streams analysis (`SUMMARY`, `DETAILED_NOTES`, `QUESTION`) via SSE (`text/event-stream`) and automatically persists response to history | `Bearer JWT` |
| `GET` | `/api/ai/documents/{fileId}/history` | Retrieves all past AI interactions and Q&A history for the given file | `Bearer JWT` |
| `POST` | `/api/rag/ingest/{fileId}` | Manually triggers text extraction and PGVector indexing for a file | `Bearer JWT` |
| `GET` | `/api/rag/status/{fileId}` | Checks the current AI vector indexing status (`NOT_INDEXED`, `INDEXING`, `READY`, `FAILED`) | `Bearer JWT` |

#### Example: Stream Document Analysis Request
```http
POST /api/ai/documents/42 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <your-jwt-token>
Content-Type: application/json
Accept: text/event-stream

{
  "action": "QUESTION",
  "question": "What are the primary performance metrics mentioned in this report?"
}
```

#### Example: Get Interaction History Response
```json
[
  {
    "id": 1,
    "fileId": 42,
    "username": "mrugesh",
    "action": "QUESTION",
    "question": "What are the primary performance metrics mentioned in this report?",
    "response": "The report highlights three primary metrics: 1. Upload latency reduced by 40%...",
    "createdAt": "2026-10-07T21:45:00"
  }
]
```

### 🔐 Authentication Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register new user account |
| `POST` | `/api/auth/login` | Authenticate user and receive JWT bearer token |

### 📁 File Management & Sharing Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/files/init-upload` | Initialize chunked upload session |
| `POST` | `/api/files/upload-chunk` | Upload encrypted chunk |
| `POST` | `/api/files/complete-upload` | Finalize upload, trigger reassembly & deduplication |
| `GET` | `/api/files` | List all files belonging to authenticated user |
| `GET` | `/api/files/{id}/download` | Download and stream decrypted file |
| `DELETE` | `/api/files/{id}` | Permanently delete file metadata and unreferenced chunks |
| `POST` | `/api/files/{id}/share` | Generate secure time-limited share link |

---

## ⚙️ Configuration & Setup

### 1. Prerequisites
- **Java 21 or 25**
- **Maven 3.9+**
- **PostgreSQL 16+** with the **`pgvector`** extension installed
- **MinIO Server** running locally or in Docker
- **Ollama** installed with required models

### 2. Ollama Model Setup
Ensure Ollama is running and pull the chat and embedding models:
```bash
ollama serve
ollama pull qwen3:8b
ollama pull nomic-embed-text
```

### 3. Database & PGVector Setup
Ensure the `vector` extension is created in your PostgreSQL database:
```sql
CREATE DATABASE cloudvault;
\c cloudvault;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 4. Configure `application.properties`
Set up your connection strings and credentials:
```properties
# Database & PGVector
spring.datasource.url=jdbc:postgresql://localhost:5433/cloudvault
spring.datasource.username=postgres
spring.datasource.password=yourpassword
spring.jpa.hibernate.ddl-auto=update

# MinIO Object Storage
minio.url=http://127.0.0.1:9000
minio.accessKey=minioadmin
minio.secretKey=minioadmin
minio.bucket=cloudvault

# Ollama LLM & Embeddings
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=qwen3:8b
spring.ai.ollama.chat.options.temperature=0.2
spring.ai.ollama.embedding.model=nomic-embed-text

# PGVector Index Settings
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.dimensions=768
```

### 5. Build and Run
```bash
cd cloudvault-backend
./mvnw clean spring-boot:run
```
The server will start on `http://localhost:8080`.

---

## 🐳 Docker Deployment
A standalone `Dockerfile` is provided for containerizing the backend:
```bash
docker build -t cloudvault-backend .
docker run -p 8080:8080 --name cloudvault-backend cloudvault-backend
```

---

## 👤 Author
**Mrugesh Patil**
- Backend Engineering | Distributed Systems | Applied AI & Security
