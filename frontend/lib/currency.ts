/**
 * Currency formatting utilities for multi-locale support (India / US).
 */

export type CountryCode = 'IN' | 'US';
export type CurrencyCode = 'INR' | 'USD';

export interface CurrencyConfig {
  countryCode: CountryCode;
  currencyCode: CurrencyCode;
  symbol: string;
  locale: string;
}

const CONFIGS: Record<CountryCode, CurrencyConfig> = {
  IN: { countryCode: 'IN', currencyCode: 'INR', symbol: '₹', locale: 'en-IN' },
  US: { countryCode: 'US', currencyCode: 'USD', symbol: '$', locale: 'en-US' },
};

/** Get currency config for a given country code. Defaults to India. */
export function getCurrencyConfig(countryCode?: string): CurrencyConfig {
  if (countryCode === 'US') return CONFIGS.US;
  return CONFIGS.IN;
}

/**
 * Format a number as currency using the appropriate locale and symbol.
 *
 * @param value  numeric value to format
 * @param cc     country code ('IN' | 'US'), defaults to 'IN'
 * @param opts   Intl.NumberFormat options overrides
 */
export function formatCurrency(
  value: number | string | undefined | null,
  cc?: string,
  opts?: Intl.NumberFormatOptions
): string {
  const num = typeof value === 'string' ? parseFloat(value) : (value ?? 0);
  if (isNaN(num)) return getCurrencyConfig(cc).symbol + '0';

  const config = getCurrencyConfig(cc);

  const formatted = new Intl.NumberFormat(config.locale, {
    style: 'currency',
    currency: config.currencyCode,
    minimumFractionDigits: 0,
    maximumFractionDigits: num % 1 === 0 ? 0 : 2,
    ...opts,
  }).format(num);

  return formatted;
}

/**
 * Format a number with the locale's grouping (no currency symbol).
 */
export function formatNumber(value: number | string | undefined | null, cc?: string): string {
  const num = typeof value === 'string' ? parseFloat(value) : (value ?? 0);
  if (isNaN(num)) return '0';
  const config = getCurrencyConfig(cc);
  return new Intl.NumberFormat(config.locale).format(num);
}

/** Indian account type labels to display names */
export const INDIA_ACCOUNT_TYPES: Record<string, string> = {
  indian_epf: 'EPF (Employee Provident Fund)',
  indian_vpf: 'VPF (Voluntary Provident Fund)',
  indian_ppf: 'PPF (Public Provident Fund)',
  indian_nps: 'NPS (National Pension System)',
  indian_apy: 'APY (Atal Pension Yojana)',
  indian_scss: 'SCSS (Senior Citizen Savings)',
  indian_mutual_fund: 'Mutual Fund',
  indian_equity: 'Direct Equity',
  indian_fd: 'Fixed Deposit',
  indian_gold: 'Gold / SGB',
};

/** US account type labels */
export const US_ACCOUNT_TYPES: Record<string, string> = {
  '401k': '401(k)',
  roth_ira: 'Roth IRA',
  traditional_ira: 'Traditional IRA',
  taxable: 'Taxable Brokerage',
  '529': '529 Plan',
  hsa: 'HSA',
  pension: 'Pension',
};

/** Get display label for an account type */
export function getAccountTypeLabel(accountType: string | undefined): string {
  if (!accountType || accountType === 'other') return 'Other';
  return INDIA_ACCOUNT_TYPES[accountType] || US_ACCOUNT_TYPES[accountType] || accountType;
}

/** Get all account type options for a given country */
export function getAccountTypeOptions(cc?: string): { value: string; label: string }[] {
  if (cc === 'US') {
    return [
      ...Object.entries(US_ACCOUNT_TYPES).map(([value, label]) => ({ value, label })),
      { value: 'other', label: 'Other' },
    ];
  }
  return [
    ...Object.entries(INDIA_ACCOUNT_TYPES).map(([value, label]) => ({ value, label })),
    { value: 'other', label: 'Other' },
  ];
}
