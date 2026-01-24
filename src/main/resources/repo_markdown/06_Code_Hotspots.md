# Code Hotspots
Identify the most frequently changed files/modules and interpret churn and technical risk using git metrics.

INSTRUCTIONS FOR AI (STRICT):
- Use ONLY contributor names, file paths, directories, commit refs, and numeric values explicitly provided in the METRICS section.
- Do NOT invent files, modules, directories, commits, or metrics.
- If a metric is missing, write: **"Not provided in metrics"**.
- **Merge Commit Rule:** Merge commits must be treated separately from feature commits when interpreting churn and risk. If merge breakdown is not provided, state:
  **"Merge commit breakdown not provided; hotspot and churn metrics may be skewed."**

REQUIRED OUTPUT:

## 1) Hotspot Summary (High-level)
Provide 5–10 bullets highlighting:
- top changed directories/modules
- whether hotspots are concentrated in core logic vs config/docs
- any high-risk patterns (bulk changes, recurring churn, low tests)

## 2) Hotspot Table (Top 10–25)
Create a table listing the most changed areas (from METRICS):

| Rank | File / Directory | Change Volume Evidence | Churn Evidence | Risk Notes |
|---:|---|---|---|---|
| 1 | path/... | commits/LOC (from METRICS) | add+delete cycles / rename noise | risk band + why |

Rules:
- Rank MUST reflect numeric evidence (e.g., commits or LOC).
- "Churn Evidence" must cite add/delete patterns if available.
- If file-level breakdown is absent, use directory-level patterns and state it.

## 3) Churn + Risk Interpretation
For the top hotspots:
- Explain why the hotspot is risky or not risky using:
    - lines added per commit
    - churn (add+delete cycles)
    - test activity presence/absence
    - file type and directory location (core logic vs lower-risk config/docs)
- Explicitly call out:
    - **Bulk generation / artifacts** (dist/, build/, *.map, minified, lockfiles) if present
    - **Refactor churn** (moves/renames/splits) if present

## 4) Mitigations & Targeted Actions
Provide 3–8 targeted improvements focused specifically on hotspots, each with:
- Hotspot path
- Observed issue
- Action (concrete steps)
- Expected outcome (reduced churn, increased tests, better modularity)

FINAL NOTE:
If hotspot detection cannot be performed due to missing file/module change data, state:
**"Insufficient evidence in metrics to identify hotspots at file/module granularity."**
