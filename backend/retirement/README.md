# nestor-retirement — Retirement Projection Agent

Runs Monte Carlo simulations and generates AI-written retirement analysis.

## How It Works

1. Receives `{ job_id, portfolio_data }` with holdings, retirement age, target income
2. Calculates total portfolio value and asset-class allocation percentages
3. Runs **Monte Carlo simulation** (500 iterations by default):
   - Accumulation phase: years until retirement with expected returns
   - Drawdown phase: post-retirement with withdrawal rate
4. Builds a prompt with simulation results (median, P10, P90 outcomes)
5. Calls Bedrock for narrative retirement analysis
6. Saves both `retirement_payload` (analysis text) and Monte Carlo data to Aurora

## Monte Carlo Assumptions

| Asset Class | Expected Return | Std Deviation |
|-------------|----------------|---------------|
| Equity | 7% | 18% |
| Fixed Income | 4% | 5% |
| Real Estate | 6% | 12% |
| Cash | 2% | 0% |
| Commodities | 5% | 15% |

## Lambda Settings

- **Timeout**: 5 minutes
- **Memory**: 1024 MB
- **Invoked by**: Planner (synchronous Lambda invoke)
