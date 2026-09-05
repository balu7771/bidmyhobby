# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

BidMyHobby is a hobby marketplace: auction workflows, AI-assisted catalog creation (conversational listing via OpenAI), hobby-profile AI chat/search, and passwordless (email/WhatsApp OTP) login. It's a polyglot monorepo: a Next.js frontend, three Spring Boot services, an NGINX gateway, and a Python Lambda.

## Compose environments — read before touching either file

- `docker-compose.local.yml`: local coding, builds from source, debugging, integration testing. This is the one to use and edit for day-to-day dev.
- `docker-compose.oci.yml`: runs prebuilt, tagged images on the OCI platform. Only touch this for work explicitly about OCI deployment — never make local-dev changes here.
- If a config value genuinely belongs in both, update each file deliberately, preserving the local-builds-from-source vs. OCI-runs-tagged-images distinction.

## Common commands

Validate and run the full local stack from the repo root:

```bash
cp .env.example .env   # first time only
docker compose -f docker-compose.local.yml --env-file .env config
docker compose -f docker-compose.local.yml --env-file .env up --build
```

Gateway is at `http://localhost:8080`; frontend is also directly reachable at `http://localhost:3000`.

Per-project build/test (run whichever matches the code you changed):

```bash
npm --prefix apps/web ci
npm --prefix apps/web run build
npm --prefix apps/web run lint

./services/catalog/mvnw -f services/catalog/pom.xml test
./services/auction/mvnw -f services/auction/pom.xml test
./services/hobby-ai/mvnw -f services/hobby-ai/pom.xml test

python3 -m py_compile lambdas/image-processor/lambda_function.py
```

Run a single Maven test class (same pattern for auction/hobby-ai):

```bash
./services/catalog/mvnw -f services/catalog/pom.xml test -Dtest=ListingConversationServiceTest
```

Build OCI-compatible images (only when explicitly asked to do OCI work):

```bash
./scripts/build-oci-images.sh
```

Package the Lambda for deployment:

```bash
./lambdas/image-processor/build.sh   # installs Linux-compatible Pillow, produces a gitignored ZIP
```

Never commit `.env`, cloud credentials, private keys, generated image archives, or Lambda deployment packages.

## Architecture

### Services and routing

NGINX (`services/api-gateway/nginx.conf`) is the single entry point (port 8080) and routes by path prefix to three independent Spring Boot services plus the Next.js frontend:

- `/api/catalog` → **catalog** (8081): listings, image upload/processing orchestration, OpenAI-driven conversational listing creation.
- `/api/auction` → **auction** (8082): auctions and bids.
- `/api/auth`, `/api/ai/whatsapp`, `/api/ai-search`, `/api/ai` → **hobby-ai** (8088): hobby profiles, AI chat/search, email + WhatsApp OTP auth.
- `/` → **frontend** (3000): Next.js app.

There is no service registry (Eureka is explicitly disabled in all three services — `EUREKA_CLIENT_*=false`); NGINX does the routing and re-resolves upstream container IPs every 10s since Docker can reassign them on rebuild. Each backend service has its own `auth/AuthenticatedUser` class (independently implemented per service, not a shared library) that validates the same HS256 JWT using the shared `AUTH_TOKEN_SECRET`. There's no cross-service auth call — each service verifies the token locally and trusts the shared secret.

### The conversational listing feature (catalog service)

This is the most involved workflow in the codebase — read `docs/CONVERSATIONAL_LISTING_WITH_GPT_5_6.md` before modifying it. Flow:

1. Frontend (`apps/web/src/app/upload/page.tsx`) starts/resumes a conversation, keeping only the conversation ID client-side.
2. `services/catalog/.../conversation/` persists transcript, draft fields, state, and version in H2, and calls the OpenAI Responses API with structured JSON output for hobby-relevance/safety checks, field extraction, and price suggestion.
3. A state machine (`ListingConversationService`) drives the listing through collection → analysis → info-gathering → review → publishing → manual-review/rejection.
4. On publish, `CatalogService` sends every image to the Python Lambda (`lambdas/image-processor/lambda_function.py`), which uses Textract to detect and redact visible phone numbers/emails, then adds a masked-owner watermark.
5. Only the processed image is ever uploaded to S3 — OCR/redaction failure stops publication rather than falling back to an unredacted image. This fail-closed behavior is a deliberate privacy boundary; don't add a bypass path.
6. The older direct form-upload path (non-conversational) still exists in parallel as a compatibility path.

### Auth model

- Passwordless login: 6-digit email OTP via SendGrid, issued/verified by hobby-ai (`auth/EmailOtpService`, `AuthController`), producing an HS256 JWT (`AuthTokenService`) with issuer `bidmyhobby`, signed with `AUTH_TOKEN_SECRET` (must be ≥32 chars — services throw `IllegalStateException` at startup otherwise). WhatsApp OTP is an alternate/optional channel, gated behind `WHATSAPP_ENABLED=true`.
- The same `AUTH_TOKEN_SECRET` must be configured identically across hobby-ai, catalog, and auction — it's the only mechanism tying the services' auth together.
- Production sessions use an HttpOnly, Secure, SameSite=Strict cookie (`AUTH_COOKIE_SECURE=false` is a local-only override).
- Frontend auth helpers live in `apps/web/src/lib/auth.ts` (`authFetch` sends credentials, `clearSession` clears local role/email caches) — the JWT itself lives only in the cookie, not localStorage.

### Repository layout

| Path | Responsibility |
| --- | --- |
| `apps/web` | Next.js frontend (App Router) |
| `services/api-gateway` | NGINX reverse-proxy config only, no code |
| `services/catalog` | Catalog APIs, S3 integration, conversational OpenAI listing analysis, Lambda invocation |
| `services/auction` | Auction and bid APIs |
| `services/hobby-ai` | Hobby profiles, AI chat/search, email + WhatsApp auth, notifications |
| `lambdas/image-processor` | Python Lambda: image watermarking + Textract-based business-card/contact-info redaction |
| `scripts` | Repo-level build automation (OCI image builds) |

Deliberately excluded from this repo/architecture: payment, notification (standalone), social-interaction, standalone auth, AI-adapter services, Eureka, Config Server, and the legacy Spring Cloud API gateway — none of these are part of the current `docker-compose.oci.yml` topology. Don't reintroduce references to them. See `docs/REPOSITORY_STRUCTURE.md` for the full rationale.

Each Spring service follows the same internal package convention: `auth/`, `client/` (inter-service HTTP clients), `config/`, `controller/`, `model/`, `repository/`, `service/`, `exception/`, `event/`.
