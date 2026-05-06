CloudVault Backend — Secure Distributed File Storage System

A production-style Spring Boot backend for a secure distributed cloud file storage platform featuring AES-256-GCM encryption, parallel chunked uploads/downloads, JWT authentication, and MinIO object storage integration.

Built to demonstrate backend engineering concepts including:

Secure file handling
Concurrent processing
Distributed object storage
Stateless authentication
Ownership-based access control
Production-grade REST API design
Features
Secure File Storage
AES-256-GCM end-to-end encryption
Per-file unique encryption keys
Random IV generation per chunk
Zero plaintext persistence at rest
Secure metadata handling
Parallel Chunked Uploads
Files split into configurable chunks (default: 8 MB)
Concurrent upload/download pipeline using CompletableFuture
Reduced latency for large files
Asynchronous processing with thread pools
Authentication & Authorization
Stateless JWT authentication
Secure route protection with Spring Security
Ownership-based file access control
Token validation filters
Proper HTTP status handling (401, 403, 404, 500)
Object Storage Integration
MinIO object storage support
Scalable distributed storage architecture
Chunk-level storage management
Secure object retrieval
REST API Architecture
Clean layered architecture
DTO-based request/response handling
Structured exception handling
JSON-based API communication
Tech Stack
Technology	Purpose
Java	Core backend language
Spring Boot	REST API framework
Spring Security	Authentication & authorization
JWT	Stateless authentication
MinIO	Object storage
CompletableFuture	Concurrent processing
Maven	Dependency management
AES-256-GCM	Encryption
Jackson	JSON serialization
Lombok	Boilerplate reduction
System Architecture
Client
   |
   v
Spring Boot REST API
   |
   +---- Authentication Layer (JWT)
   |
   +---- Encryption Service (AES-256-GCM)
   |
   +---- Chunk Processing Engine
   |
   +---- File Metadata Management
   |
   v
MinIO Object Storage
Project Structure
src/main/java/com/cloudvault
│
├── config/              # Security & application configuration
├── controller/          # REST controllers
├── dto/                 # Request/response DTOs
├── entity/              # Database entities
├── exception/           # Custom exception handling
├── filter/              # JWT filters
├── repository/          # JPA repositories
├── security/            # Security utilities
├── service/             # Business logic
├── storage/             # MinIO integration
├── encryption/          # AES encryption utilities
├── chunking/            # Parallel chunk processing
└── util/                # Helper utilities
Core Backend Concepts
1. AES-256-GCM Encryption

Each file is encrypted before storage using AES-GCM authenticated encryption.

Security Highlights
Unique key per file
Random IV per chunk
Authenticated encryption
No plaintext stored in MinIO
Encryption keys never exposed in API responses
2. Parallel Chunk Processing

Large files are divided into chunks and processed concurrently.

Workflow
File Upload
   |
Split into chunks
   |
Encrypt chunks in parallel
   |
Upload concurrently to MinIO
   |
Store metadata
Benefits
Faster uploads/downloads
Better scalability
Improved throughput
Efficient memory usage
3. JWT Authentication Flow
User Login
   |
Generate JWT
   |
Client stores token
   |
Token attached to requests
   |
Spring Security validates JWT
   |
Access granted/denied
API Endpoints
Authentication
Register User
POST /api/auth/register
Login
POST /api/auth/login
File Operations
Upload File
POST /api/files/upload
Download File
GET /api/files/{id}/download
Get User Files
GET /api/files
Delete File
DELETE /api/files/{id}
Example Request
Login Request
{
  "email": "user@example.com",
  "password": "password"
}
Example Response
{
  "token": "jwt_token_here",
  "type": "Bearer"
}
Security Best Practices Implemented
JWT-based stateless authentication
Ownership-based authorization
Secure password hashing
Sensitive field protection
Structured exception handling
Input validation
No plaintext file storage
Proper HTTP status codes
Concurrency Model

The application uses:

CompletableFuture
ExecutorService
Fixed Thread Pool

for asynchronous chunk encryption and upload operations.

Local Setup
Prerequisites
Java 17+
Maven
MinIO server
MySQL/PostgreSQL
Clone Repository
git clone https://github.com/your-username/cloudvault-backend.git
cd cloudvault-backend
Configure Environment

Update:

src/main/resources/application.properties

Example:

spring.datasource.url=jdbc:mysql://localhost:3306/cloudvault
spring.datasource.username=root
spring.datasource.password=password

jwt.secret=your-secret-key

minio.url=http://localhost:9000
minio.access-key=minioadmin
minio.secret-key=minioadmin
minio.bucket=cloudvault
Run Application
mvn spring-boot:run

Application starts at:

http://localhost:8080
Future Improvements
File sharing support
Role-based access control
Refresh tokens
File versioning
Virus scanning
Distributed metadata service
Kubernetes deployment
Rate limiting
CDN integration
Performance Highlights
Parallel chunk transfer architecture
Concurrent encryption pipeline
Optimized large file handling
Reduced upload latency
Scalable object storage design
Learning Outcomes

This project demonstrates practical implementation of:

Spring Boot backend engineering
REST API design
Concurrent programming in Java
Encryption & security
Distributed object storage
JWT authentication
Async processing with CompletableFuture

Author

Mrugesh

Computer Engineering Student | Backend Developer | Security & Systems Enthusiast
