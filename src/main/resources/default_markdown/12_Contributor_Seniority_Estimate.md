Prompt: Infer Developer Seniority From Git Metrics (Strict)

Goal:
Determine likely developer seniority bands based on commit quality, engineering discipline, risk management, and repository ownership signals derived strictly from the METRICS section.

INSTRUCTIONS FOR AI (STRICT):
- Use ONLY contributor names, file paths, directories, commit refs, commit message text (if provided), and numeric values explicitly present in the METRICS section.
- Do NOT invent contributors, roles, job titles, teams, technologies/frameworks, processes, tools, or missing metrics.
- Treat all conclusions as probabilistic inferences; do NOT present as fact.
- If data needed to support a claim is missing, write exactly: **"Not provided in metrics"**
- Do NOT infer identity, gender, location, employer, or team structure.
- Do NOT use subjective insults or value judgments; this is a behavioral pattern analysis only.

MERGE COMMIT RULE (MANDATORY):
- Merge commits MUST be treated separately from standard commits when interpreting:
    - commit frequency
    - lines added per commit
    - churn and bulk-change patterns
    - ownership/breadth signals
- If merge commit counts or merge-specific metrics are NOT provided, explicitly state:
  **"Merge commit breakdown not provided; authoring vs integration effort may be conflated."**

WHAT TO INFER:
For each contributor, infer:
1) Likely Seniority Band
2) Confidence Score (0–100)
3) Evidence-based Rationale

Allowed Seniority Bands (ONLY these):
- Junior / Early-career
- Mid-level
- Senior
- Staff / Lead
- Unclear (insufficient evidence)

Seniority band output MUST be based on evidence. If evidence is too sparse or ambiguous:
- Output: Unclear (insufficient evidence)
- Include missing metrics as bullets under Evidence Observed.

SENIORITY SIGNALS (Evidence-weighted heuristics):

A) Engineering Discipline & Risk Control (HIGH WEIGHT)
Evaluate using:
- Average/median Lines Added per Commit (non-merge preferred)
- Frequency of bulk commits (large LOC spikes)
- Presence/absence of generated/build artifact noise
- Separation of refactor-only vs feature commits (if commit message or file/path clustering supports)
- Ratio of small, reviewable changes vs massive changes
  If any required metric is absent: **"Not provided in metrics"**

B) Test & Reliability Behavior (HIGH WEIGHT)
Evaluate using:
- Tests touched relative to source logic changes
- Correlation of tests touched with feature/bugfix activity
- CI/build/test directory changes (if present)
  If test-touch metrics are absent: **"Not provided in metrics"**
  Do NOT assume any test framework.

C) Codebase Ownership / Breadth (MEDIUM WEIGHT)
Evaluate using:
- Breadth of directories touched
- Frequency of changes in core/high-risk directories (as indicated in METRICS)
- Presence in infra/config/security-sensitive areas (ONLY if present in METRICS)
  Do NOT assume any directory is "core" unless METRICS implies it.

D) Commit Message Quality & Intent Clarity (MEDIUM WEIGHT)
Evaluate using:
- Specificity of commit titles (avoid vague: "update", "fix", "changes")
- Consistency and traceability cues (issue refs, scopes) if present
  If commit message metrics are absent: **"Not provided in metrics"**

E) Integration / Coordination Work (MEDIUM WEIGHT)
Evaluate using:
- Merge commits, integration frequency, cross-area changes
- Recognize that merges can imply coordination OR automation
  If automation/bot identity is unknown: note ambiguity.

FAIRNESS / CAUTION RULES:
- Do NOT equate large commits with incompetence; consider role differences and legacy cleanup.
- Do NOT equate merge commits with bulk authoring.
- Always list counter-signals that reduce confidence.
- When evidence is mixed, prefer Mid-level with lower confidence, and explain ambiguity.

REQUIRED OUTPUT FORMAT:

1) CONTRIBUTOR SENIORITY TABLE
   For EACH contributor, output:

Contributor: <name from METRICS>
Likely Band: <one allowed band>
Confidence: <0–100 integer>
Primary Signals: <1–3 short phrases>
Risk Flags / Counter-signals: <1–3 short phrases, may be "Not provided in metrics" if required>
Evidence Observed:
- <2–6 bullets>
    - Each bullet MUST reference METRICS evidence:
        - numeric value(s)
        - and/or directories/file paths
        - and/or commit refs
    - No bullet may be generic without evidence.
      Notes on Uncertainty:
- <1–3 bullets explaining what is missing or ambiguous>

2) SENIORITY DISTRIBUTION SUMMARY
- Count contributors per band
- Identify:
    - coverage gaps (e.g. few high-seniority contributors in core areas)
    - integration concentration risk (merge/integration work concentrated on few people)
- Any claim MUST cite METRICS evidence; otherwise: **"Not provided in metrics"**

3) OPTIONAL: ACTIONABLE RECOMMENDATIONS (4–7)
   Recommendations must improve:
- auditability of seniority inference
- engineering health / risk reduction
  Each recommendation must include:
  Title:
  Severity / Priority: High / Medium / Low
  Effort: Low / Medium / High
  Evidence Observed: (2–5 bullets referencing METRICS)
  Why It Matters:
  Action Plan:
  Where: (directories / file types from METRICS only)
  Success Criteria:

OPTIONAL: EXPLAINABLE SCORING MODEL (only if supported by metrics)
If numeric coverage allows, compute:
Seniority Score (0–100) per contributor.

Suggested weighted components:
- 30% Risk Control (LOC/commit + bulk commit frequency, non-merge preferred)
- 25% Test Behavior (test-to-code delta ratio)
- 15% Churn Discipline
- 15% Commit Message Quality
- 15% Ownership Breadth

If any component is missing:
- exclude the component
- renormalize weights across available components
- explicitly state: **"Not provided in metrics"**

FINAL OUTPUT REQUIREMENTS:
- No invented facts.
- Merge commit separation must be enforced or explicitly flagged missing.
- End with:
  Top 3 Improvements to Make Seniority Inference More Reliable:
- <bullet 1>
- <bullet 2>
- <bullet 3>
- <bullet 4> (if applicable)
- <bullet 5> (if applicable)
