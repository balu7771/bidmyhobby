# BidMyHobby repository guidance

## Compose environments

- Use `docker-compose.local.yml` for local coding, builds, debugging, and
  integration testing.
- Treat references to `local.yml` as shorthand for
  `docker-compose.local.yml`.
- Use `docker-compose.oci.yml` only for running the prebuilt images on the OCI
  platform or for work explicitly related to OCI deployment.
- Do not make local-development changes in `docker-compose.oci.yml`.
- When a configuration change is genuinely required in both environments,
  update each Compose file deliberately while preserving their different
  roles: the local file builds from source; the OCI file runs tagged,
  OCI-compatible images.

## Common commands

From the repository root, validate and run the local stack with:

```bash
docker compose -f docker-compose.local.yml --env-file .env config
docker compose -f docker-compose.local.yml --env-file .env up --build
```

Build and package images for OCI only when requested:

```bash
./scripts/build-oci-images.sh
```

## Project layout

- `apps/web`: Next.js frontend
- `services/catalog`: catalog APIs and image-processing orchestration
- `services/auction`: auction and bidding APIs
- `services/hobby-ai`: hobby profiles, AI chat/search, and WhatsApp login
- `services/api-gateway`: NGINX routing
- `lambdas/image-processor`: Python image-processing Lambda

## Validation

Run checks appropriate to the code changed:

```bash
npm --prefix apps/web run build
./services/catalog/mvnw -f services/catalog/pom.xml test
./services/auction/mvnw -f services/auction/pom.xml test
./services/hobby-ai/mvnw -f services/hobby-ai/pom.xml test
python3 -m py_compile lambdas/image-processor/lambda_function.py
```

Never commit `.env`, cloud credentials, private keys, generated image archives,
or Lambda deployment packages.
