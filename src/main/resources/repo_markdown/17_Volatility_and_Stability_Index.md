# Volatility and Stability Index

Goal:
Quantify stability and volatility of directories and contributors using Git METRICS only.
Identify rework tax, instability hotspots, and maintenance risk.

---

## INSTRUCTIONS FOR AI (STRICT)
- Use ONLY churn, commit frequency, revisit patterns, timestamps (if provided), contributors, and paths from METRICS.
- Do NOT inspect source code contents.
- Do NOT invent causes not supported by METRICS.
- Missing metrics MUST be labeled: **"Not provided in metrics"**.

---

## REQUIRED OUTPUT FORMAT

### 1) Repo Stability Score (0–100)
Include:
- Score
- Interpretation band (Stable / Mixed / Volatile / Unstable)

### 2) Directory Volatility Table
Table columns:
| Directory | Volatility Score (0–100) | Churn | Commits | Notes |

If directory volatility cannot be computed: **"Not provided in metrics"**

### 3) Contributor Volatility Table (if supported)
Table columns:
| Contributor | Volatility Score | Churn Share | High-risk Areas | Notes |

If contributor volatility not available: **"Not provided in metrics"**

### 4) Instability Hotspots
- Top 5–10 volatile areas with evidence.

### 5) Workload & Delivery Risks
Provide **6–10 risks** tied to rework burden.

### 6) Recommendations (6–10)
Title / Severity / Effort / Evidence Observed / Why It Matters / Action Plan / Where / Success Criteria

---

## VOLATILITY GUIDANCE
High volatility indicators:
- churn thrash (adds/deletes repeating)
- frequent revisits
- instability after merges
  If time-window stats missing: **"Not provided in metrics"**
