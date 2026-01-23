# 21_Repository_Noise_and_Artifact_Score.md

Goal:
Quantify repository noise (generated files, vendor drops, lockfiles, config dumps) using METRICS only.
This section must prevent risk scoring distortion from irrelevant churn.

---

## INSTRUCTIONS FOR AI (STRICT)
- Use ONLY file paths, file types, churn totals, commit distribution, and contributor distribution from METRICS.
- Do NOT inspect file content.
- Do NOT invent ignore patterns not evidenced in METRICS.
- Missing data MUST be labeled: **"Not provided in metrics"**.

---

## REQUIRED OUTPUT FORMAT

### 1) Noise Ratio Estimate
- Noise churn share % (min/likely/max)

### 2) Noise Score (0–100)
- Higher score = cleaner repo
- Provide band: Clean / Mixed / Noisy / Extremely Noisy

### 3) Top Noise Sources
Provide top sources with:
- directory/file type
- churn share
- evidence

### 4) Metric Skew Warnings
Explain what other sections might be skewed by noise:
- seniority scoring
- hotspots
- workload estimate

### 5) Recommendations (6–10)
Title / Severity / Effort / Evidence Observed / Why It Matters / Action Plan / Where / Success Criteria

---

## NOISE SIGNALS (ONLY IF PRESENT IN METRICS)
- dist/build/vendor directories
- lockfiles
- minified/sourcemaps
- large JSON/XML dumps
  If file-type breakdown missing: **"Not provided in metrics"**
