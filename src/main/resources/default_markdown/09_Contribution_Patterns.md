# Contribution Patterns
Assess team work distribution, "hero developer" patterns, and onboarding/stability risks.

INSTRUCTIONS FOR AI (STRICT):
- Use ONLY contributor names and numeric evidence provided in METRICS.
- Do NOT speculate about roles or intent.
- Avoid repetition: focus on distinct patterns and their risks.
- **Merge Commit Rule:** Separate integration activity (merge commits) from direct authoring output if such metrics exist.
  If merge breakdown is absent, warn that commit count distributions may be skewed.

REQUIRED OUTPUT:

## 1) Team Work Distribution Summary
Provide:
- overall distribution overview (balanced vs concentrated)
- areas of concentration (directories/modules)
- stability implications

## 2) Contribution Distribution Table
Create a table:

| Contributor | Commits | Lines Added | Lines Deleted | Lines/Commit | Tests Touched | Notes |
|---|---:|---:|---:|---:|---|---|

Rules:
- All numbers must come from METRICS.
- Lines/commit must be calculated as total_lines_added / total_commits.

## 3) "Hero Dev" / Concentration Detection
Identify if a "hero dev" pattern exists:
- high concentration of commits/LOC in one contributor
- dominance of critical directories
- limited ownership diversity (reference Ownership section if present)

Label:
- **Hero Pattern: Yes/No**
- Evidence bullets (metrics-backed)

## 4) Onboarding & Continuity Risks
Assess:
- risk if key contributor is unavailable
- ramp-up difficulty for new developers (based on module concentration, test availability, churn)
- where onboarding documentation is likely needed (directories only if present)

## 5) Mitigation Plan
Provide 5–10 actionable mitigations:
- cross-training / pairing
- stronger review rotation
- modularization where justified
- tests and documentation for critical areas
- risk scoring updates to reflect merge/integration work fairly

FINAL NOTE:
If module-level or test-level mappings are insufficient, state:
**"Insufficient evidence in metrics to fully assess onboarding and continuity risk."**
