# Repository structure

This repository contains only the services used by the current OCI Compose
architecture and the Lambda called by the catalog service.

```text
bidmyhobby/
├── apps/
│   └── web/
├── services/
│   ├── api-gateway/
│   ├── auction/
│   ├── catalog/
│   └── hobby-ai/
├── lambdas/
│   └── image-processor/
├── scripts/
├── docs/
├── docker-compose.local.yml
└── docker-compose.oci.yml
```

## Directory responsibilities

| Path | Responsibility |
| --- | --- |
| `apps/web` | Next.js user interface |
| `services/api-gateway` | NGINX reverse-proxy configuration |
| `services/catalog` | Catalog APIs, S3 integration, OpenAI listing analysis, and Lambda invocation |
| `services/auction` | Auction and bid APIs |
| `services/hobby-ai` | Hobby profiles, AI chat/search, and WhatsApp authentication |
| `lambdas/image-processor` | Image watermarking and Textract-based business-card extraction |
| `scripts` | Repository-level build automation |

## Deliberate exclusions

Payment, notification, social-interaction, auth, AI-adapter, Eureka, Config
Server, and the legacy Spring API gateway are not part of the current
`docker-compose.oci.yml` architecture. The older AI-moderation Lambda was also
excluded because active listing analysis is performed through OpenAI.

Generated dependencies, build output, local databases, session files, nested
Git metadata, populated environment files, credentials, and private keys are
excluded. The original source tree remains the recovery source for all omitted
legacy code.
