# Conclusion & Recommendations
Final synthesis of findings and strategic recommendations based strictly on the provided METRICS and repository change data.

## Instructions for the LLM (STRICT)
- Use ONLY contributor names, file paths, commit refs, and numeric values explicitly provided in the METRICS section.
- Do NOT invent names, commits, branches, tags, files, risks, or metrics.
- If a required value is missing, write: **"Not provided in metrics"** (do not guess).
- Any ranking statements MUST be numerically correct. If METRICS labels conflict with numbers, flag:
  **"Metrics inconsistency detected"**, then correct the ranking using numeric values.
- Avoid repetition. Do not restate prior sections verbatim—focus on synthesis and decisions.

## Required Output

### 1) Most Valuable Contributor (MVC)
Identify the overall **Most Valuable Contributor** using metric-backed justification.
Selection criteria (in priority order):
1. **Iterative development** (LOWER lines_added_per_commit is better; more granular commits)
2. **Quality signals** (tests touched, low churn, balanced add/delete patterns)
3. **Meaningful impact** (core logic / important areas, not primarily generated artifacts)
4. **Requirements alignment** (only if requirements are provided in METRICS)

Include:
- contributor name (exactly as in METRICS)
- key supporting metrics (commits, lines_added_per_commit, tests touched, top directories)
- 3–6 bullet justification points referencing evidence

### 2) Top Technical Risks (Top 3)
List the top 3 risks discovered, each with:
- Risk title
- Evidence (metrics and/or directory/file patterns from METRICS)
- Why it matters (impact on stability, security, maintainability, or velocity)

Examples of valid risk types (only if supported by METRICS):
- bulk commit patterns / poor granularity
- low test activity relative to core logic changes
- high churn (large add+delete cycles)
- heavy changes concentrated in critical directories
- generated artifacts / lockfile noise dominating LOC

### 3) Actionable Recommendations (3 items)
Provide 3 concrete recommendations tied directly to the identified risks.
Each recommendation must include:
- What to do (specific action)
- Why (risk addressed)
- How to implement (practical steps)
- Expected outcome (quality, stability, or velocity improvement)

Recommendations should be realistic and immediately actionable for a development team.
