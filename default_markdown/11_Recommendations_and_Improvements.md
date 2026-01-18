# Recommendations & Improvements
Actionable, evidence-based improvements to reduce technical risk, improve auditability, and increase team velocity. Recommendations must be derived strictly from the provided METRICS and repository change distribution.

INSTRUCTIONS FOR AI (STRICT):
- Use ONLY contributor names, file paths, directories, commit refs, and numeric values explicitly provided in the METRICS section.
- Do NOT invent contributors, commits, branches, tags, technologies, or missing metrics.
- If data needed to support a recommendation is missing, write: **"Not provided in metrics"** and keep it generic.
- Do NOT repeat yourself across recommendations; each recommendation must address a distinct improvement area.
- Each recommendation MUST cite evidence from METRICS (e.g., lines/commit, churn, tests touched, high-risk directories, bulk commit patterns).
- Rank recommendations by **impact**, then by **effort**, and label each accordingly.
- **Merge Commit Rule:** Merge commits MUST be treated separately from standard commits when interpreting:
    - commit frequency
    - Lines Added per Commit
    - churn and bulk-change patterns
      If merge commit counts or merge-specific metrics are NOT provided, explicitly state:
      **"Merge commit breakdown not provided; some commit-based metrics may be skewed."**

REQUIRED OUTPUT FORMAT:
Provide **8–12 recommendations**. For each recommendation, include:

**Title:** (short, action-oriented; max ~60 characters)  
**Severity / Priority:** (High / Medium / Low)  
**Effort:** (Low / Medium / High)  
**Evidence Observed:** (2–5 bullets referencing METRICS)  
**Why It Matters:** (risk/maintainability/security/velocity impact)  
**Action Plan:** (clear steps; include team/process + technical steps)  
**Where:** (directories / file types affected; from METRICS only)  
**Success Criteria:** (how to measure improvement)

MANDATORY RECOMMENDATION CATEGORIES (cover all below if supported by METRICS):

---

## 1) Improve Commit Message Quality (Auditability Boost)
Focus: strengthen traceability without making commit titles too long.

Requirements:
- Titles must be **short** (50–72 chars max) but informative.
- Use descriptive bodies for context (why, not just what).
- Separate refactor/formatting commits from feature commits.
- If merge commits are present in the metrics, recommend merge commit messages that capture:
    - the merged scope/feature area
    - risk notes (tests, major directories impacted)
    - links/references if available in METRICS

Include:
- A recommended format (`type(scope): summary`) with examples.
- Anti-pattern examples (e.g., "update", "fix", "changes").
- Enforcement approach (optional commitlint/CI rule).

---

## 2) Reduce Lines Added per Commit (Lower Risk Score)
Focus: move the team toward more iterative, reviewable changes.

Requirements:
- Reference the risk scale and identify contributors with high lines/commit.
- Recommend splitting work into smaller PRs/commits.
- Encourage commit staging discipline and review chunking.
- **Merge Commit Rule (Category 2):**
    - Do NOT treat merge commits as equivalent to feature commits when evaluating iteration or risk.
    - Prefer recommending risk calculations using **non-merge commits only**, IF such metrics exist.
    - If only total commits are available, explicitly warn that merges may skew LOC/commit and commit count interpretation.

Include:
- Practical tactics (feature flags, incremental PRs, vertical slices).
- Show expected risk reduction outcomes.

---

## 3) Introduce or Strengthen Test Coverage Expectations
Focus: test changes should correlate with core logic / feature complexity.

Requirements:
- If tests are rarely touched compared to source logic, flag as risk.
- Recommend minimum standards:
    - new features => tests required
    - bug fixes => regression tests required
- Encourage fast test suites + CI gating.
- If merge activity is high (if shown in METRICS), recommend ensuring merges are gated on:
    - tests passing
    - coverage expectations (where applicable)

Include:
- What kind of tests: unit/integration/smoke (do not assume frameworks).
- Success metric examples (tests changed per feature PR, failing tests trend).

---

## 4) Control Code Churn (Reduce Rework & Instability)
Focus: high churn reduces stability and increases maintenance cost.

Requirements:
- If churn (add+delete cycles) is high in certain areas, call it out.
- Recommend separating:
    - formatting-only changes
    - refactors
    - feature work
- Recommend improving review discipline around churn-heavy directories.
- **Merge Commit Rule (Category 4):**
    - Do NOT interpret churn caused primarily by merges/rebases as functional churn.
    - If merge-driven churn is detectable in METRICS, recommend improving merge strategy:
        - avoid repeatedly rebasing massive branches
        - keep integration cadence high (smaller merges)
        - ensure refactors are merged separately from features

Include:
- Suggest “refactor-only commits” and churn budgeting.
- Suggest identifying instability hotspots.

---

## 5) Prevent Generated / Build Artifact Noise in Git
Focus: ensure huge LOC spikes aren’t caused by irrelevant artifacts.

Requirements:
- Identify if changes include dist/build outputs, sourcemaps, minified files,
  lockfiles, or huge config dumps (only if present in METRICS).
- Recommend `.gitignore` and build pipeline artifacts storage.
- Recommend review rules that de-emphasize such files in risk scoring.
- If merges often contain these artifacts (if detectable), recommend preventing them from entering merge commits.

Include:
- Specific directory patterns to ignore (from METRICS only).
- Success metric: reduced % of LOC in generated/noise categories.

---

## 6) Establish Refactor Discipline (Make Refactors Safe)
Focus: refactors should improve maintainability without inflating risk.

Requirements:
- Recommend splitting refactors into:
    1) pure move/rename/split commits
    2) behavior-changing commits
- Encourage rename detection and minimizing whitespace changes in refactors.
- If merge activity is high (if shown in METRICS), recommend merging refactors early and separately to reduce long-lived branch divergence.

Include:
- Clear refactor PR guidelines.
- Commit structure examples.

---

## 7) Improve Review Workflow & PR Quality
Focus: increase quality without slowing velocity.

Requirements:
- Recommend PR size targets (e.g., keep changes reviewable).
- Recommend reviewer assignment rules:
    - core logic changes => experienced reviewer
    - infra changes => platform reviewer
- Encourage “risk-based reviewing” using the repo metrics.
- **Merge Commit Rule (Category 7):**
    - If merge commits are present in METRICS, explicitly recognize merge-heavy contributors as performing integration/review work (not necessarily bulk authoring).
    - Recommend separating reporting and incentives for:
        - PR authorship (functional changes)
        - PR integration (merge commits, coordination)
    - Recommend merge strategies that improve auditability:
        - squash merge for noisy histories
        - merge commit for preserved context
          Choose only if merge strategy evidence exists in METRICS; otherwise state "Not provided in metrics".

Include:
- PR checklist examples:
    - tests included
    - scope isolated
    - commit messages meaningful
    - risk note provided if LOC high

---

## 8) Enforce Consistent Repository Structure Ownership
Focus: avoid bottlenecks and unclear code ownership.

Requirements:
- Identify if contributors concentrate heavily in specific areas.
- Recommend lightweight ownership:
    - codeowners / review responsibility mapping
    - directory responsibilities
- If merge responsibility is concentrated (if shown), recommend formalizing integration ownership to prevent burnout and reduce unreviewed merges.

Include:
- Success metric: fewer unreviewed risky commits in core areas.

---

## 9) Improve Documentation / Operational Readiness
Focus: reduce onboarding time and deployment risk.

Requirements:
- Recommend README improvements if the repo seems to lack structure clarity.
- Recommend documenting:
    - build/run steps
    - test steps
    - report generation steps
- Do not invent docs that don’t exist—state if docs presence is unknown.
- If merge practices are prominent (if shown), recommend documenting:
    - merge policy (squash vs merge commit)
    - branch protection expectations
    - review approvals and testing gates

---

## 10) Calibration of Risk Scoring (Meta Recommendation)
Focus: ensure the risk model remains fair and useful.

Requirements:
- Recommend discounting churn from file moves/renames if detected.
- Recommend discounting generated artifacts and lockfile-only commits.
- Recommend separating risk categories:
    - functional risk (core logic)
    - operational risk (infra)
    - noise risk (artifacts)
- **Merge Commit Rule (Category 10):**
    - Ensure risk scoring either:
        - excludes merge commits, OR
        - scores merges separately (integration risk vs authoring risk).
    - If merge/non-merge split is not provided, recommend adding it to METRICS as an enhancement.

Include:
- Suggest a structured “Meaningful Change Score” improvement path.

---

FINAL OUTPUT REQUIREMENTS:
- End with a short **"Top 3 Priority Actions Next Sprint"** list (bulleted).
- If METRICS are insufficient to support a recommendation category, explicitly state:
  **"Insufficient evidence in metrics to recommend changes in this area."**
