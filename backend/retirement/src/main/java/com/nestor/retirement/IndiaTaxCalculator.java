package com.nestor.retirement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * India income tax calculator supporting both Old and New tax regimes.
 * <p>
 * Calculates tax liability under both regimes and recommends the better one.
 * Accounts for Section 80C, 80D, 80CCD(1B), HRA, and home loan interest deductions.
 */
public final class IndiaTaxCalculator {

    private IndiaTaxCalculator() {}

    // ── New Regime Slabs (FY 2025-26 onwards) ────────────────────────────────
    // 0–4L: nil, 4–8L: 5%, 8–12L: 10%, 12–16L: 15%, 16–20L: 20%, 20–24L: 25%, >24L: 30%
    // Standard deduction: 75,000 for salaried
    private static final double[][] NEW_REGIME_SLABS = {
            {400_000, 0.00},
            {800_000, 0.05},
            {1_200_000, 0.10},
            {1_600_000, 0.15},
            {2_000_000, 0.20},
            {2_400_000, 0.25},
            {Double.MAX_VALUE, 0.30}
    };
    private static final double NEW_REGIME_STANDARD_DEDUCTION = 75_000;

    // ── Old Regime Slabs ─────────────────────────────────────────────────────
    // 0–2.5L: nil, 2.5–5L: 5%, 5–10L: 20%, >10L: 30%
    // Standard deduction: 50,000 for salaried
    private static final double[][] OLD_REGIME_SLABS = {
            {250_000, 0.00},
            {500_000, 0.05},
            {1_000_000, 0.20},
            {Double.MAX_VALUE, 0.30}
    };
    private static final double OLD_REGIME_STANDARD_DEDUCTION = 50_000;

    /** Maximum deduction limits */
    private static final double MAX_80C = 150_000;
    private static final double MAX_80D_SELF = 25_000;       // 50,000 if senior citizen
    private static final double MAX_80D_PARENTS = 50_000;    // for senior citizen parents
    private static final double MAX_80CCD1B = 50_000;
    private static final double MAX_HOME_LOAN_INTEREST = 200_000;

    /**
     * Calculate tax under both regimes and return comparison.
     *
     * @param grossIncome         total annual income (salary + business + other)
     * @param deductions80c       amount claimed under 80C (EPF, PPF, ELSS, etc.)
     * @param deductions80d       amount claimed under 80D (health insurance)
     * @param nps80ccd1b          NPS additional deduction under 80CCD(1B)
     * @param hraClaim            HRA exemption amount
     * @param homeLoanInterest    home loan interest deduction
     * @param dependentParents    number of dependent parents (affects 80D limit)
     * @return map with old_regime_tax, new_regime_tax, recommended_regime, delta
     */
    public static Map<String, Object> compare(
            double grossIncome,
            double deductions80c,
            double deductions80d,
            double nps80ccd1b,
            double hraClaim,
            double homeLoanInterest,
            int dependentParents) {

        // New regime: only standard deduction, no chapter VI-A deductions
        double newTaxableIncome = Math.max(0, grossIncome - NEW_REGIME_STANDARD_DEDUCTION);
        double newTax = calculateSlabTax(newTaxableIncome, NEW_REGIME_SLABS);
        newTax = applyHealthAndEducationCess(newTax);

        // Old regime: standard deduction + all applicable deductions
        double effective80c = Math.min(deductions80c, MAX_80C);
        double effective80d = Math.min(deductions80d, MAX_80D_SELF);
        if (dependentParents > 0) {
            effective80d += Math.min(deductions80d * 0.5, MAX_80D_PARENTS); // rough parent portion
        }
        double effective80ccd1b = Math.min(nps80ccd1b, MAX_80CCD1B);
        double effectiveHomeLoan = Math.min(homeLoanInterest, MAX_HOME_LOAN_INTEREST);

        double totalOldDeductions = OLD_REGIME_STANDARD_DEDUCTION
                + effective80c
                + effective80d
                + effective80ccd1b
                + hraClaim
                + effectiveHomeLoan;

        double oldTaxableIncome = Math.max(0, grossIncome - totalOldDeductions);
        double oldTax = calculateSlabTax(oldTaxableIncome, OLD_REGIME_SLABS);
        oldTax = applyHealthAndEducationCess(oldTax);

        String recommended = newTax <= oldTax ? "new" : "old";
        double delta = Math.abs(oldTax - newTax);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("old_regime_tax", round2(oldTax));
        result.put("new_regime_tax", round2(newTax));
        result.put("recommended_regime", recommended);
        result.put("delta", round2(delta));
        result.put("old_taxable_income", round2(oldTaxableIncome));
        result.put("new_taxable_income", round2(newTaxableIncome));
        result.put("total_old_deductions", round2(totalOldDeductions));
        return result;
    }

    /**
     * Calculate post-tax investable income for retirement projections.
     *
     * @param grossIncome total annual gross income
     * @param taxAmount   tax payable (from compare())
     * @param expenses    estimated annual living expenses
     * @return investable surplus
     */
    public static double calculateInvestableSurplus(double grossIncome, double taxAmount, double expenses) {
        return Math.max(0, grossIncome - taxAmount - expenses);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private static double calculateSlabTax(double taxableIncome, double[][] slabs) {
        double tax = 0.0;
        double remaining = taxableIncome;
        double previousLimit = 0.0;

        for (double[] slab : slabs) {
            double limit = slab[0];
            double rate = slab[1];
            double slabWidth = limit - previousLimit;
            double taxable = Math.min(remaining, slabWidth);

            if (taxable <= 0) break;

            tax += taxable * rate;
            remaining -= taxable;
            previousLimit = limit;
        }

        return tax;
    }

    /** 4% Health & Education Cess on total tax */
    private static double applyHealthAndEducationCess(double tax) {
        return tax * 1.04;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
