# Contributor Impact Analysis
A detailed evaluation of individual contributions based on commit frequency, change volume, risk profile, and code quality signals.

## Instructions for the LLM (STRICT)
- For EVERY major contributor listed in the METRICS section, create a dedicated technical subsection.
- Use ONLY contributor names, file paths, commit refs, and numeric values explicitly provided in METRICS.
    - Do NOT invent names, files, commit IDs, branches, tags, or metrics.
    - If a required value is missing, write: **"Not provided in metrics"** (do not guess).
- Use the **Gender** field strictly for pronouns. Do NOT explicitly state the gender or pronouns in the document text (e.g., do not say "He is a male contributor"). Simply use the correct pronouns when referring to the contributor:
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
- Documentation Lines Added
- Tests touched (Yes/No + count if available)
- Generated Files Pushed (Count)
- Primary areas modified (top directories / file types from METRICS)
- **Meaningful Score**: (Your LLM-calculated score 0-100)

### 2) Meaningful Score Tag (MANDATORY)
At the end of this contributor's subsection, you MUST include this exact tag: 
`[MEANINGFUL_SCORE: Contributor Name=XX/100]`
Where XX is the score you calculated. This score should take into account commit messages, iterative patterns, and the qualitative nature of the work. You MUST heavily penalize this score if the contributor pushed build or auto-generated files.

### 3) Impact & Ownership Analysis
- Explain WHAT areas this contributor worked on (directories + file types).
- Describe the nature of the work using evidence:
    - features vs refactors vs formatting vs generated outputs
- **Ownership Analysis (Creator vs Editor)**: 
    - Identify which files this contributor **created** (look for "Creator: [Name]" in Top Files matching this contributor).
    - Identify which files this contributor only **edited** but did not create.
    - If a contributor is the creator of a file, they have higher "foundational ownership". If they only edited it, analyze if they added new functional logic or just minor tweaks.
- **Functionality Comparison**: Identify the specific functional components or features this contributor is primarily responsible for. Compare their contributions to others to determine who "owns" or created the most functionality in key areas.
- **Key Man Identification**: Assess if this contributor is a Key Man for specific sections. High total lines committed indicate potential Key Man risk, but look at where all other contributors have committed. If other contributors did not touch a section this contributor owns, they are a Key Man for that section.
- **Top Files Analysis**: Reference the specific files they touched most frequently or with the most impact. Explain what those files do and how they contribute to the overall project requirements. Mention if they were the **original creator** of these high-impact files.
    - **Change Classification**: For each top file, use the provided **DIFF** to classify the nature of the changes into one of these categories:
        - **Logic**: Substantial changes to business logic, algorithms, or core functionality.
        - **Refactor**: Restructuring existing code without changing behavior (e.g., renaming, extracting methods).
        - **Styling**: Visual changes, CSS, layout tweaks, or HTML/JSX changes that primarily affect appearance.
        - **Other**: Documentation, configuration, boilerplate, or minor fixes.
    - Provide a brief justification for each classification based on the diff evidence.
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
- Base it primarily on **Lines Added per Commit** (higher = higher risk). This is the leading indicator of risk.
- High total lines committed indicate POTENTIAL Key Man risk, which increases organizational risk.
- Secondary risk factors (increase risk): High code churn, low test coverage relative to logic changes, high AI-generated probability.
- Mitigating factors (decrease risk): High deletion ratio (refactoring), high test coverage.
- Note if changes are concentrated in high-risk areas (core logic) vs lower-risk areas (docs/config), using directory context. Sole ownership of a section makes that contributor a Key Man, which significantly increases risk.

### 5) "Most Valuable Contributor" Potential
Assess their potential for being the **Most Valuable Contributor** using:
- iterative development (lower lines added per commit)
- evidence of quality assurance (tests touched)
- consistency and sustainability (low churn / good granularity)
- alignment with requirements (only if requirements are present in METRICS)

Do NOT select based on raw LOC alone.
