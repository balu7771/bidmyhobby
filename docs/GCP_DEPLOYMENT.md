# GCP deployment

This deployment keeps the current application topology: one Compute Engine VM
runs the NGINX gateway, Next.js frontend, three Spring services, and Caddy for
HTTPS, all on the existing Docker network. GitHub Actions builds immutable
commit-tagged images, stores them in Artifact Registry, and updates the VM when
code reaches `main`. The VM's persistent Docker volumes keep the current H2 and
uploaded-file data across container replacements.

This is the lowest-complexity way to try the current Compose application on
GCP. It is a single VM, so it has no automatic failover or horizontal scaling.

## GCP setup

1. In the Google Cloud console, select the new project and link its billing
   account. Enable Compute Engine, Artifact Registry, IAM, IAM Service Account
   Credentials, Security Token Service, Identity-Aware Proxy, and Cloud Resource
   Manager APIs.
2. Create a regional Docker repository in Artifact Registry. Choose the same
   region as the VM; for India, `asia-south1` is a reasonable starting point.
3. Create one Ubuntu 24.04 LTS Compute Engine VM in that region. Start with
   `e2-standard-2` (2 vCPU, 8 GB RAM) and a 50 GB balanced persistent boot disk
   for a low-traffic trial. The Compose configuration caps each Java heap at
   768 MiB. Attach a dedicated
   VM service account with `roles/artifactregistry.reader`; give the VM the
   `cloud-platform` access scope. Enable OS Login in the VM's metadata. Keep
   the external IPv4 address static.
4. Create a VPC firewall rule allowing ingress TCP 80 and 443 to this VM only.
   Do not expose service ports 3000, 8081, 8082, or 8088. Point the DNS A record
   for your chosen domain at the VM's static IP. Caddy obtains and renews the
   HTTPS certificate automatically after DNS and firewall are ready.
5. Install Docker Engine, the Docker Compose plugin, and the Google Cloud CLI
   on the VM using their official Ubuntu instructions. As root, configure the
   Artifact Registry Docker credential helper for your repository region with
   `sudo gcloud auth configure-docker REGION-docker.pkg.dev --quiet`. Docker
   Compose runs under `sudo`, so this writes the credential helper settings
   where Compose will find them. The VM service account supplies short-lived
   credentials; do not create a service-account key. Create
   `/opt/bidmyhobby` and prepare its environment file as described below.

Create a billing budget and email alerts in **Billing → Budgets & alerts**
before starting the VM. A budget sends alerts; it does not cap charges. The
`e2-standard-4` VM, persistent disk, external IPv4, network egress, and
third-party API use all consume credit or can be billed after the credit is
used. For a short trial, stop the VM when you are not using it; its disk and
reserved external IP can still incur charges. Check the current estimate in
Google Cloud's pricing calculator for the selected region before provisioning.

## Keep these AWS integrations for the first GCP trial

The application is not cloud-neutral today. Catalog and hobby-ai use AWS S3,
catalog calls the AWS Lambda image processor, and auction stores bids in S3.
This plan leaves those code paths and the Lambda itself in AWS and connects to
them from the GCP VM using the existing AWS SDK configuration. The AWS identity
must allow only the S3 buckets and Lambda function the app needs. These AWS
services continue to incur AWS charges, separately from the GCP credit.

The Compose environment file is on the VM only. Create it with root-only
permissions (`sudo install -m 600 /dev/null /opt/bidmyhobby/.env`, then edit it
with `sudoedit /opt/bidmyhobby/.env`). Supply the production values for:

- `GCP_DOMAIN` — DNS name from step 4.
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_DEFAULT_REGION`,
  `AWS_S3_BUCKET_NAME`, and `AWS_S3_AI_BUCKET_NAME`.
- `AWS_LAMBDA_IMAGE_PROCESSOR` — deployed AWS Lambda function name.
- `AUTH_TOKEN_SECRET` — a random secret of at least 32 characters.
- `OPENAI_API_KEY`, `SENDGRID_API_KEY`, and `SENDGRID_FROM_EMAIL`.
- Optional Twilio values, `ADMIN_EMAILS`, and `WHATSAPP_ENABLED` as needed.

Do not put this file or these values in GitHub. `GCP_DOMAIN` and the non-secret
image/project settings below are GitHub repository variables; cloud and app
credentials remain on the VM. This preserves the current `.env` secret model
while GitHub only has authority to publish images and issue VM commands.

## GitHub Actions identity and settings

Configure Workload Identity Federation (WIF) so Actions uses short-lived
credentials instead of a downloaded service-account key. In GCP, create a
workload identity pool and GitHub OIDC provider. Map `google.subject` to
`assertion.sub`, and `attribute.repository` to `assertion.repository`. Restrict
the provider condition to this repository and `assertion.ref ==
'refs/heads/main'`. On the deployment service account, grant
`roles/iam.workloadIdentityUser` to the matching repository principal set from
that pool (format:
`principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/POOL_ID/attribute.repository/OWNER/REPO`)
so the workflow can impersonate it.

Grant the deployment service account only the roles needed for deployment:

- `roles/artifactregistry.writer` on the image repository.
- `roles/compute.instanceAdmin.v1` on the project, plus
  `roles/compute.osAdminLogin` and `roles/iap.tunnelResourceAccessor` for IAP
  SSH/SCP through OS Login. `osAdminLogin` is needed because deployment runs
  Docker Compose with `sudo`.
- `roles/iam.serviceAccountUser` on the VM's service account.

On the VM service account, keep only `roles/artifactregistry.reader` for pulling
images. Use a dedicated deployment service account and tighten the Compute
permissions to the VM where practical.

In **GitHub repository → Settings → Secrets and variables → Actions**, add:

| Kind | Name | Value |
| --- | --- | --- |
| Secret | `GCP_WIF_PROVIDER` | Full provider resource name: `projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/POOL_ID/providers/PROVIDER_ID` |
| Secret | `GCP_DEPLOY_SERVICE_ACCOUNT` | Deployment service account email |
| Variable | `GCP_PROJECT_ID` | Project ID (not project number) |
| Variable | `GCP_REGION` | Artifact Registry region, such as `asia-south1` |
| Variable | `GCP_AR_REPOSITORY` | Artifact Registry repository name |
| Variable | `GCP_VM_NAME` | Compute Engine instance name |
| Variable | `GCP_VM_ZONE` | VM zone, such as `asia-south1-a` |
| Variable | `GCP_DOMAIN` | Public DNS name pointing to the VM |

After the VM and its `.env` are ready, merge a change to `main` or manually
run **Actions → Build and deploy to GCP → Run workflow**. The workflow runs the
backend test suites and frontend production build before it publishes images.
It deploys over IAP, then checks that the public HTTPS homepage responds.

## Limitations and follow-up

- There is a brief restart window during deployment, and a VM failure takes the
  whole site offline. Consider managed databases, backups, and multi-instance
  deployment only after the first end-to-end trial works.
- H2 and local image files are retained on the VM disk, not backed up. Set up
  backups before relying on this as a production system.
- To become GCP-native later, replace the S3 and Lambda adapters with Cloud
  Storage and Cloud Run functions or another image-processing service. The
  current Lambda can remain unchanged for the first hybrid deployment.
