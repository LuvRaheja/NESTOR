-- India Localization Schema Extensions
-- Version: 002
-- Description: Add India-specific user profile fields, account types, and localization support

-- ============================================================================
-- 1. Users table extensions for India localization
-- ============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS country_code VARCHAR(2) DEFAULT 'IN';
ALTER TABLE users ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3) DEFAULT 'INR';

-- Tax regime preference (India-specific)
ALTER TABLE users ADD COLUMN IF NOT EXISTS tax_regime_preference VARCHAR(10) DEFAULT 'new'
    CHECK (tax_regime_preference IN ('old', 'new', 'compare'));

-- Income fields (annual, in user's currency)
ALTER TABLE users ADD COLUMN IF NOT EXISTS annual_salary_income DECIMAL(15,2) DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS annual_business_income DECIMAL(15,2) DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS annual_other_income DECIMAL(15,2) DEFAULT 0;

-- India tax deductions
ALTER TABLE users ADD COLUMN IF NOT EXISTS deductions_80c DECIMAL(15,2) DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deductions_80d DECIMAL(15,2) DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS nps_80ccd1b DECIMAL(15,2) DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS hra_claim DECIMAL(15,2) DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS home_loan_interest DECIMAL(15,2) DEFAULT 0;

-- Lifestyle and location
ALTER TABLE users ADD COLUMN IF NOT EXISTS city_tier VARCHAR(10) DEFAULT 'tier_1'
    CHECK (city_tier IN ('tier_1', 'tier_2', 'tier_3'));

-- Healthcare
ALTER TABLE users ADD COLUMN IF NOT EXISTS healthcare_preference VARCHAR(10) DEFAULT 'mixed'
    CHECK (healthcare_preference IN ('private', 'mixed', 'government'));

-- Family and obligations
ALTER TABLE users ADD COLUMN IF NOT EXISTS expected_family_support_ratio DECIMAL(3,2) DEFAULT 0.00
    CHECK (expected_family_support_ratio >= 0 AND expected_family_support_ratio <= 1);
ALTER TABLE users ADD COLUMN IF NOT EXISTS dependent_parents_count INTEGER DEFAULT 0
    CHECK (dependent_parents_count >= 0 AND dependent_parents_count <= 4);
ALTER TABLE users ADD COLUMN IF NOT EXISTS expected_post_retirement_family_obligations DECIMAL(15,2) DEFAULT 0;

-- Investment preferences (0-100 scale)
ALTER TABLE users ADD COLUMN IF NOT EXISTS fixed_income_preference INTEGER DEFAULT 50
    CHECK (fixed_income_preference >= 0 AND fixed_income_preference <= 100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS gold_preference INTEGER DEFAULT 20
    CHECK (gold_preference >= 0 AND gold_preference <= 100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS guaranteed_income_priority INTEGER DEFAULT 50
    CHECK (guaranteed_income_priority >= 0 AND guaranteed_income_priority <= 100);

-- ============================================================================
-- 2. Accounts table: add account_type column for Indian instrument types
-- ============================================================================

ALTER TABLE accounts ADD COLUMN IF NOT EXISTS account_type VARCHAR(30) DEFAULT 'other';

-- No CHECK constraint so we can support both US and India types gracefully.
-- Valid values include:
--   US: 401k, roth_ira, traditional_ira, taxable, 529, hsa, pension, other
--   India: indian_epf, indian_vpf, indian_ppf, indian_nps, indian_apy,
--          indian_scss, indian_mutual_fund, indian_equity, indian_fd, indian_gold

-- ============================================================================
-- 3. Feature flag / market profile config
-- ============================================================================

-- Stored at the user level so US and IN users can coexist
-- country_code + currency_code already handle this above
