# NESTOR Frontend — NextJS React Dashboard

A NextJS (Pages Router) application providing the user interface for the NESTOR financial planning platform. Uses Clerk for authentication.

## Pages

| Route | Page | Description |
|-------|------|-------------|
| `/` | Landing page | Sign-in / sign-up |
| `/dashboard` | Dashboard | Portfolio overview, trigger analysis |
| `/analysis` | Analysis results | Reports, charts, retirement projections |
| `/accounts` | Account management | Create accounts, add positions |
| `/advisor-team` | AI Advisors | Meet the agent team |

## Key Directories

- `pages/` — Next.js page components
- `components/` — Reusable React components (charts, layout, etc.)
- `lib/` — API client (`api.ts`), configuration (`config.ts`), utilities
- `styles/` — Global and component CSS
- `public/` — Static assets

## Setup

```bash
# Install dependencies
npm install

# Copy and configure environment
cp .env.local.example .env.local
# Edit .env.local with your Clerk keys and API URL

# Run locally
npm run dev
```

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Yes | Clerk publishable key (from dashboard) |
| `CLERK_SECRET_KEY` | Yes | Clerk secret key (from dashboard) |
| `NEXT_PUBLIC_API_URL` | Yes | Backend API URL (local or API Gateway) |
| `NEXT_PUBLIC_CLERK_AFTER_SIGN_IN_URL` | No | Redirect after sign-in (default: `/dashboard`) |
| `NEXT_PUBLIC_CLERK_AFTER_SIGN_UP_URL` | No | Redirect after sign-up (default: `/dashboard`) |

## Production Deployment

The frontend is built as a static export and hosted on S3 behind CloudFront:

```bash
npm run build    # produces out/ directory
# Then use scripts/deploy.py or sync to S3 manually
```

CloudFront routes `/api/*` requests to API Gateway. See `terraform/7_frontend/` for infrastructure.

## Technology

- **NextJS** (Pages Router — required for Clerk integration)
- **React** with TypeScript
- **Recharts** for chart rendering
- **Clerk** for authentication (JWT-based)
- **Tailwind CSS** for styling
