# Sequence Diagram 06 - Authentication and API Gateway
> Shows how **Clerk JWT authentication** flows from the browser through CloudFront, API Gateway, to the `nestor-api` Lambda for every protected request.
```mermaid
sequenceDiagram
    autonumber
    actor       User   as User (Browser)
    participant Clerk  as Clerk Auth
    participant FE     as NextJS Frontend
    participant CF     as CloudFront CDN
    participant APIGW  as API Gateway (HTTP API)
    participant API    as nestor-api Lambda
    participant Aurora as Aurora PostgreSQL
    %% Sign In Flow
    Note over User,Clerk: Sign In Flow
    User->>FE: Navigate to app
    FE->>Clerk: Redirect to Clerk Hosted Login
    User->>Clerk: Enter credentials
    Clerk-->>User: Set session cookie and issue short-lived JWT
    %% Token Refresh
    Note over FE,Clerk: Token lifecycle (auto-refresh)
    FE->>Clerk: getToken() - Clerk SDK call
    Clerk-->>FE: Fresh JWT (signed RS256, ~1 min expiry)
    %% Authenticated API Request
    Note over User,Aurora: Authenticated API Request
    User->>FE: Action e.g. View Dashboard
    FE->>CF: GET /api/users/me with Authorization Bearer JWT
    Note over CF: CloudFront forwards /api/* to API Gateway.<br/>Static routes go directly to S3.
    CF->>APIGW: Forward request + Authorization header
    APIGW->>API: Invoke Lambda (HTTP API, JWT validation is in Lambda)
    Note over API: Spring Security OAuth2 Resource Server<br/>validates JWT locally using JWKS
    API->>Clerk: Fetch JWKS (cached) from .well-known/jwks.json
    Clerk-->>API: Public key set (RSA)
    API->>API: Verify JWT signature and expiry, extract clerk_user_id
    alt JWT valid
        API->>Aurora: SELECT * FROM users WHERE clerk_user_id = subject
        Aurora-->>API: User row
        API-->>CF: HTTP 200 with user data
        CF-->>FE: Response
        FE-->>User: Render data
    else JWT invalid or expired
        API-->>FE: HTTP 401 Unauthorized
        FE->>Clerk: Re-authenticate (auto-refresh or redirect)
    end
    %% New User Auto-Provisioning
    Note over API,Aurora: New user auto-provisioning
    opt User not found in DB
        API->>Aurora: INSERT INTO users with clerk_user_id and display_name
        Aurora-->>API: Row created
        API-->>FE: HTTP 200 with new user
    end
```
### Routing Rules
| Path | CloudFront Behaviour | Destination |
|------|---------------------|-------------|
| `/api/*` | Forward to API Gateway origin | `nestor-api` Lambda |
| `/_next/*`, `/*.js`, `/*.css` | Cache at edge | S3 Static Site |
| `/*` (catch-all) | Serve `index.html` | S3 Static Site |
### Security Configuration
| Layer | Mechanism |
|-------|-----------|
| **Transport** | TLS 1.2+ enforced by CloudFront |
| **Authentication** | Clerk JWT (RS256) - validated in `nestor-api` via Spring Security |
| **JWKS caching** | Public keys cached in Lambda memory; refreshed on cache miss |
| **CORS** | Configured on API Gateway for the CloudFront domain |
| **Ingest API** | Separate API Gateway, protected by API Key (not JWT) |
---
[05 - Instrument Classification (Tagger)](./05_tagger_flow.md) | Next: [07 - Deployment Pipeline](./07_deployment_pipeline.md)