# 23_Onboarding_Friction_Index.md

Goal:
Estimate onboarding friction and developer experience maturity using METRICS-only signals.
No code reading. No new inputs.

---

## INSTRUCTIONS FOR AI (STRICT)
- Use ONLY presence/absence indicators and change frequency of docs/build/scripts/templates directories IF present in METRICS.
- Use ONLY churn and commit distribution by directory/file type if available.
- Do NOT assume tooling or languages.
- Missing data MUST be labeled: **"Not provided in metrics"**.

---

## REQUIRED OUTPUT FORMAT

### 1) Onboarding Friction Score (0–100)
- Higher = easier onboarding
- Provide band:
    - 0–25 very hard
    - 26–50 hard
    - 51–75 moderate
    - 76–100 easy

### 2) Evidence Observed (METRICS-only)
8–16 bullets.

### 3) Friction Drivers (Ranked)
Provide 6–12 drivers with:
- Driver
- Severity
- Evidence Observed (METRICS)
- Why it slows onboarding

### 4) Recommendations (6–10)
Title / Severity / Effort / Evidence / Action Plan / Where / Success Criteria

---

## SIGNALS (ONLY IF PRESENT IN METRICS)
- README/docs files present and updated
- contribution guidelines paths
- scripts/setup paths
- repeated changes to setup scripts (instability)
  If docs presence cannot be inferred: **"Not provided in metrics"**
