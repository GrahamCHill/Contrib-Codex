# Requirements Alignment
Evaluate whether repository work and implemented capabilities align with project requirements, using both the git METRICS and the uploaded **Features** reference.

INSTRUCTIONS FOR AI (STRICT):
- You MUST use BOTH inputs:
    1) the **METRICS** section (git activity, file paths, contributor mappings)
    2) the **Features** field (uploaded capabilities/feature list)
- Use ONLY names, file paths, directories, commit refs, metrics, and feature statements explicitly present in METRICS or Features.
- Do NOT invent requirements, capabilities, modules, or features.
- If a requirement/capability is not present in Features or cannot be supported by METRICS, state:
  **"Not provided in Features"** or **"Not Implemented / No Evidence Detected"**.
- Do NOT assume implementation just because a feature is mentioned in Features.
    - Features describes intent/capability.
    - METRICS provides evidence of implementation activity.
- Avoid repetition; keep the analysis evidence-driven.

REQUIRED OUTPUT:

## 1) Feature Coverage Summary
Provide:
- Overall alignment rating (High / Medium / Low)
- 3–7 bullets summarizing what is strongly aligned, partially aligned, and missing

## 2) Feature-to-Evidence Mapping Table
Create a table mapping EVERY feature area from **Features** to evidence in **METRICS**.
**CRITICAL: You MUST include ALL features listed in the "Features" input in this table. If a feature is not present in METRICS, you MUST still include it and state "Not Implemented / No Evidence Detected".**

| Feature / Requirement (from Features) | Evidence in Repo (from METRICS) | Contributors Involved | Alignment Status |
|---|---|---|---|
| ... | directories/files touched, commit refs if provided | contributor names | Fully Met / Partially Met / Not Implemented / No Evidence Detected |

Rules:
- Evidence MUST reference file paths/directories from METRICS (and commit refs if present).
- If no evidence exists in METRICS, mark as **Not Implemented / No Evidence Detected** and leave "Evidence in Repo" and "Contributors Involved" as "N/A" or "None".
- DO NOT skip any features from the provided Features input, regardless of whether evidence exists.

## 3) Alignment by Architecture Area
Using directory patterns from METRICS, describe alignment across:
- Backend areas
- Frontend areas
- Infrastructure/config areas
- Data/reporting areas
  Only use categories if directories exist in METRICS.

## 4) Gaps, Risks, and Misalignment
Identify gaps in one of these categories (only if supported by Features + METRICS):
- Feature described in Features but no evidence in METRICS
- High churn / bulk commits in critical features
- Core feature changes without tests
- Evidence suggests partial implementation only

For each gap:
- Explain why it matters
- List evidence paths/directories
- List contributors involved (if provided)

## 5) Recommended Next Implementation Priorities
Provide 3–8 priorities derived strictly from:
- unmet/partial features in Features
- low test activity relative to feature complexity (if supported)
- areas of repo with high risk or high churn
  Each priority must include:
- What to implement/complete
- Where (directories)
- Who (contributors, if appropriate)
- How success will be measured
