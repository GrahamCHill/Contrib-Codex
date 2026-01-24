# Contributor Seniority Estimate (HARSH MODE)

Goal:
Determine each contributor’s estimated seniority level based strictly on Git METRICS
(commit history metadata, file paths, churn/LOC stats, merge stats). This MUST be a harsh,
evidence-based classification.

Allowed Estimated Levels (ONLY these):
- Junior Developer / Engineer
- Mid-level Developer / Engineer
- Senior Developer / Engineer
- Staff / Lead Engineer
- Unclear (insufficient activity)

This section MUST:
- assign an estimated level to each contributor
- be harsh: downgrade aggressively when evidence is weak or hygiene is poor
- treat vague commits as a stronger negative than bulk commits
- avoid over-penalizing initial scaffolding/bootstrapping commits (see rule below)
- treat AI as a potential risk factor AFTER initial scaffolding (see AI rule below)

---

## STRICT INPUT LIMITATIONS
- Use ONLY contributor names, file paths/directories, commit refs, commit message text (if provided), and numeric values explicitly present in METRICS.
- Do NOT inspect or interpret source code contents.
- Do NOT invent missing PRs/issues/processes/tools.
- If a metric is missing, state exactly: **"Not provided in metrics"**

---

## REQUIRED HARSH OUTPUT BEHAVIOR (MANDATORY)
1) You MUST assign each contributor an estimated level from the allowed set.
2) You MUST be harsh:
  - default to lower levels when evidence is weak
  - penalize unclear/noisy patterns
3) Avoid hedging language ("maybe", "could be", "seems").
4) Confidence must track evidence strength:
  - weak evidence → low confidence AND low estimated level
5) "Unclear" is ONLY allowed if activity is too low to judge (if activity metrics exist).

---

## MERGE COMMIT RULE (MANDATORY)
- Merge commits MUST be treated separately from standard commits in scoring.
- If merge commit breakdown is NOT present, you MUST state:
  **"Merge commit breakdown not provided; integration work may be conflated with authoring work."**
- In harsh mode:
  - merges alone MUST NOT increase estimated seniority beyond Mid-level.

---

## INITIAL SCAFFOLDING / BOOTSTRAP RULE (MANDATORY)
Purpose:
Initial repository scaffolding often produces large commits and broad directory touches that are NOT
representative of day-to-day engineering discipline. The model MUST NOT penalize contributors heavily
for legitimate initial scaffolding unless there is evidence the architecture is unstable/unmaintainable.

Definitions:
- "Initial scaffolding / bootstrap" refers to early commits or early change clusters that introduce:
  - project skeleton structure
  - default templates
  - initial config/setup
  - baseline documentation or initial module layout
    This must be inferred ONLY from METRICS (commit refs, date ordering if present, file path patterns).

Rules (Harsh Mode Adjustments):
1) Scaffolding commits MUST NOT dominate atomicity penalties:
  - For category B (Atomicity / Reviewability), if large/broad commits are attributable to initial scaffolding,
    apply at most a LIGHT penalty (cap penalty to -25% of the usual bulk-commit deduction).
2) Scaffolding commits MUST NOT be used as evidence of poor discipline:
  - They cannot trigger Disqualifier 2 (Oversized Bulk Commit Pattern) unless oversized bulk patterns
    persist beyond scaffolding into normal development periods.
3) Architecture maintainability exception (the ONLY case to penalize scaffolding):
  - If METRICS show repeated broad churn across directories soon after scaffolding OR repeated rebuild/restructure
    patterns OR high churn concentrated in structural directories, then scaffolding may be treated as a negative
    signal and fully counted in churn/atomicity.
  - If this evidence is not present, state:
    **"Scaffolding change patterns detected; treated as neutral unless instability is evidenced."**
4) Scaffolding must be explicitly separated in analysis:
  - If identified, you MUST state the period/commits identified as scaffolding.
