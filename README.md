# 🌌 SpaceHub Backend

[![Java](https://img.shields.io/badge/Java-17-orange.svg?style=flat&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-KRaft-black.svg?style=flat&logo=apachekafka)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-7.x%20Pub%2FSub%20%26%20Presence-red.svg?style=flat&logo=redis)](https://redis.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20PostGIS-blue.svg?style=flat&logo=postgresql)](https://www.postgresql.org/)
[![LiveKit](https://img.shields.io/badge/LiveKit-WebRTC%20SFU-blueviolet.svg?style=flat&logo=webrtc)](https://livekit.io/)
[![Checkstyle](https://img.shields.io/badge/Checkstyle-0%20Violations-success.svg?style=flat)](https://checkstyle.sourceforge.io/)

SpaceHub is a production-grade, distributed real-time platform engineered for scalable community collaboration, low-latency audio/video conferencing, high-throughput chat streaming, geo-spatial local group discovery, and real-time presence tracking.

---

## 🏗️ System Architecture & Distributed Patterns

```
                                      ┌──────────────────────────────────────────────┐
                                      │            React 19 Client Tier              │
                                      │  (Vite + Redux + WebSockets + LiveKit WebRTC) │
                                      └───────┬──────────────┬──────────────▲────────┘
                                              │              │              │
                     REST / Presigned S3 URLs │              │ WebSockets   │ WebRTC SFU Media
                                              ▼              ▼              ▼
                                      ┌──────────────────────────────┐ ┌──────────────┐
                                      │  SpaceHub Gateway Nodes      │ │ LiveKit SFU  │
                                      │  (Spring Boot / Java 17)     │ │ (Media Node) │
                                      └───────┬──────────────┬───────┘ └──────┬───────┘
                                              │              │                │
                        ┌─────────────────────┴──────┐       │                │
                        ▼ (Fast Broadcast: < 5ms)    ▼       ▼                │
               ┌─────────────────┐       ┌───────────────────────┐            │
               │ Redis 7 Cluster │       │  Apache Kafka (KRaft) │            │
               │ - Pub/Sub Hub   │       │  - Community Chat Ingest           │
               │ - ZSET Presence │       │  - Direct Chat Ingest │            │
               │ - Token Buckets │       │  - Outbox Events CDC  │            │
               └─────────────────┘       └───────────┬───────────┘            │
                                                     │ Batch Ingestion        │
                                                     ▼ (Bulk Insert)          │
                                         ┌───────────────────────┐            │
                                         │ PostgreSQL 16 Primary │            │
                                         │ + PostGIS GIST Index  │◄───────────┘
                                         └───────────────────────┘
```

### 1. Multi-Node WebSocket Scaling (Redis Pub/Sub Backplane)
- Stateful WebSocket connections (`/chat`, `/ws/direct-chat`, `/notification`) are scaled horizontally across cluster nodes.
- When an event occurs, nodes publish a lightweight `WsRedisEnvelope` to dedicated Redis channels (`spacehub.ws.community-chat`, `spacehub.ws.direct-chat`, `spacehub.ws.notifications`).
- `WsRedisMessageSubscriber` receives the broadcast and pushes frames directly to connected local sessions.
- **Fail-Safe Fallback:** If Redis is temporarily unreachable, nodes log a warning and dispatch locally to ensure zero local connection breakage.

### 2. High-Throughput Asynchronous Ingestion (Kafka + Transactional Outbox)
- **Zero-Block Connection Handlers:** Chat messages are validated and broadcast optimistically within $<5\text{ms}$, while ingestion is offloaded to Apache Kafka (`spacehub.chat.community`, `spacehub.chat.direct`).
- **Partition FIFO Ordering:**
  - Community messages partition by `roomCode` (UUID).
  - Direct messages partition by canonical conversation hash `min(userA, userB) + ":" + max(userA, userB)`.
- **Batch Persistence:** `ChatKafkaConsumer` polls Kafka records in batches and performs bulk database inserts (`saveAll`) to reduce database round-trips by up to 95%.
- **Transactional Outbox:** Critical domain state transitions are saved to an `OutboxMessage` table in the same ACID transaction and relayed to Kafka via `OutboxPublisherService`.

### 3. Distributed Sliding-Window Presence Engine
- Eliminates database write amplification by tracking user status (`ONLINE`, `IDLE`, `OFFLINE`) in Redis Sorted Sets (`presence:active:global` and `presence:community:{id}`).
- Heartbeat timestamps serve as sorted set scores ($O(\log N)$ writes).
- A scheduled sweeper periodically evicts stale heartbeats via `ZREMRANGEBYSCORE` and publishes status transitions across cluster nodes.
- **Hybrid Fanout Strategy:**
  - *Fanout-on-Write:* Real-time delta broadcasts are pushed exclusively to mutual friends.
  - *Fanout-on-Read:* Active members in massive communities ($50,000+$ users) are queried on-demand when opening the room to prevent message storms.

### 4. Low-Latency Voice & Video Streaming (LiveKit SFU)
- WebRTC Selective Forwarding Unit (SFU) topology with dynamic simulcast layers and Dynacast downlink bandwidth conservation.
- Secure, stateless token generation (`/api/v1/voice-room/token`) with cryptographic HMAC-SHA256 JWT grants (`roomJoin`, `canPublish`, `canSubscribe`, `canPublishData`).
- Active speaker detection, voice activity detection (VAD), and native screen sharing.

### 5. Geo-Spatial Proximity Engine (PostGIS + R-Tree Indexing)
- Spatial indexing using `GEOMETRY(Point, 4326)` and R-Tree (`GIST`) indexes on local groups and coordinates.
- Sub-millisecond bounding box and radius queries via `ST_DWithin` and `ST_Distance` ($O(\log N)$ query complexity).

### 6. Security & Token-Bucket Rate Limiting
- Stateless JWT authentication filter (`JwtAuthenticationFilter`) with password version revocation.
- Distributed rate limiting with Bucket4j and Redisson atomic Lua scripts to prevent brute force and API abuse.
- Direct-to-S3 Presigned URL upload pattern (`S3Service`), preventing large binary payloads from saturating application JVM heap memory.

---

## 🛠️ Technology Stack

| Domain | Technologies |
|---|---|
| **Core Framework** | Java 17, Spring Boot 3.5.x, Spring Data JPA, Spring Security, Spring WebSocket |
| **Distributed Messaging** | Apache Kafka 3.8 (KRaft mode), Spring Kafka |
| **Caching & Pub/Sub** | Redis 7, Spring Data Redis, Redisson |
| **Database & GIS** | PostgreSQL 16, PostGIS, Hibernate Spatial |
| **Media & WebRTC** | LiveKit SFU (Go), AWS S3 SDK (Presigned Uploads/Downloads) |
| **Rate Limiting** | Bucket4j, Redisson |
| **Quality & Linting** | Checkstyle (Strict: Cyclomatic Complexity $\le 11$, NPath $\le 200$, 0 warnings) |

---

## 🚀 Getting Started

### Prerequisites
- **Java 17+** (JDK 17 or higher)
- **Docker & Docker Compose** (for Kafka, Redis, PostgreSQL, and LiveKit)
- **Maven 3.9+** (or use included `./mvnw`)

### 1. Clone & Configure Environment
```bash
git clone https://github.com/MonuChaudhary14/SpaceHub-Backend.git
cd SpaceHub-Backend
```

Create or verify `.env` / `application.properties` with your credentials:
```properties
# Server
server.port=8080

# PostgreSQL + PostGIS
spring.datasource.url=jdbc:postgresql://localhost:5432/spacehub
spring.datasource.username=postgres
spring.datasource.password=postgres

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Apache Kafka
spring.kafka.bootstrap-servers=localhost:9092

# AWS S3 (or MinIO/LocalStack)
aws.s3.bucket-name=spacehub-assets
aws.access-key=YOUR_AWS_ACCESS_KEY
aws.secret-key=YOUR_AWS_SECRET_KEY
aws.region=ap-south-1

# LiveKit SFU
livekit.api-key=devkey
livekit.api-secret=secret
livekit.url=ws://localhost:7880
```

### 2. Launch Infrastructure via Docker Compose
```bash
docker compose up -d
```
*Services started:*
- **PostgreSQL 16 + PostGIS:** `localhost:5432`
- **Redis 7:** `localhost:6379`
- **Kafka KRaft:** `localhost:9092`
- **LiveKit WebRTC SFU:** `localhost:7880`

### 3. Build & Run the Backend
```bash
# Verify Checkstyle & compile
./mvnw clean compile test-compile

# Run application
./mvnw spring-boot:run
```

---

## 👥 Pre-seeded Test Accounts

The platform automatically seeds development accounts on bootstrap with idempotent credential synchronization:

| Email | Username | Password | Global Role | Purpose |
|---|---|---|---|---|
| `monuchaudharypoonia@gmail.com` | `monuchaudhary` | `@Monu1402` | `ADMIN` | SpaceHub Founder & Lead Architect |
| `admin@spacehub.dev` | `admin` | `Password@123` | `ADMIN` | System Administrator |
| `alex.chen@spacehub.dev` | `alexchen` | `Password@123` | `USER` | Distributed Systems Engineer |
| `sarah.jenkins@spacehub.dev` | `sarahj` | `Password@123` | `USER` | Lead Product Designer |
| `dev.marcus@spacehub.dev` | `marcusdev` | `Password@123` | `USER` | WebRTC & Media Researcher |
| `demo.user@spacehub.dev` | `demouser` | `Password@123` | `USER` | General Platform Tester |

---

## 📡 Core API & WebSocket Endpoints

### REST Endpoints
- `POST /api/v1/registration` & `POST /api/v1/login` – Authentication & JWT issuance
- `POST /api/v1/presence/heartbeat` – Distributed heartbeat renewal
- `GET /api/v1/presence/users` – Multi-user bulk presence status lookup
- `GET /api/v1/presence/community/{communityId}` – Active community members query
- `POST /api/v1/voice-room/token` – WebRTC LiveKit JWT token minting
- `POST /api/v1/invites/accept` – Unified invitation acceptance handler
- `POST /api/v1/files/upload-url` – Direct-to-S3 presigned URL generator
- `GET /api/v1/local-groups/nearby` – Spatial radius-based group discovery

### WebSocket Handlers
- `ws://localhost:8080/chat?roomCode={UUID}&email={email}` – Community text channel & file chat
- `ws://localhost:8080/ws/direct-chat?senderEmail={email}&receiverEmail={email}` – 1-on-1 private messaging
- `ws://localhost:8080/notification?email={email}` – Push notification stream

---

## 🛡️ Code Quality & Checkstyle Standards

The project strictly enforces clean architecture and SOLID design principles via Maven Checkstyle:
- **Max Cyclomatic Complexity:** $\le 11$
- **Max NPath Complexity:** $\le 200$
- **Braces Placement:** `RightCurly` (`alone_or_singleline`) and `NeedBraces` enforced across all control flow blocks.
- **Top-Level Imports:** Fully qualified inline package types are prohibited in favor of clean top-level imports.

Run checkstyle audit:
```bash
./mvnw checkstyle:check
```

---

## 📄 License & Author
Built with ❤️ by **Monu Chaudhary** as part of the **SpaceHub** ecosystem.
Licensed under the [MIT License](LICENSE).
