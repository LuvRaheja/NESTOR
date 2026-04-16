import { useUser, useAuth } from "@clerk/nextjs";
import { useEffect, useState, useCallback } from "react";
import { API_URL } from "../lib/config";
import { formatCurrency, getCurrencyConfig } from "../lib/currency";
import Layout from "../components/Layout";
import Card from "../components/ui/Card";
import Button from "../components/ui/Button";
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from "recharts";
import { Skeleton, SkeletonCard } from "../components/Skeleton";
import { showToast } from "../components/Toast";
import Head from "next/head";

interface UserData {
  clerk_user_id: string;
  display_name: string;
  years_until_retirement: number;
  target_retirement_income: number;
  asset_class_targets: Record<string, number>;
  region_targets: Record<string, number>;
  country_code?: string;
  currency_code?: string;
  tax_regime_preference?: string;
  annual_salary_income?: number;
  annual_business_income?: number;
  annual_other_income?: number;
  deductions_80c?: number;
  deductions_80d?: number;
  nps_80ccd1b?: number;
  hra_claim?: number;
  home_loan_interest?: number;
  city_tier?: string;
  healthcare_preference?: string;
  expected_family_support_ratio?: number;
  dependent_parents_count?: number;
  expected_post_retirement_family_obligations?: number;
  fixed_income_preference?: number;
  gold_preference?: number;
  guaranteed_income_priority?: number;
}

interface Account {
  account_id: string;
  clerk_user_id: string;
  account_name: string;
  account_type: string;
  account_purpose: string;
  cash_balance: number;
  created_at: string;
  updated_at: string;
}

interface Position {
  position_id: string;
  account_id: string;
  symbol: string;
  quantity: number;
  created_at: string;
  updated_at: string;
}

interface Instrument {
  symbol: string;
  name: string;
  instrument_type: string;
  current_price?: number;
  asset_class_allocation?: Record<string, number>;
  region_allocation?: Record<string, number>;
  sector_allocation?: Record<string, number>;
}

export default function Dashboard() {
  const { user, isLoaded: userLoaded } = useUser();
  const { getToken } = useAuth();
  const [userData, setUserData] = useState<UserData | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [positions, setPositions] = useState<Record<string, Position[]>>({});
  const [instruments, setInstruments] = useState<Record<string, Instrument>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastAnalysisDate, setLastAnalysisDate] = useState<string | null>(null);

  // Form state for editable fields - start empty to avoid flicker
  const [displayName, setDisplayName] = useState("");
  const [yearsUntilRetirement, setYearsUntilRetirement] = useState(0);
  const [targetRetirementIncome, setTargetRetirementIncome] = useState(0);
  const [equityTarget, setEquityTarget] = useState(0);
  const [fixedIncomeTarget, setFixedIncomeTarget] = useState(0);
  const [indiaTarget, setIndiaTarget] = useState(0);
  const [internationalTarget, setInternationalTarget] = useState(0);

  // India-specific form state
  const [countryCode, setCountryCode] = useState("IN");
  const [taxRegimePreference, setTaxRegimePreference] = useState("new");
  const [annualSalaryIncome, setAnnualSalaryIncome] = useState(0);
  const [deductions80c, setDeductions80c] = useState(0);
  const [deductions80d, setDeductions80d] = useState(0);
  const [nps80ccd1b, setNps80ccd1b] = useState(0);
  const [cityTier, setCityTier] = useState("tier_1");
  const [healthcarePreference, setHealthcarePreference] = useState("mixed");
  const [familySupportRatio, setFamilySupportRatio] = useState(0);
  const [dependentParentsCount, setDependentParentsCount] = useState(0);
  const [fixedIncomePreference, setFixedIncomePreference] = useState(50);
  const [goldPreference, setGoldPreference] = useState(20);

  // Calculate portfolio summary
  const calculatePortfolioSummary = useCallback(() => {
    let totalValue = 0;
    const assetClassBreakdown: Record<string, number> = {
      equity: 0,
      fixed_income: 0,
      alternatives: 0,
      cash: 0
    };

    // Add cash balances
    accounts.forEach(account => {
      const cashBalance = Number(account.cash_balance);
      totalValue += cashBalance;
      assetClassBreakdown.cash += cashBalance;
    });

    // Add position values
    Object.entries(positions).forEach(([, accountPositions]) => {
      accountPositions.forEach(position => {
        const instrument = instruments[position.symbol];
        if (instrument?.current_price) {
          const positionValue = Number(position.quantity) * Number(instrument.current_price);
          totalValue += positionValue;

          // Add to asset class breakdown
          if (instrument.asset_class_allocation) {
            Object.entries(instrument.asset_class_allocation).forEach(([assetClass, percentage]) => {
              assetClassBreakdown[assetClass] = (assetClassBreakdown[assetClass] || 0) + (positionValue * percentage / 100);
            });
          }
        }
      });
    });

    return { totalValue, assetClassBreakdown };
  }, [accounts, positions, instruments]);

  // Load user data and accounts
  useEffect(() => {
    async function loadData() {
      if (!userLoaded || !user) return;

      try {
        const token = await getToken();
        if (!token) {
          setError("Not authenticated");
          setLoading(false);
          return;
        }

        // Get/create user
        const userResponse = await fetch(`${API_URL}/api/user`, {
          headers: {
            "Authorization": `Bearer ${token}`,
          },
        });

        if (!userResponse.ok) {
          throw new Error(`Failed to sync user: ${userResponse.status}`);
        }

        const response = await userResponse.json();
        const userData = response.user; // Extract user from response
        setUserData(userData);
        setDisplayName(userData.display_name || "");
        setYearsUntilRetirement(userData.years_until_retirement || 0);
        // Ensure target_retirement_income is a number
        const income = userData.target_retirement_income
          ? (typeof userData.target_retirement_income === 'string'
            ? parseFloat(userData.target_retirement_income)
            : userData.target_retirement_income)
          : 0;
        setTargetRetirementIncome(income);
        setEquityTarget(userData.asset_class_targets?.equity || 0);
        setFixedIncomeTarget(userData.asset_class_targets?.fixed_income || 0);
        setIndiaTarget(userData.region_targets?.india || userData.region_targets?.north_america || 0);
        setInternationalTarget(userData.region_targets?.international || 0);

        // India-specific fields
        setCountryCode(userData.country_code || "IN");
        setTaxRegimePreference(userData.tax_regime_preference || "new");
        setAnnualSalaryIncome(userData.annual_salary_income || 0);
        setDeductions80c(userData.deductions_80c || 0);
        setDeductions80d(userData.deductions_80d || 0);
        setNps80ccd1b(userData.nps_80ccd1b || 0);
        setCityTier(userData.city_tier || "tier_1");
        setHealthcarePreference(userData.healthcare_preference || "mixed");
        setFamilySupportRatio(userData.expected_family_support_ratio || 0);
        setDependentParentsCount(userData.dependent_parents_count || 0);
        setFixedIncomePreference(userData.fixed_income_preference || 50);
        setGoldPreference(userData.gold_preference || 20);

        // Get accounts
        const accountsResponse = await fetch(`${API_URL}/api/accounts`, {
          headers: {
            "Authorization": `Bearer ${token}`,
          },
        });

        if (accountsResponse.ok) {
          const accountsData = await accountsResponse.json();
          setAccounts(accountsData);

          // Get positions for each account
          const positionsMap: Record<string, Position[]> = {};
          const instrumentsMap: Record<string, Instrument> = {};

          for (const account of accountsData) {
            // Skip if account has no ID
            if (!account.id) {
              console.warn('Account missing ID in dashboard:', account);
              continue;
            }

            const positionsResponse = await fetch(`${API_URL}/api/accounts/${account.id}/positions`, {
              headers: {
                "Authorization": `Bearer ${token}`,
              },
            });

            if (positionsResponse.ok) {
              const positionsData = await positionsResponse.json();
              // API returns positions in a positions key
              positionsMap[account.id] = positionsData.positions || [];

              // Store instrument data from each position
              for (const position of positionsData.positions || []) {
                if (position.instrument) {
                  instrumentsMap[position.symbol] = position.instrument as Instrument;
                }
              }
            }
          }

          setPositions(positionsMap);
          setInstruments(instrumentsMap);
        }

        // Get last analysis date from jobs endpoint
        const jobsResponse = await fetch(`${API_URL}/api/jobs`, {
          headers: {
            "Authorization": `Bearer ${token}`,
          },
        });

        if (jobsResponse.ok) {
          const jobsData = await jobsResponse.json();
          const lastJob = (jobsData.jobs || []).find(
            (job: { status: string }) => job.status === "completed"
          );
          if (lastJob) {
            setLastAnalysisDate(lastJob.completed_at || lastJob.created_at);
          }
        }

      } catch (err) {
        console.error("Error loading data:", err);
        setError(err instanceof Error ? err.message : "Failed to load data");
      } finally {
        setLoading(false);
      }
    }

    loadData();
  }, [userLoaded, user, getToken]);

  // Listen for analysis completion events to refresh data
  useEffect(() => {
    if (!userLoaded || !user) return;

    const handleAnalysisCompleted = async () => {
      try {
        const token = await getToken();
        if (!token) return;

        console.log('Analysis completed - refreshing dashboard data...');

        // Refresh accounts to get latest prices
        const accountsResponse = await fetch(`${API_URL}/api/accounts`, {
          headers: {
            "Authorization": `Bearer ${token}`,
          },
        });

        if (accountsResponse.ok) {
          const accountsData = await accountsResponse.json();
          setAccounts(accountsData.accounts || []);

          // Load positions for each account
          const positionsData: Record<string, Position[]> = {};
          const instrumentsData: Record<string, Instrument> = {};

          for (const account of accountsData.accounts || []) {
            const positionsResponse = await fetch(
              `${API_URL}/api/accounts/${account.id}/positions`,
              {
                headers: {
                  "Authorization": `Bearer ${token}`,
                },
              }
            );

            if (positionsResponse.ok) {
              const data = await positionsResponse.json();
              positionsData[account.id] = data.positions || [];

              // Extract instruments from positions
              for (const position of data.positions || []) {
                if (position.instrument) {
                  instrumentsData[position.symbol] = position.instrument;
                }
              }
            }
          }

          setPositions(positionsData);
          setInstruments(instrumentsData);

          // Portfolio will be recalculated on render
        }
      } catch (err) {
        console.error("Error refreshing dashboard data:", err);
      }
    };

    // Listen for the completion event
    window.addEventListener('analysis:completed', handleAnalysisCompleted);

    return () => {
      window.removeEventListener('analysis:completed', handleAnalysisCompleted);
    };
  }, [userLoaded, user, getToken, calculatePortfolioSummary]);

  // Save user settings
  const handleSaveSettings = async () => {
    if (!userData) return;

    // Input validation
    if (!displayName || displayName.trim().length === 0) {
      showToast('error', 'Display name is required');
      return;
    }

    if (yearsUntilRetirement < 0 || yearsUntilRetirement > 50) {
      showToast('error', 'Years until retirement must be between 0 and 50');
      return;
    }

    if (targetRetirementIncome < 0) {
      showToast('error', 'Target retirement income must be positive');
      return;
    }

    // Validate allocation percentages
    const equityFixed = equityTarget + fixedIncomeTarget;
    if (Math.abs(equityFixed - 100) > 0.01) {
      showToast('error', 'Equity and Fixed Income must sum to 100%');
      return;
    }

    const regionTotal = indiaTarget + internationalTarget;
    if (Math.abs(regionTotal - 100) > 0.01) {
      showToast('error', 'India and International must sum to 100%');
      return;
    }

    setSaving(true);
    setError(null);

    try {
      const token = await getToken();
      if (!token) throw new Error("Not authenticated");

      const updateData = {
        display_name: displayName.trim(),
        years_until_retirement: yearsUntilRetirement,
        target_retirement_income: targetRetirementIncome,
        asset_class_targets: {
          equity: equityTarget,
          fixed_income: fixedIncomeTarget
        },
        region_targets: {
          india: indiaTarget,
          international: internationalTarget
        },
        // India localization fields
        country_code: countryCode,
        currency_code: countryCode === 'IN' ? 'INR' : 'USD',
        tax_regime_preference: taxRegimePreference,
        annual_salary_income: annualSalaryIncome,
        deductions_80c: deductions80c,
        deductions_80d: deductions80d,
        nps_80ccd1b: nps80ccd1b,
        city_tier: cityTier,
        healthcare_preference: healthcarePreference,
        expected_family_support_ratio: familySupportRatio,
        dependent_parents_count: dependentParentsCount,
        fixed_income_preference: fixedIncomePreference,
        gold_preference: goldPreference,
      };

      const response = await fetch(`${API_URL}/api/user`, {
        method: "PUT",
        headers: {
          "Authorization": `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(updateData),
      });

      if (!response.ok) {
        throw new Error(`Failed to save settings: ${response.status}`);
      }

      const updatedUser = await response.json();
      setUserData(updatedUser);

      // Show success toast
      showToast('success', 'Settings saved successfully!');

    } catch (err) {
      console.error("Error saving settings:", err);
      showToast('error', err instanceof Error ? err.message : "Failed to save settings");
    } finally {
      setSaving(false);
    }
  };

  const { totalValue, assetClassBreakdown } = calculatePortfolioSummary();
    const cc = userData?.country_code || countryCode || 'IN';
  // Prepare data for pie chart
  const pieChartData = Object.entries(assetClassBreakdown)
    .filter(([, value]) => value > 0)
    .map(([key, value]) => ({
      name: key.charAt(0).toUpperCase() + key.slice(1).replace('_', ' '),
      value: Math.round(value),
      percentage: totalValue > 0 ? Math.round(value / totalValue * 100) : 0
    }));

  const COLORS = ['#1178A8', '#753991', '#FFB707', '#062147', '#047857'];

  return (
    <>
      <Head>
        <title>Dashboard - Alex AI Financial Advisor</title>
      </Head>
      <Layout>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h1 className="text-3xl font-bold text-dark mb-8">Dashboard</h1>

        {loading ? (
          // Loading skeleton
          <div className="space-y-8">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="bg-white rounded-lg shadow p-6">
                  <Skeleton className="h-4 w-3/4 mx-auto mb-3" />
                  <Skeleton className="h-8 w-1/2 mx-auto" />
                </div>
              ))}
            </div>
            <SkeletonCard />
            <SkeletonCard />
          </div>
        ) : (
          <>
            {/* Portfolio Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <Card tier="raised" className="text-center">
            <h3 className="text-sm font-medium text-gray-500 mb-3">Total Portfolio Value</h3>
            <p className="text-3xl font-bold text-primary">
              {formatCurrency(totalValue, cc)}
            </p>
          </Card>

          <Card tier="raised" className="text-center">
            <h3 className="text-sm font-medium text-gray-500 mb-3">Number of Accounts</h3>
            <p className="text-3xl font-bold text-dark">{accounts.length}</p>
          </Card>

          <Card tier="raised">
            <h3 className="text-sm font-medium text-gray-500 mb-2 text-center">Asset Allocation</h3>
            {pieChartData.length > 0 ? (
              <div className="h-24">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={pieChartData}
                      cx="50%"
                      cy="50%"
                      innerRadius={20}
                      outerRadius={40}
                      paddingAngle={2}
                      dataKey="value"
                    >
                      {pieChartData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip formatter={(value: number) => formatCurrency(value, cc)} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <p className="text-sm text-gray-500">No positions yet</p>
            )}
          </Card>

          <Card tier="raised" className="text-center">
            <h3 className="text-sm font-medium text-gray-500 mb-3">Last Analysis</h3>
            <p className="text-3xl font-bold text-dark">
              {lastAnalysisDate ? new Date(lastAnalysisDate).toLocaleDateString() : "Never"}
            </p>
          </Card>
        </div>

        {/* User Settings Section */}
        <Card tier="raised" className="mb-8">
          <h2 className="text-xl font-semibold text-dark mb-6">User Settings</h2>

          {loading ? (
            <p className="text-gray-500">Loading...</p>
          ) : error && !error.includes("success") ? (
            <div className="bg-error-light border border-red-200 rounded-lg p-4 mb-4">
              <p className="text-error">{error}</p>
            </div>
          ) : error && error.includes("success") ? (
            <div className="bg-success-light border border-green-200 rounded-lg p-4 mb-4">
              <p className="text-success">✅ {error}</p>
            </div>
          ) : null}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Basic Info */}
            <div>
              <label htmlFor="displayName" className="block text-sm font-medium text-gray-700 mb-2">
                Display Name
              </label>
              <input
                id="displayName"
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label htmlFor="targetRetirementIncome" className="block text-sm font-medium text-gray-700 mb-2">
                Target Retirement Income (Annual)
              </label>
              <input
                id="targetRetirementIncome"
                type="text"
                value={targetRetirementIncome ? targetRetirementIncome.toLocaleString('en-US') : ''}
                onChange={(e) => {
                  const value = e.target.value.replace(/,/g, '');
                  const num = parseInt(value) || 0;
                  if (!isNaN(num)) {
                    setTargetRetirementIncome(num);
                  }
                }}
                className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            {/* Retirement Slider */}
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Years Until Retirement: {yearsUntilRetirement}
              </label>
              <input
                type="range"
                min="0"
                max="50"
                value={yearsUntilRetirement}
                onChange={(e) => setYearsUntilRetirement(Number(e.target.value))}
                className="w-full"
              />
              <div className="flex justify-between text-xs text-gray-500">
                <span>0</span>
                <span>10</span>
                <span>20</span>
                <span>30</span>
                <span>40</span>
                <span>50</span>
              </div>
            </div>

            {/* Target Allocations */}
            <div>
              <h3 className="text-sm font-medium text-gray-700 mb-3">Target Asset Class Allocation</h3>
              <div className="space-y-3">
                <div>
                  <label className="text-sm text-gray-600">Equity: {equityTarget}%</label>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={equityTarget}
                    onChange={(e) => {
                      const val = Number(e.target.value);
                      setEquityTarget(val);
                      setFixedIncomeTarget(100 - val);
                    }}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="text-sm text-gray-600">Fixed Income: {fixedIncomeTarget}%</label>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={fixedIncomeTarget}
                    onChange={(e) => {
                      const val = Number(e.target.value);
                      setFixedIncomeTarget(val);
                      setEquityTarget(100 - val);
                    }}
                    className="w-full"
                  />
                </div>
              </div>

              {/* Mini pie chart for asset allocation */}
              <div className="mt-4 h-32">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={[
                        { name: 'Equity', value: equityTarget },
                        { name: 'Fixed Income', value: fixedIncomeTarget }
                      ]}
                      cx="50%"
                      cy="50%"
                      outerRadius={40}
                      dataKey="value"
                    >
                      <Cell fill="#1178A8" />
                      <Cell fill="#753991" />
                    </Pie>
                    <Tooltip formatter={(value) => `${value}%`} />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div>
              <h3 className="text-sm font-medium text-gray-700 mb-3">Target Regional Allocation</h3>
              <div className="space-y-3">
                <div>
                  <label className="text-sm text-gray-600">India: {indiaTarget}%</label>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={indiaTarget}
                    onChange={(e) => {
                      const val = Number(e.target.value);
                      setIndiaTarget(val);
                      setInternationalTarget(100 - val);
                    }}
                    className="w-full"
                  />
                </div>
                <div>
                  <label className="text-sm text-gray-600">International: {internationalTarget}%</label>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={internationalTarget}
                    onChange={(e) => {
                      const val = Number(e.target.value);
                      setInternationalTarget(val);
                      setIndiaTarget(100 - val);
                    }}
                    className="w-full"
                  />
                </div>
              </div>

              {/* Mini pie chart for regional allocation */}
              <div className="mt-4 h-32">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={[
                        { name: 'India', value: indiaTarget },
                        { name: 'International', value: internationalTarget }
                      ]}
                      cx="50%"
                      cy="50%"
                      outerRadius={40}
                      dataKey="value"
                    >
                      <Cell fill="#FFB707" />
                      <Cell fill="#062147" />
                    </Pie>
                    <Tooltip formatter={(value) => `${value}%`} />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* India-Specific Settings */}
          <div className="mt-6 border-t border-border pt-6">
            <h3 className="text-lg font-semibold text-dark mb-4">India Retirement Profile</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {/* Country / Region */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Country / Region</label>
                <select
                  value={countryCode}
                  onChange={(e) => setCountryCode(e.target.value)}
                  className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="IN">India</option>
                  <option value="US">United States</option>
                </select>
              </div>

              {/* Tax Regime */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Tax Regime Preference</label>
                <select
                  value={taxRegimePreference}
                  onChange={(e) => setTaxRegimePreference(e.target.value)}
                  className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="new">New Regime</option>
                  <option value="old">Old Regime</option>
                  <option value="compare">Compare Both</option>
                </select>
              </div>

              {/* Annual Salary Income */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Annual Salary Income (₹)</label>
                <input
                  type="text"
                  value={annualSalaryIncome ? annualSalaryIncome.toLocaleString('en-IN') : ''}
                  onChange={(e) => {
                    const val = parseInt(e.target.value.replace(/,/g, '')) || 0;
                    setAnnualSalaryIncome(val);
                  }}
                  className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="0"
                />
              </div>

              {/* 80C Deductions */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Section 80C Deductions (₹)</label>
                <input
                  type="text"
                  value={deductions80c ? deductions80c.toLocaleString('en-IN') : ''}
                  onChange={(e) => setDeductions80c(parseInt(e.target.value.replace(/,/g, '')) || 0)}
                  className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="Max ₹1,50,000"
                />
              </div>

              {/* 80D Deductions */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Section 80D (Health Insurance) (₹)</label>
                <input
                  type="text"
                  value={deductions80d ? deductions80d.toLocaleString('en-IN') : ''}
                  onChange={(e) => setDeductions80d(parseInt(e.target.value.replace(/,/g, '')) || 0)}
                  className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="Max ₹25,000"
                />
              </div>

              {/* NPS 80CCD(1B) */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">NPS 80CCD(1B) (₹)</label>
                <input
                  type="text"
                  value={nps80ccd1b ? nps80ccd1b.toLocaleString('en-IN') : ''}
                  onChange={(e) => setNps80ccd1b(parseInt(e.target.value.replace(/,/g, '')) || 0)}
                  className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="Max ₹50,000"
                />
              </div>

              {/* City Tier */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">City Tier</label>
                <select
                  value={cityTier}
                  onChange={(e) => setCityTier(e.target.value)}
                  className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="tier_1">Tier 1 (Metro: Mumbai, Delhi, Bangalore...)</option>
                  <option value="tier_2">Tier 2 (Pune, Jaipur, Lucknow...)</option>
                  <option value="tier_3">Tier 3 (Smaller cities)</option>
                </select>
              </div>

              {/* Healthcare Preference */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Healthcare Preference</label>
                <select
                  value={healthcarePreference}
                  onChange={(e) => setHealthcarePreference(e.target.value)}
                  className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="private">Private (Higher cost, ~14% inflation)</option>
                  <option value="mixed">Mixed (Moderate, ~11% inflation)</option>
                  <option value="government">Government (Lower cost, ~8% inflation)</option>
                </select>
              </div>

              {/* Dependent Parents */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Dependent Parents Count</label>
                <input
                  type="number"
                  min="0"
                  max="4"
                  value={dependentParentsCount}
                  onChange={(e) => setDependentParentsCount(Number(e.target.value))}
                  className="w-full px-3 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
                />
              </div>

              {/* Family Support Ratio */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Family Support Ratio: {Math.round(familySupportRatio * 100)}%
                </label>
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={Math.round(familySupportRatio * 100)}
                  onChange={(e) => setFamilySupportRatio(Number(e.target.value) / 100)}
                  className="w-full"
                />
                <div className="flex justify-between text-xs text-gray-500">
                  <span>0%</span>
                  <span>50%</span>
                  <span>100%</span>
                </div>
              </div>

              {/* Fixed Income Preference */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Fixed Income Preference: {fixedIncomePreference}/100
                </label>
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={fixedIncomePreference}
                  onChange={(e) => setFixedIncomePreference(Number(e.target.value))}
                  className="w-full"
                />
              </div>

              {/* Gold Preference */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Gold Preference: {goldPreference}/100
                </label>
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={goldPreference}
                  onChange={(e) => setGoldPreference(Number(e.target.value))}
                  className="w-full"
                />
              </div>
            </div>
          </div>

          <div className="mt-6">
            <Button
              onClick={handleSaveSettings}
              disabled={saving || loading}
              loading={saving}
              variant="primary"
            >
              {saving ? 'Saving...' : 'Save Settings'}
            </Button>
          </div>
        </Card>
          </>
        )}
      </div>
      </Layout>
    </>
  );
}