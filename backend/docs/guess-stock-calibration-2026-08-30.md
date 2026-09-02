# Guess Stock V2 Calibration Report

Run date: 2026-08-30  
Command: `mvn -q -Dcalibration.full=true -Dtest=com.order.menu.module.entertainment.mode.guessstock.simulation.CalibrationSuiteTest test`

## Market Distribution

Each engine/scenario pair used 10,000 games with 100 rounds and a fixed seed. The table below is the V2 result;
the generated report also retains all six V1 baselines for before/after comparison.

| Scenario | Median return | Positive games | Median max drawdown | Stage move P99 | Realized volatility | Regime switches |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| No event, no flow | +0.0416% | 50.60% | -3.3315% | 0.6866% | 0.3691% | 0.0160 |
| No event, balanced flow | +0.0040% | 50.04% | -4.3977% | 0.8972% | 0.4854% | 0.4287 |
| Balanced events, no flow | +0.1274% | 50.65% | -8.5617% | 1.7736% | 0.8427% | 10.6071 |
| Positive events only | +13.6559% | 99.99% | -2.2580% | 1.0009% | 0.5057% | 3.5006 |
| Negative events only | -11.7146% | 0.01% | -12.8092% | 0.9965% | 0.5015% | 3.3007 |
| Full market | +0.3070% | 51.48% | -9.2610% | 1.8087% | 0.8939% | 11.4206 |

The neutral scenarios remain centered near zero, positive and negative events separate in the expected direction,
and the full market satisfies the fixed 3-12 regime-switch regression band. In the full scenario, trend-state time
is 0.73% strong-up, 14.51% up, 71.07% sideways, 13.23% down, and 0.45% strong-down.

For comparison, the fixed-seed V1 full-market baseline has a -20.5863% median maximum drawdown, a 5.4137% stage
move P99, and 31.4990 trend switches per 100 rounds. V2 reduces those values to -9.2610%, 1.8087%, and 11.4206.

## AI Behavior

Each difficulty used 1,000 local, provider-free games with 100 rounds and a fixed seed.

| Difficulty | Sharpe-like | Average trades | Turnover | Holding ratio / rounds | Reverse trades | Decision checks |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Easy | -0.01 | 8.870 | 79.13% | 76.33% / 76.14 | 2.960 | 26.794 |
| Normal | 0.04 | 13.076 | 114.86% | 85.61% / 84.77 | 4.161 | 34.660 |
| Hard | 0.01 | 19.579 | 166.84% | 95.16% / 93.89 | 5.666 | 46.220 |

Trade counts fall inside the initial 5-15, 8-20, and 10-25 targets. This harness validates deterministic
policy and execution behavior; it intentionally does not call a paid model provider or measure provider token usage.

Machine-readable JSON plus separate V1, V2, and AI CSV files are generated under
`backend/target/guess-stock-calibration/` by the command above.
