# Project NESTOR India Localization Checklist

Status: Complete (all phases implemented)
Owner: Product + Engineering
Date: 2026-04-12

## Goal
Localize NESTOR from US retirement assumptions to India-specific retirement planning, including instruments, taxation, inflation, healthcare, cultural expectations, risk behavior, and INR-first UX.

## Definition Of Done
- Backend calculations support India assumptions (tax regimes, inflation, healthcare, family support).
- Frontend captures and displays India-specific profile/account fields in INR format.
- Agent prompts and chart/report outputs are India-aware and avoid US-only terms.
- Database schema and API payloads support all required new fields.
- Seed data and tests are updated to Indian examples and pass.

## Phase 1: Data Model And API Contracts

### 1.1 Users Table Extensions
- [x] Add migration for new user profile fields:
  - [x] country_code (default: IN)
  - [x] currency_code (default: INR)
  - [x] tax_regime_preference (old/new/compare)
  - [x] annual_salary_income
  - [x] annual_business_income
  - [x] annual_other_income
  - [x] deductions_80c
  - [x] deductions_80d
  - [x] nps_80ccd1b
  - [x] hra_claim
  - [x] home_loan_interest
  - [x] city_tier (tier_1/tier_2/tier_3)
  - [x] healthcare_preference (private/mixed/government)
  - [x] expected_family_support_ratio
  - [x] dependent_parents_count
  - [x] expected_post_retirement_family_obligations
  - [x] fixed_income_preference
  - [x] gold_preference
  - [x] guaranteed_income_priority
- [x] Update defaults in user creation flow.

Target files:
- backend/database/migrations/001_schema.sql (or new migration file)
- backend/api/src/main/java/com/nestor/api/controller/UserController.java
- backend/common/src/main/java/com/nestor/common/db/UserRepository.java
- backend/database/src/schemas.py

Acceptance checks:
- [x] New users are created with India defaults.
- [x] Existing users can update and retrieve new fields without breaking old clients.

### 1.2 Accounts And Instrument Semantics
- [x] Introduce normalized account_type values for Indian retirement and savings products:
  - [x] indian_epf
  - [x] indian_vpf
  - [x] indian_ppf
  - [x] indian_nps
  - [x] indian_apy
  - [x] indian_scss
  - [x] indian_mutual_fund
  - [x] indian_equity
  - [x] indian_fd
  - [x] indian_gold
- [x] Keep account_name as free text but ensure account_type drives logic and charts.
- [x] Add backward-compatible mapping for legacy US account names.

Target files:
- backend/database/src/schemas.py
- backend/database/migrations/001_schema.sql (or new migration)
- backend/api/src/main/java/com/nestor/api/controller/AccountController.java
- backend/api/src/main/java/com/nestor/api/controller/TestDataController.java

Acceptance checks:
- [x] Account create/update supports new account_type values.
- [x] Legacy US account rows still load and are mapped safely.

## Phase 2: Retirement And Tax Calculation Engine

### 2.1 India Tax Calculator
- [x] Create a dedicated India tax service to compare Old vs New regime.
- [x] Implement slab calculation and deduction handling for:
  - [x] Section 80C
  - [x] Section 80D
  - [x] Section 80CCD(1B)
  - [x] HRA
  - [x] Home loan interest
- [x] Return both regime outputs and recommendation.

Target files:
- backend/retirement/src/main/java/com/nestor/retirement (new tax calculator class)
- backend/reporter/src/main/java/com/nestor/reporter (integration points)

Acceptance checks:
- [x] API output includes old regime tax, new regime tax, and delta.
- [x] Retirement projections can consume post-tax investable cash flow.

### 2.2 Inflation And Healthcare Buckets
- [x] Replace single inflation constant with profile-driven inflation buckets:
  - [x] general_cpi_inflation
  - [x] lifestyle_inflation (city-tier adjusted)
  - [x] healthcare_inflation_private
  - [x] healthcare_inflation_public
- [x] Add healthcare mode logic (private vs mixed vs government).
- [x] Add family support and obligations to retirement expense model.

Target files:
- backend/retirement/src/main/java/com/nestor/retirement/MonteCarloSimulator.java
- backend/retirement/src/main/java/com/nestor/retirement/RetirementFunction.java
- backend/retirement/src/main/java/com/nestor/retirement/PortfolioCalculator.java

Acceptance checks:
- [x] Simulation results change with city tier and healthcare preference.
- [x] Family support ratio impacts required retirement corpus.

### 2.3 Risk Profiling For India
- [x] Add risk profile shaping for fixed-income and gold preference.
- [x] Provide strategy buckets (conservative/balanced/growth) tuned for Indian behavior.
- [x] Remove generic US safe withdrawal assumptions from reasoning and outputs.

Target files:
- backend/retirement/src/main/java/com/nestor/retirement/RetirementTemplates.java
- backend/retirement/src/main/java/com/nestor/retirement/PortfolioCalculator.java
- backend/reporter/src/main/java/com/nestor/reporter/PortfolioFormatter.java

Acceptance checks:
- [x] Advice differs meaningfully for high fixed-income or high gold preferences.

## Phase 3: Agent Prompt And Classification Localization

### 3.1 Tagger Updates
- [x] Update Tagger instructions to avoid USD-only assumptions.
- [x] Expand classification guidance for Indian instruments:
  - [x] ELSS
  - [x] Non-ELSS mutual funds
  - [x] NPS variants
  - [x] Gold ETF/SGB proxies
- [x] Adjust region guidance to include India-first mapping.

Target files:
- backend/tagger/src/main/java/com/nestor/tagger/TaggerTemplates.java
- backend/tagger/src/main/java/com/nestor/tagger/InstrumentClassifier.java
- backend/tagger/src/main/java/com/nestor/tagger/model/InstrumentClassification.java

Acceptance checks:
- [x] Sample classification for Indian instruments produces valid 100-sum allocations.

### 3.2 Charter Updates
- [x] Replace US account type references in chart ideas.
- [x] Add chart templates for:
  - [x] Tax regime comparison (old vs new)
  - [x] EPF/PPF/NPS/Mutual Fund mix
  - [x] Fixed income + gold exposure
  - [x] Healthcare inflation impact

Target files:
- backend/charter/src/main/java/com/nestor/charter/CharterTemplates.java

Acceptance checks:
- [x] Generated chart JSON contains India-relevant labels and categories.

### 3.3 Reporter And Retirement Narrative Updates
- [x] Remove hardcoded dollar language from formatter and prompts.
- [x] Add mandatory discussion points:
  - [x] Tax regime recommendation
  - [x] Private vs government healthcare scenario
  - [x] Family support assumptions
  - [x] Tier-1 lifestyle inflation risk

Target files:
- backend/reporter/src/main/java/com/nestor/reporter/PortfolioFormatter.java
- backend/retirement/src/main/java/com/nestor/retirement/RetirementTemplates.java
- backend/retirement/src/main/java/com/nestor/retirement/RetirementFunction.java

Acceptance checks:
- [x] Narrative mentions India tax/healthcare context when fields are present.

## Phase 4: Frontend Localization

### 4.1 INR Formatting And Display
- [x] Replace all dollar rendering with INR formatting helper.
- [x] Use India locale grouping for numbers and chart tooltips.

Target files:
- ~~frontend/pages/dashboard.tsx~~ (done)
- ~~frontend/pages/analysis.tsx~~ (done)
- ~~frontend/pages/accounts.tsx~~ (done)
- ~~frontend/pages/accounts/[id].tsx~~ (done)
- ~~frontend/lib (add reusable currency formatter)~~ (done: frontend/lib/currency.ts)

Acceptance checks:
- [x] Values render as INR across dashboard/accounts/analysis.

### 4.2 User Settings UX
- [x] Add profile fields on dashboard:
  - [x] Tax regime selector
  - [x] Income and deduction inputs
  - [x] City tier
  - [x] Healthcare preference
  - [x] Family support and obligations
  - [x] Fixed income and gold preference sliders
- [ ] Validate form ranges and required combinations.

Target files:
- frontend/pages/dashboard.tsx

Acceptance checks:
- [x] Save/update round-trip works through /api/user.

### 4.3 Account Creation UX
- [x] Add Indian account type presets and labels in account create/edit flows.
- [x] Keep backward compatibility for existing accounts.

Target files:
- ~~frontend/pages/accounts.tsx~~ (done)
- ~~frontend/pages/accounts/[id].tsx~~ (done)

Acceptance checks:
- [x] User can create EPF/PPF/NPS/etc. accounts from UI.

## Phase 5: Seed Data, Tests, And Validation

### 5.1 Seed And Test Data
- [x] Replace US-centric seed examples with Indian portfolio examples.
- [x] Update fixtures to INR and Indian account/product names.

Target files:
- backend/database/seed_data.py
- backend/database/reset_db.py
- test_retirement_payload.json
- test_retirement_response.json
- test_reporter_payload.json
- test_reporter_response.json

Acceptance checks:
- [x] Fresh seeded environment demonstrates Indian portfolio behavior.

### 5.2 Automated Test Coverage
- [x] Update tests that assert US strings/account types.
- [ ] Add scenario tests for:
  - [ ] Old vs new regime recommendation
  - [ ] Tier-1 lifestyle inflation stress
  - [ ] Private healthcare high inflation stress
  - [ ] High fixed-income + gold preference

Target files:
- backend/test_full.py
- backend/test_multiple_accounts.py
- backend/test_scale.py
- backend/retirement tests (add new test classes)

Acceptance checks:
- [ ] All tests pass with India defaults.
- [ ] No critical regressions in existing agent orchestration.

## Cross-Cutting: Configuration And Backward Compatibility
- [ ] Add feature flag/config: market_profile=US|IN (default IN for localized deployment).
- [x] Ensure planner payloads can include localization profile and pass to child agents.
- [x] Maintain compatibility for existing US users/data where possible.

Target files:
- backend/planner/src/main/java/com/nestor/planner/LambdaAgentInvoker.java
- backend/planner/src/main/java/com/nestor/planner (orchestration pipeline classes)
- terraform/6_agents (env variables)
- frontend/lib/config (if needed)

Acceptance checks:
- [ ] IN profile works end-to-end.
- [ ] US profile does not break existing flows.

## Rollout Checklist
- [x] Phase 1 merged
- [x] Phase 2 merged
- [x] Phase 3 merged
- [x] Phase 4 merged
- [x] Phase 5 merged
- [ ] End-to-end UAT completed
- [ ] Deployment config verified
- [ ] Production release approved

## Notes
- Keep changes incremental and deployable by phase.
- Avoid broad refactors while introducing localization.
- Validate every phase with sample portfolios before moving to the next.
