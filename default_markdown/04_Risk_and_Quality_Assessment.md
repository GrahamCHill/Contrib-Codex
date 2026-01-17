# Risk & Quality Assessment
Evaluation of project stability and potential technical debt based on commit patterns and repository change metrics.

## Instructions for the LLM (STRICT)
Use ONLY the contributor names, file paths, commit refs, and numeric values provided in the METRICS section.
- Do NOT invent contributors, commits, file names, or numbers.
- If a value is missing, write: **"Not provided in metrics"**.
- Any ranking (highest/lowest risk) MUST be numerically correct. If the METRICS are inconsistent, explicitly flag:
  **"Metrics inconsistency detected"**, then correct the ranking using the numeric values.

## Required Output
### 1) Risk Table (all contributors)
Create a table with **one row per contributor**, including:
- Contributor name
- Total commits
- Total lines added
- **Lines added per commit** (must be calculated: total_lines_added / total_commits)
- Tests touched (Yes/No + count if available)
- Risk band (LOW / LOW-MED / MED / MED-HIGH / HIGH / VERY HIGH) using the defined scale
- Brief justification (1–3 sentences, metric-backed)

### 2) Risk Reasoning (per contributor)
For each contributor:
- Explain WHY their risk level was assigned, referencing specific metrics.
- Identify whether their commits are primarily:
    - **Iterative Refinement** (small/consistent changes), or
    - **Bulk Commits** (large spikes, low granularity)

### 3) Quality Signals and Testing Gaps
- Highlight areas where **test activity is low** relative to the amount/complexity of feature changes.
- Explicitly call out when:
    - core logic changes occurred without corresponding test changes, OR
    - tests were modified and likely reduced risk.

### 4) Summary Findings
Provide 3–7 bullet points summarizing:
- Highest-risk contributors (numerically verified)
- Lowest-risk / most iterative contributors (numerically verified)
- Key technical debt or stability concerns inferred from the provided metrics
