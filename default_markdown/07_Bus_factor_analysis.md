# Ownership / Bus Factor Analysis
Evaluate ownership concentration and risk from low contributor diversity in critical modules.

INSTRUCTIONS FOR AI (STRICT):
- Use ONLY contributor names, file paths, directories, and metrics explicitly present in METRICS.
- Do NOT infer ownership of modules not evidenced by METRICS.
- If ownership diversity data is missing, state: **"Not provided in metrics"**.
- **Merge Commit Rule:** Do NOT count merge commits as equivalent to direct code ownership. If merge breakdown is missing, explicitly warn that ownership signals may be skewed.

REQUIRED OUTPUT:

## 1) Bus Factor Summary
Provide 5–8 bullets describing:
- highest-risk modules for bus factor
- whether ownership is concentrated in core logic areas
- onboarding risk implications

## 2) Critical Modules Ownership Table
Create a table for the top critical modules/directories:

| Module / Directory | Primary Contributors | Ownership Diversity | Risk Level | Evidence |
|---|---|---|---|---|
| path/... | names | Low / Medium / High | High/Med/Low | metrics references |

Rules:
- "Ownership Diversity" is based on number of distinct contributors touching the module (if provided).
- "Risk Level" increases when:
    - module is core logic AND
    - ownership diversity is low AND
    - changes are high-churn/high-risk.

## 3) Single-Point-of-Failure Contributors
Identify contributors who appear to be single points of failure:
- list contributor name
- list modules/directories they dominate
- cite metrics evidence (commit concentration, LOC concentration, etc.)

## 4) Recommendations to Reduce Bus Factor
Provide 5–10 concrete actions (metric-backed), including:
- documentation and onboarding playbooks in high-risk areas
- pairing/review rotations on critical modules
- CODEOWNERS / review rules (only if directory structure is provided)
- increasing test coverage in concentrated modules


FINAL NOTE:
If ownership concentration cannot be computed due to missing per-module contributor mapping, state:
**"Insufficient evidence in metrics to compute module-level ownership diversity."**
