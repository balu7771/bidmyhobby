# Conversational Hobby Listing with GPT-5.6

## What we built

BidMyHobby now lets a signed-in seller create a hobby listing through a guided
conversation instead of completing a long upload form. The seller adds one to
four photos and describes the item naturally. The assistant identifies missing
details, proposes a structured draft, estimates an INR price, and asks one
focused follow-up question at a time. The seller remains in control: suggested
values can be edited, the price must be confirmed, and publishing is an explicit
action.

GPT-5.6 was used as the engineering assistant for designing, implementing, and
debugging this feature across the web, catalog, gateway, Docker, OpenAI, and AWS
Lambda layers. The application runtime model is separately configurable through
`OPENAI_MODEL`; the current local AWS/OpenAI project exposes `gpt-4o-mini`, so
that is the checked-in default. This separation lets the runtime move to GPT-5.6
when the project is granted model access without changing the workflow.

## How it works

1. The Next.js upload page creates or resumes a listing conversation and stores
   only its conversation ID in browser storage.
2. The catalog service persists the transcript, draft fields, state, version,
   and temporary image bytes in its local H2 database.
3. Photos and the conversation are sent to the OpenAI Responses API using
   structured JSON output. The response contains hobby relevance, safety,
   confidence, title, description, category, condition, suggested price, city,
   size, missing fields, and the next assistant message.
4. A small state machine moves the listing through collection, analysis,
   information gathering, review, publishing, manual review, or rejection.
5. On publish, every image is sent to the Python image-processor Lambda.
   Amazon Textract detects visible phone numbers and email addresses; matching
   lines are redacted before a masked-owner watermark is added.
6. Only the processed image is uploaded to S3. OCR or redaction failure stops
   publication, ensuring that the system never silently falls back to an
   unredacted image.

## Safety and product decisions

- Only hobby-related and safe items can proceed to publication.
- Contact details are masked in generated descriptions and redacted from image
  pixels.
- AI price estimates are suggestions, not promises, and require confirmation.
- Low-confidence listings are published with `REVIEW_REQUIRED` status.
- Conversations are owner-scoped, resumable, and protected from duplicate
  publication.
- The existing form-upload path remains available as a compatibility path while
  the conversational endpoint uses the new OpenAI workflow.

## Main implementation areas

- `apps/web/src/app/upload/page.tsx` — conversational upload and draft review UI
- `services/catalog/.../conversation` — API, persistence, state machine, and
  structured OpenAI analysis
- `services/catalog/.../CatalogService.java` — safe publication of approved
  drafts
- `lambdas/image-processor/lambda_function.py` — deterministic OCR redaction and
  watermarking
- `docker-compose.local.yml` and `services/catalog/Dockerfile.local` —
  source-based, reproducible local catalog builds

The result is a shorter seller journey with explicit review controls and a
fail-closed privacy boundary between AI-assisted drafting and public listing
publication.
