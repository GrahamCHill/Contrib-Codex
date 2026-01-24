# 22_Release_Cadence_and_Engineering_Rhythm.md

Goal:
Quantify release cadence and engineering rhythm from Git activity patterns to identify workload spikes,
schedule risk windows, and delivery predictability.

---

## INSTRUCTIONS FOR AI (STRICT)
- Use ONLY timestamps, commit frequency, merge cadence, churn over time, and contributor activity from METRICS.
- Do NOT assume sprint structure or deployment flow.
- Do NOT moralize about working times.
- Missing data MUST be labeled: **"Not provided in metrics"**.

---

## REQUIRED OUTPUT FORMAT

### 1) Rhythm Summary
- classify: steady / burst-driven / irregular / highly spiky (METRICS-cited)

### 2) Cadence Metrics
If timestamps exist:
- commits/week, churn/week
- merges/week (if present)
- active contributors/week
  If missing: **"Not provided in metrics"**

### 3) Peak Workload Windows
- Identify top 3–8 peak periods with evidence.

### 4) Predictability Score (0–100)
- Higher = more predictable cadence.

### 5) Workload Risks (Cadence-driven)
Provide 6–10 risks:
Risk / Severity / Evidence / Why It Matters / Mitigation / Where

### 6) Recommendations (6–10)
Title / Severity / Effort / Evidence / Action Plan / Success Criteria
