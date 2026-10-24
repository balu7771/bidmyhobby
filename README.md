# BidMyHobby AI

BidMyHobby AI is a hobby marketplace with auction workflows, AI-assisted
catalog creation, hobby-profile chat, and passwordless email login.

## Active applications

- `apps/web`: Next.js frontend
- `services/catalog`: catalog, image processing orchestration, and OpenAI listing analysis
- `services/auction`: auction and bidding APIs
- `services/hobby-ai`: hobby profiles, AI chat/search, email authentication, and optional WhatsApp notifications

## Email login configuration

Passwordless login sends a six-digit code through SendGrid. Verify the
BidMyHobby sending domain in SendGrid, then configure these values in `.env`:

```bash
SENDGRID_API_KEY=...
SENDGRID_FROM_EMAIL=login@bidmyhobby.com
AUTH_TOKEN_SECRET=at-least-32-random-characters
ADMIN_EMAILS=admin@example.com
```

Use the same `AUTH_TOKEN_SECRET` for hobby-ai, catalog, and auction services.
Production sessions use an HttpOnly, Secure, SameSite=Strict cookie. WhatsApp
delivery remains disabled unless `WHATSAPP_ENABLED=true` is set deliberately.
- `services/api-gateway`: NGINX routing for the web and backend services
- `lambdas/image-processor`: Python Lambda for image watermarking and business-card extraction

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the complete system
architecture, deployment platforms, AWS Lambda pipelines, and component breakdown.

See [docs/REPOSITORY_STRUCTURE.md](docs/REPOSITORY_STRUCTURE.md) for the full
layout and the decisions used to create this repository.

See
[docs/CONVERSATIONAL_LISTING_WITH_GPT_5_6.md](docs/CONVERSATIONAL_LISTING_WITH_GPT_5_6.md)
for an overview of the AI-assisted conversational listing workflow.

## Prerequisites

- Docker with Docker Compose
- Java 17 (for running backend builds outside Docker)
- Node.js 20+ and npm
- Python 3.12 and `zip` for packaging the Lambda

## Configuration

Copy the safe template and provide values locally:

```bash
cp .env.example .env
```

Never commit `.env`, cloud credentials, private keys, or generated deployment
packages. The committed template contains placeholders only.

### Option A: Native local execution (No Docker required)

Run the full local stack (Frontend, Spring Boot microservices, Dev Gateway) natively:

```bash
# Start all services concurrently in one terminal
npm run dev
# OR using PowerShell directly:
.\scripts\dev.ps1

# Start each service in a dedicated PowerShell window:
npm run dev:windows
# OR:
.\scripts\dev.ps1 -SeparateWindows

# Stop all running local services and free ports:
npm run dev:stop
# OR:
.\scripts\dev.ps1 -Stop
```

### Option B: Docker Desktop (Docker Compose)

```bash
docker compose -f docker-compose.local.yml --env-file .env config
docker compose -f docker-compose.local.yml --env-file .env up --build
```

Open `http://localhost:8080`. The frontend is also directly reachable at `http://localhost:3000`.

## Build and validate

```bash
npm --prefix apps/web ci
npm --prefix apps/web run build

./services/catalog/mvnw -f services/catalog/pom.xml test
./services/auction/mvnw -f services/auction/pom.xml test
./services/hobby-ai/mvnw -f services/hobby-ai/pom.xml test

python3 -m py_compile lambdas/image-processor/lambda_function.py
```

Build OCI-compatible images from the repository root:

```bash
./scripts/build-oci-images.sh
```

The script creates a generated image archive, which is ignored by Git.

## Lambda packaging

```bash
./lambdas/image-processor/build.sh
```

The build installs Linux-compatible Pillow binaries and creates an ignored ZIP.
Deploy with Python 3.12 and handler `lambda_function.lambda_handler`.
