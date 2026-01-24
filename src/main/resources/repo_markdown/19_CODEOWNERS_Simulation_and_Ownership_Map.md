# CODEOWNERS Simulation and Ownership Map

Goal:
Infer repository ownership patterns and propose CODEOWNERS-style mappings using METRICS only.
Identify bottlenecks, overload risk, and review routing improvements.

---

## INSTRUCTIONS FOR AI (STRICT)
- Use ONLY contributors, directories, commit counts, churn shares, merge counts (if present), and paths from METRICS.
- Do NOT assume org structure or teams.
- Missing data MUST be labeled: **"Not provided in metrics"**.

---

## REQUIRED OUTPUT FORMAT

### 1) Ownership Concentration Summary
- Is ownership distributed or concentrated? (METRICS evidence)

### 2) Ownership Map Table
Table columns:
| Directory | Top Contributors | Ownership Share % | Risk Notes |

Rules:
- Ownership share must be derived from churn or commit distribution metrics.

### 3) CODEOWNERS Simulation
Produce suggested rules in CODEOWNERS-like format, e.g.:
- /core/ @Alice @Bob
  Only use contributor names present in METRICS.

### 4) Bottleneck & Burnout Risks
Provide **6–10 risks** with:
Risk / Severity / Evidence Observed / Why It Matters / Mitigation / Where

### 5) Recommendations (6–10)
Title / Severity / Effort / Evidence Observed / Why It Matters / Action Plan / Where / Success Criteria

---

## OWNERSHIP RULES
- Single maintainer directory → flag bus factor risk
- Merge-heavy single integrator → flag integration overload
- If directory risk rating exists in METRICS, prioritize those areas
