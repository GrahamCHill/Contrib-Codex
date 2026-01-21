# Auditability_and_Traceability_Score.md
Compute an evidence-based Auditability & Traceability Score for a Git repository using ONLY high-level Git metadata and the provided METRICS. This section MUST NOT inspect or interpret actual source code contents.

---

## Purpose
Quantify how reliably the repository history can answer:
- "Why was this change made?"
- "Who changed what, when, and where?"
- "How is a change linked to a requirement, issue, incident, or release?"
- "Can we reconstruct intent and review context from Git history alone?"

This is NOT a security score. It is an auditability score focused on traceability and historical integrity.

---

## INSTRUCTIONS FOR AI (STRICT)
- Use ONLY contributor names, file paths, directories, commit refs, commit message text (if included in METRICS), and numeric values explicitly provided in the METRICS section.
- Do NOT invent contributors, commits, PR numbers, issue IDs, branches, tags, technologies, ticketing systems, or review processes not explicitly mentioned.
- Do NOT infer actual intent beyond what commit messages and structural patterns support.
- If any metric needed to support a claim is missing, write: **"Not provided in metrics"**
- Do NOT analyze or quote repository source code content. Only commit metadata and file path distribution are allowed.

### Merge Commit Rule (MANDATORY)
- Merge commits MUST be treated separately from standard commits when scoring and interpreting:
    - commit message quality
    - frequency patterns
    - bulk-change patterns
    - traceability linkage
- If merge-specific metrics are NOT provided, explicitly state:
  **"Merge commit breakdown not provided; some auditability metrics may be skewed by integration activity."**

---

## REQUIRED OUTPUT
Produce the following sections in order:

1) Auditability & Traceability Score (0–100)
2) Score Breakdown Table (subscores + weights)
3) Evidence Observed (metrics-cited)
4) Top Traceability Failure Modes (ranked)
5) Recommendations to Improve Score (8–12 items, actionable)
6) "Top 3 Priority Actions Next Sprint" (bulleted)

---

## 1) Auditability & Traceability Score (0–100)
Compute a single overall score and classify it:
- 90–100: Excellent (audit-ready)
- 75–89: Good (minor gaps)
- 55–74: Fair (material traceability gaps)
- 35–54: Poor (significant audit risk)
- 0–34: Critical (history not reliable for audit)

You MUST show:
- Overall score
- Confidence (0–100)
- One-sentence summary interpretation

---

## 2) Score Breakdown Table
Provide a table like:

| Category | Weight | Subscore (0–100) | Evidence Basis |
|---------|--------|------------------|----------------|

### Scoring Categories (REQUIRED)
A) Commit Message Quality (Weight: 25%)
B) Change Atomicity / Reviewability (Weight: 20%)
C) Linkage / Reference Hygiene (Weight: 20%)
D) Merge Auditability (Weight: 15%)
E) Structural Traceability (Paths/Ownership Clarity) (Weight: 10%)
F) Noise / Artifact Contamination (Weight: 10%)

Rules:
- Each subscore MUST cite evidence from METRICS (numbers, commit refs, path patterns).
- If a category cannot be computed due to missing metrics, set it to "N/A" and:
    - mark: **"Not provided in metrics"**
    - renormalize the remaining weights to total 100%
    - explicitly state the renormalization

---

## 3) Evidence Observed (METRICS-cited only)
Provide 10–20 bullets. Each bullet MUST include at least one of:
- a numeric metric
- a file path/directory
- a contributor name
- a commit ref

Examples of acceptable evidence bullets:
- "X commits contain message title < 8 characters: 42% (METRICS)"
- "Contributor Alice has avg 900 lines added per non-merge commit (METRICS)"
- "Merge commits touch /core and /infra in 60% of merges (METRICS)"
- "Lockfile-only commits in /package-lock.json occur in N commits (METRICS)"

If the metric is not in METRICS:
- write: **"Not provided in metrics"**

---

## 4) Top Traceability Failure Modes (ranked)
List 6–10 failure modes, each with:

- Failure Mode: (short)
- Severity: High/Medium/Low
- Evidence Observed: (2–5 bullets strictly from METRICS)
- Impact: explain how this damages traceability/audit reconstruction
- Most affected areas: directories / file types (from METRICS only)

Example failure modes:
- Vague commit messages dominate
- Oversized bulk commits reduce intent clarity
- Merge commits lack scope context
- Generated artifacts swamp real changes
- Single contributor owns high-risk change history
- No linkage to external references (issues/incidents/releases)

---

## 5) Recommendations to Improve Score (8–12)
Each recommendation MUST follow this format:

Title: (short, action-oriented; max ~60 chars)
Severity / Priority: (High / Medium / Low)
Effort: (Low / Medium / High)
Score Impact: (+1 to +15 expected improvement)
Evidence Observed: (2–5 bullets referencing METRICS)
Why It Matters: (auditability/maintainability/velocity)
Action Plan:
- Process steps (policy / checklist / CI checks)
- Technical steps (git hooks / templates / enforcement)
  Where: (directories / file types from METRICS only)
  Success Criteria: (measurable metric improvements)

Constraints:
- Recommendations MUST be derived strictly from METRICS.
- No repeated recommendations. Each must cover a distinct root cause.
- If evidence is missing for a category: include:
  **"Insufficient evidence in metrics to recommend changes in this area."**

---

## 6) Top 3 Priority Actions Next Sprint
End with exactly 3 bullets.
Each bullet MUST map directly to a recommendation above.

---

# SCORING GUIDELINES (Evidence-based)

## A) Commit Message Quality (25%)
Score higher when:
- Titles are descriptive (not "update", "fix", "wip")
- Presence of structured patterns: type(scope): summary (ONLY if METRICS shows it)
- Bodies used for context (ONLY if METRICS includes bodies)
  Score lower when:
- Many vague messages
- Missing bodies (if bodies are tracked)
- Inconsistent casing/syntax reduces searchability

If commit message metrics absent:
- "Not provided in metrics"

## B) Change Atomicity / Reviewability (20%)
Score higher when:
- Low average lines changed per commit (non-merge preferred)
- Few bulk commits
- Limited number of files per commit (if tracked)
  Score lower when:
- Large multi-directory commits dominate
- High churn per commit
- Single commits touch too many unrelated areas

## C) Linkage / Reference Hygiene (20%)
Score higher when:
- Commit messages reference issue/ticket IDs (ONLY if METRICS includes patterns/counts)
- Consistent reference patterns
  Score lower when:
- No linkage patterns detectable
  If no linkage metric:
- "Not provided in metrics"

## D) Merge Auditability (15%)
Score higher when:
- Merge commit messages include scope/intent
- Merge commits do not hide large functional changes without description
- Merge strategy evidence supports clean history (ONLY if METRICS shows strategy)
  Score lower when:
- Many merges with generic messages ("merge branch", "merge pull request") and no context
- Merge-driven churn confuses authoring vs integration

If merge data not split:
- state limitation explicitly

## E) Structural Traceability (10%)
Score higher when:
- Directory ownership is clear (contributors map predictably to areas)
- Hotspots align with clear module boundaries
  Score lower when:
- "everything changes everywhere" pattern
- cross-cutting changes dominate

## F) Noise / Artifact Contamination (10%)
Score higher when:
- Low proportion of generated/build artifacts or lockfile-only noise
  Score lower when:
- Large LOC in dist/build outputs or minified files (ONLY if shown in METRICS)
- Frequent churn in vendor/generated directories

---

# FINAL NOTES (MANDATORY)
- All computed scores must cite supporting METRICS evidence.
- Any claim without METRICS backing must be replaced with:
  **"Not provided in metrics"**
- If METRICS contain conflicting signals, explain both and lower confidence.
