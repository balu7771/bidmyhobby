# BidMyHobby AI

## Inspiration

Hobbies are deeply personal. The objects connected to them—collectibles,
instruments, art supplies, handmade creations, vintage equipment, and
memorabilia—often carry stories that a conventional marketplace cannot
capture.

I was inspired by a simple question:

> What if selling or showcasing a hobby item felt like having a conversation
> with another enthusiast instead of completing a long, intimidating form?

Traditional marketplaces place most of the work on the seller. They must decide
on a title, category, description, condition, price, and safety considerations
before they can publish anything. New sellers may not know what their item is
worth, while collectors may care about details that a generic listing form
never asks for.

That led us to build **BidMyHobby AI**: an AI-assisted hobby marketplace where
people can discover hobbyists, showcase meaningful items, and offer items for
bidding through a guided, conversational experience.

BidMyHobby AI was built through a human–AI collaboration between me and Codex. I
brought the original idea, product vision, and decisions about the experience,
while Codex worked alongside me as an engineering partner to help design,
implement, debug, and refine the project across the frontend, backend services,
AI workflow, and cloud integrations.

## What it does

BidMyHobby AI helps hobby enthusiasts:

- Create a hobby profile and connect around shared interests.
- Discover hobby items and other enthusiasts.
- Chat using AI-powered hobby conversations.
- List an item by uploading photos and describing it naturally.
- Receive AI-generated suggestions for the title, description, category,
  condition, and price.
- Choose between an auction listing and a display-only showcase.
- Review and edit every AI suggestion before publishing.
- Place and manage bids through a dedicated auction workflow.
- Sign in securely using a passwordless email verification code.

The conversational listing assistant asks one focused question at a time when
information is missing. It can analyze up to four item photos, check whether the
item is hobby-related and safe, organize the information into a structured
draft, and suggest an estimated price in INR.

The seller always remains in control. AI-generated values are editable, prices
require explicit confirmation, and publishing is a deliberate final action.

## How we built it

We designed BidMyHobby AI as a modular application composed of a modern web
interface and specialized backend services.

### Frontend

The user experience is built with **Next.js and TypeScript**. It includes:

- A marketplace interface for browsing hobby items.
- Hobbyist profiles and AI-assisted conversations.
- A conversational listing workflow.
- A structured draft-review screen.
- Auction and bid-management experiences.
- Passwordless authentication.

The listing experience stores only the conversation identifier in the browser.
The complete transcript, uploaded images, draft fields, workflow state, and
version are maintained securely by the backend.

### Backend services

The platform uses multiple **Java Spring Boot** services:

- **Catalog service** for listings, image-processing orchestration, AI analysis,
  and publication.
- **Auction service** for bids and auction workflows.
- **Hobby AI service** for profiles, hobby conversations, AI search, and
  authentication.
- **NGINX API gateway** for routing requests between the frontend and backend
  services.

The services are containerized with **Docker Compose**, allowing the complete
application to run as one reproducible local stack while remaining suitable for
OCI deployment.

### AI-assisted listing creation

The catalog service integrates with the **OpenAI Responses API**.

The seller's images and conversation are transformed into structured output
containing information such as:

- Hobby relevance
- Safety assessment
- Confidence
- Title and description
- Category and condition
- Suggested price
- City and item dimensions
- Missing information
- The assistant's next question

Instead of treating the model response as unrestricted text, we use a
state-driven workflow that moves each listing through stages such as:

1. Information collection
2. Image and text analysis
3. Follow-up questions
4. Seller review
5. Publication
6. Manual review or rejection when necessary

This made the AI experience feel conversational while keeping the application
predictable.

### Image privacy and safety

One of the most important parts of the project is the image-processing boundary.

Before publication, uploaded images are processed by a **Python AWS Lambda**.
**Amazon Textract** performs OCR to identify visible text. Lines containing
phone numbers or email addresses are redacted directly from the image pixels,
and a masked-owner watermark is added.

Only the processed image is uploaded to **Amazon S3**.

The pipeline follows a fail-closed approach: if OCR or redaction fails,
publication stops. The platform never silently publishes the original,
potentially sensitive image.

### Authentication and security

Passwordless authentication uses a six-digit verification code delivered
through **SendGrid**. Authenticated sessions use secure, HttpOnly cookies, and a
shared token secret protects communication across the relevant services.

Listing conversations are owner-scoped and resumable. Duplicate publication is
prevented, and contact information is masked in both generated descriptions and
uploaded images.

## Challenges we faced

### Making AI output dependable

A language model can generate excellent descriptions, but a marketplace cannot
rely on free-form text alone. The application needs predictable fields, valid
prices, safe categories, and clear workflow transitions.

We addressed this with structured JSON output, validation, persisted
conversation state, and an explicit state machine. The AI proposes; the
application validates; the seller decides.

### Balancing automation with user control

It was tempting to make listing creation fully automatic, but that could publish
incorrect descriptions or unrealistic prices.

We designed the AI as an assistant rather than an authority. Sellers can edit
suggestions, confirm prices, and decide when to publish. Low-confidence listings
can also be marked for review.

### Protecting personal information inside images

Masking contact details in text is relatively straightforward. Detecting a phone
number or email address embedded in an uploaded photograph is much harder.

Combining Textract OCR with deterministic pixel-level redaction gave us a
practical solution. We also chose to stop publication when processing fails,
because privacy protection should not depend on a best-effort fallback.

### Coordinating several technologies

The project spans Next.js, TypeScript, Spring Boot, OpenAI, Docker, NGINX, AWS
Lambda, Textract, S3, SendGrid, and multiple backend services.

Keeping request contracts, authentication, networking, environment
configuration, and error handling consistent across these layers was one of the
largest engineering challenges. Containerizing the local environment and
separating service responsibilities helped make the system easier to understand
and test.

### Designing a natural conversation

The assistant needed to collect enough information without interrogating or
overwhelming the seller.

We learned to ask one focused question at a time, preserve conversation context,
and switch to structured review once the draft is sufficiently complete. This
made the process feel more like collaborating with a knowledgeable hobbyist.

## What we learned

Building BidMyHobby AI together taught us that creating an AI product is less
about simply generating text and more about designing a reliable system around
the model. This project was also an experiment in human–AI collaboration:
combining my understanding of the problem and product vision with Codex's
ability to reason across the codebase, implement features, and help troubleshoot
complex integrations.

The most valuable lessons were:

- Structured AI output is much easier to validate and integrate than free-form
  responses.
- AI should provide suggestions while consequential actions remain explicit.
- Confidence levels need real product behavior, such as manual-review states.
- Privacy and safety should be enforced by deterministic application logic.
- Conversational interfaces still require carefully designed state management.
- Image privacy must account for information inside pixels, not only metadata.
- A fail-closed design is appropriate when accidental exposure would harm
  users.
- Clear service boundaries make a complex, multi-technology system manageable.
- A good hobby marketplace must preserve the story of an item, not just its
  price.

## Accomplishments that we are proud of

We are especially proud that BidMyHobby AI combines a friendly conversational
experience with practical safeguards.

The platform does more than generate attractive listing descriptions. It
provides:

- Resumable, owner-scoped listing conversations.
- Multimodal analysis of descriptions and photographs.
- Structured, editable drafts.
- AI-assisted INR price suggestions.
- Auction and display-only listing options.
- Hobby relevance and safety checks.
- Manual-review handling for uncertain results.
- Pixel-level redaction of contact information.
- Explicit seller confirmation before publication.
- A modular architecture that can evolve independently.

## What's next for BidMyHobby AI

Our next steps include improving semantic hobby-item discovery, expanding
personalized recommendations, supporting more hobby categories, and making
price suggestions more explainable through comparable listings.

We would also like to add richer real-time auction experiences, stronger trust
and reputation signals, multilingual conversations, production-grade
persistence, and community features that help enthusiasts exchange knowledge—not
merely buy and sell objects.

Ultimately, we want BidMyHobby AI to become a place where every hobby item can
find the person who understands its value and the story behind it.
