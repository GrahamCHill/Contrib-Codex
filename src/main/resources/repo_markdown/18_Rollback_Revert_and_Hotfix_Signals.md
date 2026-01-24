# Rollback Revert and Hotfix Signals

Goal:
Infer incident-like patterns and quality escapes from Git history signals:
reverts, rollbacks, and hotfix activity, using METRICS only.

---

## INSTRUCTIONS FOR AI (STRICT)
- Use ONLY commit message text (if present), commit refs, timestamps (if present), contributors, and numeric counts from METRICS.
- Do NOT inspect source code contents.
- Do NOT invent incidents; only infer patterns when revert/hotfix evidence exists.
- Missing data MUST be labeled: **"Not provided in metrics"**.

---

## REQUIRED OUTPUT FORMAT

### 1) Revert/Hotfix Summary
- Total revert/hotfix indicators
- Time-window summary if timestamps exist

### 2) Quality Escape Risk Score (0–100)
- 0–25 low, 26–50 moderate, 51–75 high, 76–100 extreme

### 3) Time Cluster Analysis
If timestamps exist:
- Identify high-risk periods (bursts)
  If missing: **"Not provided in metrics"**

### 4) Most Affected Areas
- Directories most involved in revert/hotfix patterns (if supported)

### 5) Recommendations (6–10)
Title / Severity / Effort / Evidence Observed / Why It Matters / Action Plan / Where / Success Criteria

---

## SIGNAL RULES
- Only treat commit messages as revert/hotfix evidence if METRICS includes them.
- If message metrics absent: **"Not provided in metrics"**
