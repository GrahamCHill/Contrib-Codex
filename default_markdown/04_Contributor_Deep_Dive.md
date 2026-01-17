# Contributor Impact Analysis
A detailed evaluation of individual contributions based on commit frequency, change volume, risk profile, and code quality signals.

## Instructions for the LLM (STRICT)
- For EVERY major contributor listed in the METRICS section, create a dedicated technical subsection.
- Use ONLY contributor names, file paths, commit refs, and numeric values explicitly provided in METRICS.
    - Do NOT invent names, files, commit IDs, branches, tags, or metrics.
    - If a required value is missing, write: **"Not provided in metrics"** (do not guess).
- Use the **Gender** field strictly for pronouns:
    - male → he/him
    - female → she/her
    - non-binary/they/unknown → they/them
- Ensure any ranking statements are numerically correct. If the METRICS contradict themselves, flag:
  **"Metrics inconsistency detected"**, then correct the ranking using numeric values.

## Required Output (per contributor)
For each contributor, include:

### 1) Summary Table
A table containing (if available in METRICS):
- Total commits
- Total lines added
- Total lines deleted
- Net change (added − deleted)
- Lines added per commit (total_lines_added / total_commits)
- Tests touched (Yes/No + count if available)
- Primary areas modified (top directories / file types from METRICS)
- **Meaningful Score**: (Your LLM-calculated score 0-100)

### 2) Meaningful Score Tag (MANDATORY)
At the end of this contributor's subsection, you MUST include this exact tag: 
`[MEANINGFUL_SCORE: Contributor Name=XX/100]`
Where XX is the score you calculated. This score should take into account commit messages, iterative patterns, and the qualitative nature of the work.

### 3) Impact & Ownership Analysis
- Explain WHAT areas this contributor worked on (directories + file types).
- Describe the nature of the work using evidence:
    - features vs refactors vs formatting vs generated outputs
- **Functionality Comparison**: Identify the specific functional components or features this contributor is primarily responsible for. Compare their contributions to others to determine who "owns" or created the most functionality in key areas.
- **Top Files Analysis**: Reference the specific files they touched most frequently or with the most impact. Explain what those files do and how they contribute to the overall project requirements.
- MEANINGFUL COMMIT NAMES: Evaluate if the contributor's commit messages are descriptive and follow good practices (e.g., prefixing with type, clear intent) versus being vague (e.g., "update", "fix").
- FUNCTIONAL VS VISUAL/STYLING: Distinguish if their work was primarily functional logic or visual/styling (CSS, HTML, UI components in React/Vue). Be smart about detecting styling even in component files (e.g., changes to CSS-in-JS, styled-components, or large chunks of JSX with classes). If a contributor has significant 'Styling' category metrics, call it out specifically.
- Explicitly differentiate between:
    - **Meaningful functional changes**
    - **Low-signal changes** (generated artifacts, build outputs, lockfiles, whitespace churn), if indicated by METRICS

### 3) Code Quality Signals
Evaluate quality indicators based ONLY on METRICS:
- commit granularity (iterative vs bulk)
- whether tests were updated alongside logic
- churn (high add+delete in same areas) if data is provided

### 4) Risk Rating & Justification
Assign a risk band using the defined scale:
- Base it primarily on **Lines Added per Commit** (higher = higher risk)
- Adjust downward if tests were modified
- Note if changes are concentrated in high-risk areas (core logic) vs lower-risk areas (docs/config), using directory context

### 5) "Most Valuable Contributor" Potential
Assess their potential for being the **Most Valuable Contributor** using:
- iterative development (lower lines added per commit)
- evidence of quality assurance (tests touched)
- consistency and sustainability (low churn / good granularity)
- alignment with requirements (only if requirements are present in METRICS)

Do NOT select based on raw LOC alone.
