# 20_Metrics_Integrity_and_Confidence_Report.md

Goal:
Evaluate completeness, reliability, and bias of the Git METRICS pipeline itself.
Provide confidence scores per report section and list skews/limitations.

---

## INSTRUCTIONS FOR AI (STRICT)
- Use ONLY what is present/missing in METRICS.
- Do NOT invent missing metrics.
- Do NOT infer repository properties beyond METRICS.

---

## REQUIRED OUTPUT FORMAT

### 1) Metrics Coverage Summary
- What’s included, what’s not, why it matters.

### 2) Missing Metrics List (Explicit)
- Bullet list of missing items; each MUST be factual:
  **"Not provided in metrics"**

### 3) Known Skews / Bias Risks
Examples (only if applicable):
- merge commits skew authoring
- renames inflate churn
- generated artifacts inflate LOC

### 4) Confidence Scores per Section (0–100)
Must include your report sections 01–20 (and any others present).
Format:
| Section | Confidence (0–100) | Main Limitations |

### 5) Recommendations to Improve METRICS (6–12)
Must include:
Title / Severity / Effort / Evidence / Action Plan / Success Criteria

### 6) Top 3 METRICS Additions Next Sprint
Exactly 3 bullets.

---

## MANDATORY CHECKLIST
State whether each is present:
- merge vs non-merge split
- timestamps / time window
- churn by directory over time
- contributor churn share
- test path detection
- file count per commit
- rename/move detection
- revert/hotfix detection
- generated artifact classification
