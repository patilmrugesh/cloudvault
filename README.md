# ☁️ CloudVault

> **Encrypted. Deduplicated. Unstoppable.**  
> A production-grade cloud file storage system built with Spring Boot, MinIO, PostgreSQL, and React.

---

## What is CloudVault?

CloudVault is a self-hosted cloud storage backend that takes file storage seriously. Every file is split into chunks, encrypted with AES-256-GCM before it ever touches disk, deduplicated at the chunk level using SHA-256 hashing, and reassembled in parallel on download. Share files securely with signed URLs. Resume interrupted uploads from exactly where they left off.

No vendor lock-in. No plaintext at rest. No wasted storage.

---

## Features

### 🔐 AES-256-GCM Encrypted Storage
Every chunk is encrypted with a unique IV before hitting MinIO. The encryption key lives in PostgreSQL, never in the object store. Losing MinIO access means an attacker gets ciphertext — nothing more.

### ⚡ Parallel Chunked Transfer
Files are split into 2 MB chunks and uploaded or downloaded concurrently using a `CompletableFuture` thread pool. Large files transfer significantly faster than single-stream approaches.

### 🔗 Secure Signed Share URLs
Generate a time-limited share link for any file you own. Links expire automatically. Add an optional BCrypt-hashed password for a second layer of access control. Revoke any link instantly — no waiting for expiry.

```
POST /api/share/create       → { shareUrl, ttlMinutes }
GET  /api/share/{token}      → file download (public, no auth needed)
DELETE /api/share/{token}    → revoke immediately
```

### ♻️ SHA-256 Chunk-Level Deduplication
Two users uploading the same 4 GB file? MinIO stores it once. Deduplication works at the chunk level — even files that are 80% identical share the chunks they have in common. Reference counting ensures nothing is deleted until every file pointing to it is gone.

```
Same file, different name      → 1 copy stored  ✅
Same file, different user      → 1 copy stored  ✅
File with 1 byte changed       → only that chunk is re-stored
```

### ⏸️ Resumable Uploads
Upload interrupted at 78%? No problem. Resume from the exact chunk that failed. The backend tracks session state in PostgreSQL — chunks already received are idempotent and safe to skip. Sessions stay alive for 48 hours.

```
POST /api/files/upload/init              → sessionId, totalChunks
PUT  /api/files/upload/chunk             → upload one chunk (idempotent)
GET  /api/files/upload/{id}/status       → which chunks are done
POST /api/files/upload/{id}/commit       → finalise the file
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3, Java 17 |
| Database | PostgreSQL (JPA / Hibernate) |
| Object Storage | MinIO (S3-compatible) |
| Encryption | AES-256-GCM (javax.crypto) |
| Auth | Spring Security + JWT |
| Frontend | React, TypeScript |
| Build | Maven |

---

## Architecture

```
Client (React TSX)
       │
       ▼
Spring Boot REST API
       │
       ├── EncryptionService   (AES-256-GCM, per-chunk IV)
       │
       ├── DeduplicationService (SHA-256 hash → chunk_hashes table)
       │
       ├── ResumableUploadService (session state in upload_sessions)
       │
       ├── ShareService        (signed tokens in share_tokens)
       │
       └── MinioService        (storeAtKey / getAtKey / deleteAtKey)
              │
              ▼
           MinIO
        (encrypted chunks at dedup/<sha256>)
              │
              ▼
         PostgreSQL
  (metadata, keys, sessions, tokens)
```

---

## Database Schema

```
users               — accounts
file_metadata       — file records, encryption keys, owner
share_tokens        — signed share URLs with TTL and password hash
chunk_hashes        — SHA-256 → MinIO object key + reference count
file_chunk_map      — file + chunkIndex → chunk hash (dedup index)
upload_sessions     — resumable upload state, uploaded chunk tracking
```

> `spring.jpa.hibernate.ddl-auto=update` creates all tables automatically on first boot.

---

## Getting Started

### Prerequisites
- Java 17+
- Maven
- PostgreSQL 14+
- MinIO (or any S3-compatible store)

### 1. Clone the repo
```bash
git clone https://github.com/YOUR_USERNAME/cloudvault.git
cd cloudvault
```

### 2. Create the database
```sql
CREATE DATABASE cloudvault;
```

### 3. Start MinIO
```bash
minio.exe server C:\minio-data --console-address ":9001"
# Console → http://localhost:9001
# Create a bucket named: cloudvault
```

### 4. Configure application.properties
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/cloudvault
spring.datasource.username=postgres
spring.datasource.password=yourpassword

minio.url=http://127.0.0.1:9000
minio.accessKey=minioadmin
minio.secretKey=minioadmin
minio.bucket=cloudvault

app.base-url=http://localhost:8080
app.dedup-key=YOUR_BASE64_AES_256_KEY
```

Generate the dedup key (PowerShell):
```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Max 256) }))
```

### 5. Run
```bash
mvn spring-boot:run
```

---

## API Reference

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login, receive JWT |

### Files
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/files/upload` | Standard encrypted upload |
| GET | `/api/files/download/{id}` | Download and decrypt |
| GET | `/api/files/list` | List your files |

### Share Links
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/share/create` | Generate signed URL |
| GET | `/api/share/{token}` | Public download via link |
| DELETE | `/api/share/{token}` | Revoke a link |

### Resumable Uploads
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/files/upload/init` | Start a session |
| PUT | `/api/files/upload/chunk` | Upload one chunk |
| GET | `/api/files/upload/{id}/status` | Check progress |
| POST | `/api/files/upload/{id}/commit` | Finalise upload |

---

## Security Design

**Encryption** — AES-256-GCM with a fresh 96-bit IV per chunk. The IV is prepended to the ciphertext so no extra DB column is needed. Auth tags prevent silent corruption.

**Share tokens** — 48 bytes of `SecureRandom` output encoded as URL-safe Base64. 2^384 possible values. Brute force is not a realistic attack. Tokens are validated against expiry, revocation, and an optional BCrypt password on every request.

**Deduplication key separation** — Deduplicated chunks use a global key; per-file metadata keys are separate. A compromised dedup key does not expose per-user encryption keys.

**Ownership checks** — Every file operation validates that the requesting user owns the file before any work is done.

---

## Project Structure

```
src/main/java/com/cloudvault/
├── controller/
│   ├── FileController.java
│   ├── ShareController.java
│   └── ResumableUploadController.java
├── service/
│   ├── FileService.java
│   ├── EncryptionService.java
│   ├── MinioService.java
│   ├── ShareService.java
│   ├── DeduplicationService.java
│   └── ResumableUploadService.java
├── model/
│   ├── User.java
│   ├── FileMetadata.java
│   ├── ShareToken.java
│   ├── ChunkHash.java
│   ├── FileChunkMap.java
│   └── UploadSession.java
└── repository/
    ├── UserRepository.java
    ├── FileRepository.java
    ├── ShareTokenRepository.java
    ├── ChunkHashRepository.java
    ├── FileChunkMapRepository.java
    └── UploadSessionRepository.java
```

---

## Interview Talking Points

If you're using this project in campus placements, these are the strongest angles:

**On encryption:** "Each chunk gets a unique IV — reusing IVs in GCM mode is catastrophic because it breaks both confidentiality and the authentication tag. I generate a fresh IV per chunk using SecureRandom and prepend it to the ciphertext so decrypt always has it without a separate DB column."

**On deduplication:** "SHA-256 is computed over plaintext before encryption. Two users uploading the same file produce the same hash regardless of their individual keys. The dedup key encrypts the canonical stored copy. Reference counting ensures MinIO objects are only deleted when no file points to them anymore."

**On resumable uploads:** "The protocol is idempotent at the chunk level — if a chunk upload succeeds but the client crashes before receiving the 200, it can safely retry. The backend checks the uploaded chunk set and skips duplicates. On reconnect, the client calls /status to get the exact set of missing indices and resumes from there."

**On parallel transfer:** "CompletableFuture.allOf() lets all chunks fly in parallel. I snapshot the entity fields — encryptionKey, totalChunks — on the main thread before handing work to the executor pool. Accessing Hibernate-managed fields from async threads risks detached-entity exceptions if the session closes."

---

## License

MIT — use it, fork it, learn from it.

---

<p align="center">Built with Java, grit, and an unreasonable amount of attention to chunk boundaries.</p>
