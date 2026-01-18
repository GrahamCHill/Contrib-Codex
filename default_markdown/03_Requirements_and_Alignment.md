# Requirements Alignment
Evaluate whether repository work and implemented capabilities align with project requirements, using both the git METRICS and the uploaded **Features** reference.
**CRITICAL: You MUST list ALL features requested in the requirements.md or Features input.**

> **Note**: Entries in the Feature-to-Evidence Mapping Table may not be 100% complete. Please refer to a proper feature completeness review for a definitive status.

INSTRUCTIONS FOR AI (STRICT):
- You MUST use BOTH inputs:
    1) the **METRICS** section (git activity, file paths, contributor mappings)
    2) the **Features** field (uploaded capabilities/feature list)
- Use ONLY names, file paths, directories, commit refs, metrics, and feature statements explicitly present in METRICS or Features.
- **STRICT EVIDENCE RULE**: Unless you are over 90% sure a file or directory directly implements a requested feature based on the METRICS (path names, diffs, etc.), do NOT list it as "Fully Met". If confidence is lower, mark as "Partially Met" or "Not Implemented / No Evidence Detected".
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

| Feature / Requirement (from Features) | Evidence in Repo (from METRICS) | Contributors Involved | Contribution Breakdown (%) | Alignment Status |
|---|---|---|---|---|
| ... | directories/files touched, commit refs if provided | contributor names | e.g., Alice (70%), Bob (30%) | Fully Met / Partially Met / Not Implemented / No Evidence Detected |

Rules:
- Evidence MUST reference file paths/directories from METRICS (and commit refs if present).
- If no evidence exists in METRICS, mark as **Not Implemented / No Evidence Detected** and leave "Evidence in Repo", "Contributors Involved", and "Contribution Breakdown (%)" as "N/A" or "None".
- **Contribution Breakdown (%)**: For each feature, estimate the percentage of implementation effort per contributor based on their **Meaningful Score** and volume of changes (LOC/commits) in the relevant directories/files.
    - **CRITICAL WEIGHTING**: When calculating the breakdown, you MUST apply weights based on the nature of the work:
        - **Backend/Logic / API Implementation**: 1.0x (Full weight for core logic, backend implementation, and actually *developing* an API endpoint).
        - **Frontend/Styling / API Consumption**: 0.5x (Half weight if the contribution is primarily visual, styling, or simply *calling* an existing API endpoint without implementing the underlying logic).
        - **Developing vs. Calling**: A contributor who *implements* the logic for an API endpoint should be credited significantly more than one who only adds a call to that endpoint in the frontend. Calling an API is an integration/usage task, whereas developing the endpoint is a core logic task.
        - Use the **Change Classification** from the Top Files analysis to identify if a contributor's work on a feature was primarily Logic vs. Styling/Other.
- DO NOT skip any features from the provided Features input, regardless of whether evidence exists.
- You MUST list ALL features requested in the **requirements.md** or Features input.

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
