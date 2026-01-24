# Repo Time and Effort Estimate (AUTOMATED / METRICS-ONLY / WITH ACCURACY + WORKLOAD RISKS)

Goal:
Estimate the total engineering effort and time required to develop the repository in its current state,
based strictly on the provided Git METRICS. This must be automated and require NO new file inputs.

Additionally, this section MUST include:
1) An **Accuracy / Reality Check** (LLM estimate vs historical execution)
2) A **Workload Risk Assessment** (risks derived from workload intensity, concentration, churn burden, and integration load)
3) A **Comparable-Systems Estimate** based on inferred structure and known patterns of similar systems (see Step E)

---

## STRICT INSTRUCTIONS FOR AI (MANDATORY)
- Use ONLY contributor names, file paths/directories, commit refs, commit timestamps/ranges (if provided),
  and numeric values explicitly present in METRICS.
- Do NOT inspect or interpret repository source code contents.
- Do NOT invent technologies, frameworks, story points, sprint lengths, deployment complexity, or business requirements.
- Do NOT ask for new inputs or new files. This must run fully automated from existing METRICS.
- If data needed for accuracy is missing, you MAY state what would improve accuracy, but you MUST still compute
  an estimate using what is available.
- If a metric is missing, write: **"Not provided in metrics"**
- All estimates MUST be ranges (min / likely / max) and must cite supporting metrics.

---

## MERGE COMMIT RULE (MANDATORY)
- Merge commits MUST be treated separately from standard commits when interpreting:
  - commit counts
  - LOC/commit
  - churn patterns
  - timeline intensity
- If merge breakdown is NOT provided, explicitly state:
  **"Merge commit breakdown not provided; commit-count-based estimates may be skewed."**

---

## WHAT THIS ESTIMATE REPRESENTS (MANDATORY DEFINITION)
This model estimates "engineering effort embodied in repository history", not product value.
It approximates effort using measurable signals:
- total net LOC change
- total churn (adds+deletes)
- commits and change frequency
- complexity proxies (breadth of directories, hotspots, rework)

It does NOT include:
- requirement discovery / product design (unless implied by METRICS, usually not)
- meetings, QA, stakeholder alignment (not measurable here)
- unknown operational/deployment effort unless METRICS shows infra/deploy files

---

# REQUIRED OUTPUT FORMAT

## 1) Summary (Repo Effort Estimate)
Provide:

Estimated Engineering Effort (person-time):
- Person-hours: <min> – <likely> – <max>
- Person-days (8h/day): <min> – <likely> – <max>
- Person-months (160h/mo): <min> – <likely> – <max>

Estimated Calendar Duration (based on team size):
- 1 developer: <min> – <likely> – <max>
- 2 developers: <min> – <likely> – <max>
- 3 developers: <min> – <likely> – <max>
- 5 developers: <min> – <likely> – <max>

Comparable-Systems Calendar Duration Cross-Check (Step E):
- person-hours: <min> – <likely> – <max>
- calendar time (team sizes 1/2/3/5): <min> – <likely> – <max>

Reconciliation Verdict:
- state whether Step E is consistent with Steps A–D:
  - "Consistent"
  - "Some divergence"
  - "Major divergence"
- include 2–6 bullets explaining why divergence exists (METRICS-only)

Confidence (0–100): <value>
Main Uncertainty Drivers:
- <3–6 bullets; metrics limitations>

MANDATORY:
- Include the repo’s observed activity time window if METRICS provide timestamps.
- If timestamps not provided: state **"Not provided in metrics"** and derive time using commits only.

---

## 2) Evidence Used (Metrics-only)
Provide 8–16 bullets. Each bullet MUST include at least one:
- numeric metric (LOC, churn, commits, contributors, tests touched, file counts)
- directory/file pattern
- date range (if available)

Example evidence bullet format:
- "Total lines added: X; total lines deleted: Y; churn: X+Y (METRICS)"
- "Total commits: N; unique contributors: M (METRICS)"
- "Top churn directory: /src/core with churn Z (METRICS)"

---

## 3) Estimation Method (Explainable / Deterministic)
You MUST compute the estimate using the following layered model:

### Step A: Compute Baseline Effort from Churn
Let:
- A = total lines added (non-merge preferred)
- D = total lines deleted (non-merge preferred)
- C = churn = A + D

Compute baseline effort:
- Baseline person-hours = C / ProductivityRate

ProductivityRate MUST be chosen from the following table based on repo signals:

Productivity Rate Selection (choose ONE, justify with METRICS):
- 20 churn LOC/hour (complex / high rework / low tests / high-risk dirs dominate)
- 35 churn LOC/hour (typical mixed repo)
- 50 churn LOC/hour (docs/config heavy / low complexity signals)

Rules:
- If tests are rarely touched + core churn is high → use 20 LOC/hr
- If churn is moderate + structure stable → use 35 LOC/hr
- If mostly docs/templates/config → use 50 LOC/hr
  If required metrics missing: default to 35 LOC/hr and state limitation.

### Step B: Apply Rework & Instability Multiplier
Compute an instability factor IF churn metrics exist:

Instability multiplier:
- Low churn discipline / high thrash → x1.4
- Moderate → x1.2
- Stable → x1.0

If churn instability evidence missing: use x1.15 default and state **"Not provided in metrics"**.

### Step C: Apply Architecture Breadth Multiplier
If METRICS include directory breadth / top directory distribution:
- Many directories touched broadly + cross-cutting changes → x1.2
- Moderate → x1.1
- Narrow → x1.0
  If missing: x1.1 default.

### Step D: Apply Auditability / Process Overhead Factor
Approximate "process overhead" from commit hygiene:
- Vague commits dominate → x1.15
- Mixed → x1.08
- Strong hygiene → x1.03
  If commit message quality metrics missing: x1.08 default.

Final effort:
EffortHours = BaselineHours * InstabilityMultiplier * BreadthMultiplier * OverheadFactor

You MUST show each multiplier and cite METRICS evidence for why it was chosen.

---

### Step E (Comparable-Systems Estimate Cross-Check) (MANDATORY)
Purpose:
Compute a second independent estimate based on inferred project structure and inferred goal,
using generalized prior knowledge of similar repository types. This is a cross-check to validate realism.

IMPORTANT RESTRICTIONS:
- You MUST infer repo type ONLY from file paths/directories/file types present in METRICS.
- You MUST NOT assume languages, frameworks, or cloud platforms unless evidenced by METRICS.
- You MUST NOT use code contents.
- If repo type cannot be inferred: **"Not provided in metrics"**, and SKIP Step E estimate.

#### Step E.1 Infer Repo Type (METRICS-only)
Pick ONE (only if supported):
- "CLI tool / scripting utility"
- "Library / SDK"
- "Web app"
- "Backend service"
- "Desktop application"
- "Data pipeline / ETL"
- "Documentation/templates-heavy repo"
- "Multi-component mixed repo"
  If uncertain: **"Not provided in metrics"**

#### Step E.2 Infer Delivery Complexity Tier
Choose one tier and justify with METRICS:
- Tier 1 (Small): narrow directory set, limited hotspots, low churn
- Tier 2 (Medium): multiple dirs/components, moderate churn, clear hotspots
- Tier 3 (Large): broad directory spread, high churn, multi-hotspot instability
  If insufficient: **"Not provided in metrics"**

#### Step E.3 Comparable-Systems Baseline
Using Tier + inferred repo type, output a baseline effort range:
- Tier 1: 40–240 hours
- Tier 2: 240–1200 hours
- Tier 3: 1200–6000 hours

Adjustments (METRICS-only):
- if churn is very high relative to repo size signals → push upward
- if mostly docs/templates/config churn → push downward
- if high test adjacency → modest reduction
- if high rework churn → increase

#### Step E.4 Output Comparable Estimate
Provide:
- ComparableEffortHours: <min> – <likely> – <max>
- ComparableCalendarWeeks for team size 1/2/3/5 using same availability factor rules
- ComparableConfidence (0–100) + limitations

---

## 4) Effort Breakdown (Only if supported by METRICS)
If METRICS include per-contributor and/or per-directory totals, output:

### Breakdown by Contributor
| Contributor | Effort Share % | Effort Hours (range) | Evidence |
|-----------:|----------------:|----------------------|---------|

### Breakdown by Directory
| Directory | Effort Share % | Effort Hours (range) | Evidence |
|----------:|----------------:|----------------------|---------|

Rules:
- Use churn share as the primary proxy for effort share.
- If per-contributor churn isn’t provided: state **"Not provided in metrics"** and omit that table.

---

## 5) Calendarization Model (Time-to-build)
Convert effort hours to calendar time.

Assumptions (must be stated):
- Dev availability factor:
  - 60% (typical: meetings/review/context switching)
  - If repo shows high integration/review overhead (merge-heavy), use 50%
  - If repo is small/simple, use 70%

Rules:
- If merge breakdown missing: availability defaults to 60%.

For team size N:
CalendarWeeks = EffortHours / (N * 40 * AvailabilityFactor)

Output per team size:
- min / likely / max
  Where:
- min uses best-case multipliers (lower)
- likely uses chosen multipliers
- max uses worst-case multipliers (higher)

---

## 6) LLM Estimate vs Repo Reality (MANDATORY ACCURACY CHECK)
Purpose:
Validate the LLM estimation quality by comparing:
- what the model estimated effort/time should have been
  vs
- what the repository history indicates was actually executed over time

### 6.1 Observed Historical Output Rate (from METRICS)
If timestamps/time window are provided:
- Observed time window: <start> → <end>
  Compute:
- churn per week = C / weeks
- churn per month = C / months
- commits per week = commits / weeks
- contributor activity spread (if available)

If timestamps missing:
- state: **"Not provided in metrics"**
- fall back to commit-based intensity only:
  - churn per commit
  - churn per contributor

### 6.2 Derived Observed Effort (History-Implied Effort)
Compute an observed-effort estimate using the SAME productivity model.

If timestamps exist:
ObservedCalendarWeeks_actual = weeks(start→end)
ObservedEffectiveTeamSize = EffortHours / (ObservedCalendarWeeks_actual * 40 * AvailabilityFactor)

You MUST output:
- ObservedCalendarWeeks_actual (or "Not provided in metrics")
- ObservedEffectiveTeamSize (or "Not provided in metrics")
- Output pattern classification (METRICS only):
  - "slow and steady"
  - "burst-driven"
  - "high sustained throughput"
    If unsupported: **"Not provided in metrics"**

### 6.3 Accuracy / Delta Report
Compute and report deltas:

Effort Delta:
- EstimatedEffortHours_likely vs ObservedEffortHours_likely
- Delta% = (Estimated - Observed) / Observed * 100

Calendar Delta (only if timestamps exist):
- EstimatedCalendarWeeks_likely (for team size ≈ ObservedEffectiveTeamSize)
- vs ObservedCalendarWeeks_actual
- Delta% = (EstimatedWeeks - ObservedWeeks) / ObservedWeeks * 100

Interpretation Rules:
- |Delta| <= 15% → "Accurate"
- 16–35% → "Moderately off"
- >35% → "Poor fit / metrics mismatch"

MANDATORY OUTPUT:
- "Accuracy Grade: Accurate / Moderately off / Poor fit"
- "Why accuracy is high/low" with 3–8 METRICS-cited bullets

---

## 7) Workload & Delivery Risks (MANDATORY / METRICS-ONLY)
Purpose:
State risks derived from workload intensity and distribution. This MUST focus on workload,
burnout risk, integration risk, schedule risk, and concentration risk — NOT code-level security.

You MUST provide 8–14 risks. For each risk include:

Risk Title: (short)
Severity: High / Medium / Low
Evidence Observed: (2–6 bullets strictly from METRICS)
Why This Matters: (delivery risk / burnout risk / quality risk)
Mitigation: (actionable, process-oriented + technical hygiene)
Where: (directories/contributors from METRICS only)
Success Metric: (what improves in METRICS)

### Workload risk categories you MUST evaluate (only if METRICS supports):
1) Workload concentration (top contributor share; bus factor)
2) Integration burden (merge concentration)
3) Oversized changes increase review load (LOC/commit spikes)
4) Churn thrash indicates rework tax
5) Low test adjacency increases defect rework time
6) Hotspot risk (most-changed directories)
7) Time clustering / burst work (late spikes; intense periods)
8) Long-running instability in core directories
9) Documentation drift (if doc churn high relative to code)
10) Onboarding dependency (single maintainer patterns)

If a category cannot be evaluated:
- include the risk entry but write:
  **"Not provided in metrics"** in Evidence Observed and keep mitigation generic.

MANDATORY framing:
- Risks MUST be phrased primarily as workload/delivery risk.
  Examples:
- "Burnout risk: integration load concentrated on <name>"
- "Schedule risk: oversized commits inflate review cycle time"
- "Quality risk via rework: churn thrash in <dir> causes repeat work"
- "Single point of failure risk in <dir> from ownership concentration"

### REQUIRED ADDITION: Per-Contributor Throughput Analysis (PART OF SECTION 7)
This is NOT a separate section; it MUST be embedded inside Workload & Delivery Risks output.

You MUST add:
A) A throughput table per contributor
B) Outlier detection
C) Under-engagement red-flag rule
D) Junior low-activity protection rule

#### 7.A Per-Contributor Throughput Table (Required)
If METRICS provide timestamps and per-contributor churn/commits:
Compute per contributor:
- Active window: first_commit_date → last_commit_date
- Active weeks
- Commits/week
- Churn/week (adds+deletes per week)
- Repo churn share %
- Repo commit share %

Output table:
| Contributor | Active Window | Commits/week | Churn/week | Repo Churn Share % | Repo Commit Share % | Notes |
|-----------:|--------------:|-------------:|-----------:|-------------------:|--------------------:|------|

If timestamps missing:
- state: **"Not provided in metrics"**
- fall back to totals:
  | Contributor | Total Commits | Total Churn | Repo Churn Share % | Notes |
  |-----------:|--------------:|------------:|-------------------:|------|

#### 7.B Low Throughput Outlier Detection (Required)
Compare each contributor’s throughput to team distribution.

Compute:
- median commits/week across contributors
- median churn/week across contributors
- percentile rank if computable

Flag as "Low Throughput Outlier" if ANY:
- churn/week < 25% of team median
- commits/week < 25% of team median
- contributor active window >= repo median active window AND throughput is bottom quartile

If team size <= 3:
- thresholds become 15% instead of 25%

If required values cannot be computed:
- state: **"Not provided in metrics"**
- do not apply outlier logic; provide descriptive stats only.

#### 7.C Under-Engagement Red Flag Rule (MANDATORY)
If contributor is a Low Throughput Outlier:
- you MUST explicitly label:
  **"Potential Red Flag: Under-engagement risk"**
- include:
  - Severity (High/Medium/Low)
  - Evidence Observed (METRICS-only)

Default interpretation (harsh):
- low throughput is a risk signal unless METRICS explicitly prove external project allocation.

External allocation exemption:
- Only exempt if METRICS explicitly indicate they are working elsewhere or have a clear alternative role.
- If no such evidence exists, you MUST state:
  **"No evidence in metrics of external project allocation; flagged as risk."**
- You may NOT speculate or excuse low throughput without METRICS evidence.

#### 7.D Junior Low-Activity Protection Rule (MANDATORY)
To avoid unfair penalization:

- If contributor has < 4 total commits (non-merge preferred; if merge breakdown missing use total commits)
  AND they are determined by the LLM to be:
  **"Junior Developer / Engineer"**
  THEN:

  1) They MUST NOT be flagged as "Potential Red Flag: Under-engagement risk"
     solely due to low throughput or low commit rate.
  2) They MUST be excluded from outlier distribution calculations (median/percentiles).
  3) Their throughput MUST be labeled:
     **"Low activity - junior/onboarding safe"**
     unless METRICS contain explicit negative evidence (e.g., multiple reverts directly linked to them).

Allowed exception:
- If METRICS explicitly show harmful patterns even with <4 commits (reverts/hotfixes, extreme churn thrash),
  flagging is allowed but MUST be:
  - Severity: Low or Medium (never High)
  - Evidence Observed: must cite METRICS

If seniority estimate is missing:
- state: **"Not provided in metrics"**
- apply rule conservatively: do NOT flag contributors with <4 commits.

---

## 8) What Would Make This Estimate More Accurate? (No new inputs required)
You MUST list 6–12 items that would improve accuracy IF they were included in METRICS generation,
but you must not request new repository scans beyond the automated pipeline.

Examples (allowed):
- non-merge vs merge commit split
- file count per commit
- median LOC/commit per contributor
- churn by directory over time
- test-touch ratio by directory
- revert/hotfix commit counts
- rename/move detection rates (to reduce false churn)
- active-days per contributor (to better estimate effective team size)

Each item must be phrased:
"Would improve accuracy if METRICS included: <metric>"

---

## 9) Estimation Divergence Risk (Actual vs Estimated)
Purpose:
Explain the risks when the observed repository development period is much larger or much smaller
than the model’s estimated calendar duration. This section exists to enforce estimation quality
and identify where the model may be mis-calibrated.

STRICT RULES:
- Use ONLY METRICS evidence: timestamps/time window, churn, commits, contributor counts, merge ratio (if any).
- Do NOT invent external factors (funding, staffing changes, requirements churn) unless explicitly evidenced in METRICS.
- If the repo time window is missing, write: **"Not provided in metrics"** and only provide generic risks.

### 9.1 Key Definitions (Metrics-only)
- Estimated calendar duration (likely): <value from section 1>
- Observed repo duration: <end-start from METRICS>
- Divergence Ratio (DR) = ObservedDuration / EstimatedDuration

Interpretation:
- DR ~ 1.0 → estimate aligned
- DR >> 1.0 → repo took longer than estimated
- DR << 1.0 → repo completed faster than estimated

If ObservedDuration cannot be computed:
- **"Not provided in metrics"** (and skip ratio-based claims)

### 9.2 Divergence Classification
Use thresholds:
- DR <= 0.50   → "Much faster than estimated"
- DR 0.51–0.85 → "Faster than estimated"
- DR 0.86–1.15 → "Aligned"
- DR 1.16–1.75 → "Slower than estimated"
- DR > 1.75    → "Much slower than estimated"

Output:
- Divergence classification: <label>
- DR value: <number>
- Confidence: <0–100> (lower if merge breakdown missing)

### 9.3 Risks if Observed Duration is MUCH LARGER than Estimated (DR > 1.75)
Provide 6–10 risks focusing on workload/process (NOT code security).
Mandatory themes (include if supported):
1) Underestimated integration tax (merge-heavy)
2) Rework tax (high churn / repeated revisits)
3) Review bottleneck (oversized commits)
4) Single point of failure / bus factor (concentration)
5) Low test adjacency causing rework
6) Process debt (vague commits reducing auditability → slows team)

### 9.4 Risks if Observed Duration is MUCH SMALLER than Estimated (DR <= 0.50)
Provide 6–10 risks focusing on sustainability/quality gate risks (NOT moralizing).
Mandatory themes (include if supported):
1) Burnout risk (burst-driven output intensity)
2) Oversized commits increase defect probability
3) Reduced review rigor (commit volume spikes)
4) Low test adjacency suggests quality debt
5) AI-acceleration risk post-scaffolding (only if evidenced)
6) Auditability risk (vague messages despite high velocity)

### 9.5 Calibration Actions (Model Improvement Loop)
Output 4–8 automated calibration steps:
- adjust ProductivityRate using observed churn/week
- discount churn for renames/moves if detectable
- separate merge churn from author churn
- exclude scaffolding window from calibration
- introduce per-repo baseline productivity derived from stable periods
  If needed data missing: **"Not provided in metrics"**

### 9.6 Final Determination
Conclude with:
- "Estimation reliability: High / Medium / Low"
- "Primary reason" (metrics-cited)
- "Next calibration step" (one actionable item)

---

## FINAL REQUIREMENTS
- Must output deterministic estimates from existing METRICS.
- Must not require any new file inputs.
- Must explicitly state missing metrics with: **"Not provided in metrics"**
- Must include section "LLM Estimate vs Repo Reality" and output an Accuracy Grade.
- Must include "Workload & Delivery Risks" section with workload-focused risks AND per-contributor throughput red flag logic.
- Must include Step E comparable-systems cross-check.
- Must end with:
  "Top 3 Metrics Additions That Would Most Improve Effort Accuracy"
  (3 bullets)
# Repo Time and Effort Estimate (AUTOMATED / METRICS-ONLY / WITH ACCURACY + WORKLOAD RISKS)

Goal:
Estimate the total engineering effort and time required to develop the repository in its current state,
based strictly on the provided Git METRICS. This must be automated and require NO new file inputs.

Additionally, this section MUST include:
1) An **Accuracy / Reality Check** (LLM estimate vs historical execution)
2) A **Workload Risk Assessment** (risks derived from workload intensity, concentration, churn burden, and integration load)
3) A **Comparable-Systems Estimate** based on inferred structure and known patterns of similar systems (see Step E)

---

## STRICT INSTRUCTIONS FOR AI (MANDATORY)
- Use ONLY contributor names, file paths/directories, commit refs, commit timestamps/ranges (if provided),
  and numeric values explicitly present in METRICS.
- Do NOT inspect or interpret repository source code contents.
- Do NOT invent technologies, frameworks, story points, sprint lengths, deployment complexity, or business requirements.
- Do NOT ask for new inputs or new files. This must run fully automated from existing METRICS.
- If data needed for accuracy is missing, you MAY state what would improve accuracy, but you MUST still compute
  an estimate using what is available.
- If a metric is missing, write: **"Not provided in metrics"**
- All estimates MUST be ranges (min / likely / max) and must cite supporting metrics.

---

## MERGE COMMIT RULE (MANDATORY)
- Merge commits MUST be treated separately from standard commits when interpreting:
  - commit counts
  - LOC/commit
  - churn patterns
  - timeline intensity
- If merge breakdown is NOT provided, explicitly state:
  **"Merge commit breakdown not provided; commit-count-based estimates may be skewed."**

---

## WHAT THIS ESTIMATE REPRESENTS (MANDATORY DEFINITION)
This model estimates "engineering effort embodied in repository history", not product value.
It approximates effort using measurable signals:
- total net LOC change
- total churn (adds+deletes)
- commits and change frequency
- complexity proxies (breadth of directories, hotspots, rework)

It does NOT include:
- requirement discovery / product design (unless implied by METRICS, usually not)
- meetings, QA, stakeholder alignment (not measurable here)
- unknown operational/deployment effort unless METRICS shows infra/deploy files

---

# REQUIRED OUTPUT FORMAT

## 1) Summary (Repo Effort Estimate)
Provide:

Estimated Engineering Effort (person-time):
- Person-hours: <min> – <likely> – <max>
- Person-days (8h/day): <min> – <likely> – <max>
- Person-months (160h/mo): <min> – <likely> – <max>

Estimated Calendar Duration (based on team size):
- 1 developer: <min> – <likely> – <max>
- 2 developers: <min> – <likely> – <max>
- 3 developers: <min> – <likely> – <max>
- 5 developers: <min> – <likely> – <max>

Comparable-Systems Calendar Duration Cross-Check (Step E):
- person-hours: <min> – <likely> – <max>
- calendar time (team sizes 1/2/3/5): <min> – <likely> – <max>

Reconciliation Verdict:
- state whether Step E is consistent with Steps A–D:
  - "Consistent"
  - "Some divergence"
  - "Major divergence"
- include 2–6 bullets explaining why divergence exists (METRICS-only)

Confidence (0–100): <value>
Main Uncertainty Drivers:
- <3–6 bullets; metrics limitations>

MANDATORY:
- Include the repo’s observed activity time window if METRICS provide timestamps.
- If timestamps not provided: state **"Not provided in metrics"** and derive time using commits only.

---

## 2) Evidence Used (Metrics-only)
Provide 8–16 bullets. Each bullet MUST include at least one:
- numeric metric (LOC, churn, commits, contributors, tests touched, file counts)
- directory/file pattern
- date range (if available)

Example evidence bullet format:
- "Total lines added: X; total lines deleted: Y; churn: X+Y (METRICS)"
- "Total commits: N; unique contributors: M (METRICS)"
- "Top churn directory: /src/core with churn Z (METRICS)"

---

## 3) Estimation Method (Explainable / Deterministic)
You MUST compute the estimate using the following layered model:

### Step A: Compute Baseline Effort from Churn
Let:
- A = total lines added (non-merge preferred)
- D = total lines deleted (non-merge preferred)
- C = churn = A + D

Compute baseline effort:
- Baseline person-hours = C / ProductivityRate

ProductivityRate MUST be chosen from the following table based on repo signals:

Productivity Rate Selection (choose ONE, justify with METRICS):
- 20 churn LOC/hour (complex / high rework / low tests / high-risk dirs dominate)
- 35 churn LOC/hour (typical mixed repo)
- 50 churn LOC/hour (docs/config heavy / low complexity signals)

Rules:
- If tests are rarely touched + core churn is high → use 20 LOC/hr
- If churn is moderate + structure stable → use 35 LOC/hr
- If mostly docs/templates/config → use 50 LOC/hr
  If required metrics missing: default to 35 LOC/hr and state limitation.

### Step B: Apply Rework & Instability Multiplier
Compute an instability factor IF churn metrics exist:

Instability multiplier:
- Low churn discipline / high thrash → x1.4
- Moderate → x1.2
- Stable → x1.0

If churn instability evidence missing: use x1.15 default and state **"Not provided in metrics"**.

### Step C: Apply Architecture Breadth Multiplier
If METRICS include directory breadth / top directory distribution:
- Many directories touched broadly + cross-cutting changes → x1.2
- Moderate → x1.1
- Narrow → x1.0
  If missing: x1.1 default.

### Step D: Apply Auditability / Process Overhead Factor
Approximate "process overhead" from commit hygiene:
- Vague commits dominate → x1.15
- Mixed → x1.08
- Strong hygiene → x1.03
  If commit message quality metrics missing: x1.08 default.

Final effort:
EffortHours = BaselineHours * InstabilityMultiplier * BreadthMultiplier * OverheadFactor

You MUST show each multiplier and cite METRICS evidence for why it was chosen.

---

### Step E (Comparable-Systems Estimate Cross-Check) (MANDATORY)
Purpose:
Compute a second independent estimate based on inferred project structure and inferred goal,
using generalized prior knowledge of similar repository types. This is a cross-check to validate realism.

IMPORTANT RESTRICTIONS:
- You MUST infer repo type ONLY from file paths/directories/file types present in METRICS.
- You MUST NOT assume languages, frameworks, or cloud platforms unless evidenced by METRICS.
- You MUST NOT use code contents.
- If repo type cannot be inferred: **"Not provided in metrics"**, and SKIP Step E estimate.

#### Step E.1 Infer Repo Type (METRICS-only)
Pick ONE (only if supported):
- "CLI tool / scripting utility"
- "Library / SDK"
- "Web app"
- "Backend service"
- "Desktop application"
- "Data pipeline / ETL"
- "Documentation/templates-heavy repo"
- "Multi-component mixed repo"
  If uncertain: **"Not provided in metrics"**

#### Step E.2 Infer Delivery Complexity Tier
Choose one tier and justify with METRICS:
- Tier 1 (Small): narrow directory set, limited hotspots, low churn
- Tier 2 (Medium): multiple dirs/components, moderate churn, clear hotspots
- Tier 3 (Large): broad directory spread, high churn, multi-hotspot instability
  If insufficient: **"Not provided in metrics"**

#### Step E.3 Comparable-Systems Baseline
Using Tier + inferred repo type, output a baseline effort range:
- Tier 1: 40–240 hours
- Tier 2: 240–1200 hours
- Tier 3: 1200–6000 hours

Adjustments (METRICS-only):
- if churn is very high relative to repo size signals → push upward
- if mostly docs/templates/config churn → push downward
- if high test adjacency → modest reduction
- if high rework churn → increase

#### Step E.4 Output Comparable Estimate
Provide:
- ComparableEffortHours: <min> – <likely> – <max>
- ComparableCalendarWeeks for team size 1/2/3/5 using same availability factor rules
- ComparableConfidence (0–100) + limitations

---

## 4) Effort Breakdown (Only if supported by METRICS)
If METRICS include per-contributor and/or per-directory totals, output:

### Breakdown by Contributor
| Contributor | Effort Share % | Effort Hours (range) | Evidence |
|-----------:|----------------:|----------------------|---------|

### Breakdown by Directory
| Directory | Effort Share % | Effort Hours (range) | Evidence |
|----------:|----------------:|----------------------|---------|

Rules:
- Use churn share as the primary proxy for effort share.
- If per-contributor churn isn’t provided: state **"Not provided in metrics"** and omit that table.

---

## 5) Calendarization Model (Time-to-build)
Convert effort hours to calendar time.

Assumptions (must be stated):
- Dev availability factor:
  - 60% (typical: meetings/review/context switching)
  - If repo shows high integration/review overhead (merge-heavy), use 50%
  - If repo is small/simple, use 70%

Rules:
- If merge breakdown missing: availability defaults to 60%.

For team size N:
CalendarWeeks = EffortHours / (N * 40 * AvailabilityFactor)

Output per team size:
- min / likely / max
  Where:
- min uses best-case multipliers (lower)
- likely uses chosen multipliers
- max uses worst-case multipliers (higher)

---

## 6) LLM Estimate vs Repo Reality (MANDATORY ACCURACY CHECK)
Purpose:
Validate the LLM estimation quality by comparing:
- what the model estimated effort/time should have been
  vs
- what the repository history indicates was actually executed over time

### 6.1 Observed Historical Output Rate (from METRICS)
If timestamps/time window are provided:
- Observed time window: <start> → <end>
  Compute:
- churn per week = C / weeks
- churn per month = C / months
- commits per week = commits / weeks
- contributor activity spread (if available)

If timestamps missing:
- state: **"Not provided in metrics"**
- fall back to commit-based intensity only:
  - churn per commit
  - churn per contributor

### 6.2 Derived Observed Effort (History-Implied Effort)
Compute an observed-effort estimate using the SAME productivity model.

If timestamps exist:
ObservedCalendarWeeks_actual = weeks(start→end)
ObservedEffectiveTeamSize = EffortHours / (ObservedCalendarWeeks_actual * 40 * AvailabilityFactor)

You MUST output:
- ObservedCalendarWeeks_actual (or "Not provided in metrics")
- ObservedEffectiveTeamSize (or "Not provided in metrics")
- Output pattern classification (METRICS only):
  - "slow and steady"
  - "burst-driven"
  - "high sustained throughput"
    If unsupported: **"Not provided in metrics"**

### 6.3 Accuracy / Delta Report
Compute and report deltas:

Effort Delta:
- EstimatedEffortHours_likely vs ObservedEffortHours_likely
- Delta% = (Estimated - Observed) / Observed * 100

Calendar Delta (only if timestamps exist):
- EstimatedCalendarWeeks_likely (for team size ≈ ObservedEffectiveTeamSize)
- vs ObservedCalendarWeeks_actual
- Delta% = (EstimatedWeeks - ObservedWeeks) / ObservedWeeks * 100

Interpretation Rules:
- |Delta| <= 15% → "Accurate"
- 16–35% → "Moderately off"
- >35% → "Poor fit / metrics mismatch"

MANDATORY OUTPUT:
- "Accuracy Grade: Accurate / Moderately off / Poor fit"
- "Why accuracy is high/low" with 3–8 METRICS-cited bullets

---

## 7) Workload & Delivery Risks (MANDATORY / METRICS-ONLY)
Purpose:
State risks derived from workload intensity and distribution. This MUST focus on workload,
burnout risk, integration risk, schedule risk, and concentration risk — NOT code-level security.

You MUST provide 8–14 risks. For each risk include:

Risk Title: (short)
Severity: High / Medium / Low
Evidence Observed: (2–6 bullets strictly from METRICS)
Why This Matters: (delivery risk / burnout risk / quality risk)
Mitigation: (actionable, process-oriented + technical hygiene)
Where: (directories/contributors from METRICS only)
Success Metric: (what improves in METRICS)

### Workload risk categories you MUST evaluate (only if METRICS supports):
1) Workload concentration (top contributor share; bus factor)
2) Integration burden (merge concentration)
3) Oversized changes increase review load (LOC/commit spikes)
4) Churn thrash indicates rework tax
5) Low test adjacency increases defect rework time
6) Hotspot risk (most-changed directories)
7) Time clustering / burst work (late spikes; intense periods)
8) Long-running instability in core directories
9) Documentation drift (if doc churn high relative to code)
10) Onboarding dependency (single maintainer patterns)

If a category cannot be evaluated:
- include the risk entry but write:
  **"Not provided in metrics"** in Evidence Observed and keep mitigation generic.

MANDATORY framing:
- Risks MUST be phrased primarily as workload/delivery risk.
  Examples:
- "Burnout risk: integration load concentrated on <name>"
- "Schedule risk: oversized commits inflate review cycle time"
- "Quality risk via rework: churn thrash in <dir> causes repeat work"
- "Single point of failure risk in <dir> from ownership concentration"

### REQUIRED ADDITION: Per-Contributor Throughput Analysis (PART OF SECTION 7)
This is NOT a separate section; it MUST be embedded inside Workload & Delivery Risks output.

You MUST add:
A) A throughput table per contributor
B) Outlier detection
C) Under-engagement red-flag rule
D) Junior low-activity protection rule

#### 7.A Per-Contributor Throughput Table (Required)
If METRICS provide timestamps and per-contributor churn/commits:
Compute per contributor:
- Active window: first_commit_date → last_commit_date
- Active weeks
- Commits/week
- Churn/week (adds+deletes per week)
- Repo churn share %
- Repo commit share %

Output table:
| Contributor | Active Window | Commits/week | Churn/week | Repo Churn Share % | Repo Commit Share % | Notes |
|-----------:|--------------:|-------------:|-----------:|-------------------:|--------------------:|------|

If timestamps missing:
- state: **"Not provided in metrics"**
- fall back to totals:
  | Contributor | Total Commits | Total Churn | Repo Churn Share % | Notes |
  |-----------:|--------------:|------------:|-------------------:|------|

#### 7.B Low Throughput Outlier Detection (Required)
Compare each contributor’s throughput to team distribution.

Compute:
- median commits/week across contributors
- median churn/week across contributors
- percentile rank if computable

Flag as "Low Throughput Outlier" if ANY:
- churn/week < 25% of team median
- commits/week < 25% of team median
- contributor active window >= repo median active window AND throughput is bottom quartile

If team size <= 3:
- thresholds become 15% instead of 25%

If required values cannot be computed:
- state: **"Not provided in metrics"**
- do not apply outlier logic; provide descriptive stats only.

#### 7.C Under-Engagement Red Flag Rule (MANDATORY)
If contributor is a Low Throughput Outlier:
- you MUST explicitly label:
  **"Potential Red Flag: Under-engagement risk"**
- include:
  - Severity (High/Medium/Low)
  - Evidence Observed (METRICS-only)

Default interpretation (harsh):
- low throughput is a risk signal unless METRICS explicitly prove external project allocation.

External allocation exemption:
- Only exempt if METRICS explicitly indicate they are working elsewhere or have a clear alternative role.
- If no such evidence exists, you MUST state:
  **"No evidence in metrics of external project allocation; flagged as risk."**
- You may NOT speculate or excuse low throughput without METRICS evidence.

#### 7.D Junior Low-Activity Protection Rule (MANDATORY)
To avoid unfair penalization:

- If contributor has < 4 total commits (non-merge preferred; if merge breakdown missing use total commits)
  AND they are determined by the LLM to be:
  **"Junior Developer / Engineer"**
  THEN:

  1) They MUST NOT be flagged as "Potential Red Flag: Under-engagement risk"
     solely due to low throughput or low commit rate.
  2) They MUST be excluded from outlier distribution calculations (median/percentiles).
  3) Their throughput MUST be labeled:
     **"Low activity - junior/onboarding safe"**
     unless METRICS contain explicit negative evidence (e.g., multiple reverts directly linked to them).

Allowed exception:
- If METRICS explicitly show harmful patterns even with <4 commits (reverts/hotfixes, extreme churn thrash),
  flagging is allowed but MUST be:
  - Severity: Low or Medium (never High)
  - Evidence Observed: must cite METRICS

If seniority estimate is missing:
- state: **"Not provided in metrics"**
- apply rule conservatively: do NOT flag contributors with <4 commits.

---

## 8) What Would Make This Estimate More Accurate? (No new inputs required)
You MUST list 6–12 items that would improve accuracy IF they were included in METRICS generation,
but you must not request new repository scans beyond the automated pipeline.

Examples (allowed):
- non-merge vs merge commit split
- file count per commit
- median LOC/commit per contributor
- churn by directory over time
- test-touch ratio by directory
- revert/hotfix commit counts
- rename/move detection rates (to reduce false churn)
- active-days per contributor (to better estimate effective team size)

Each item must be phrased:
"Would improve accuracy if METRICS included: <metric>"

---

## 9) Estimation Divergence Risk (Actual vs Estimated)
Purpose:
Explain the risks when the observed repository development period is much larger or much smaller
than the model’s estimated calendar duration. This section exists to enforce estimation quality
and identify where the model may be mis-calibrated.

STRICT RULES:
- Use ONLY METRICS evidence: timestamps/time window, churn, commits, contributor counts, merge ratio (if any).
- Do NOT invent external factors (funding, staffing changes, requirements churn) unless explicitly evidenced in METRICS.
- If the repo time window is missing, write: **"Not provided in metrics"** and only provide generic risks.

### 9.1 Key Definitions (Metrics-only)
- Estimated calendar duration (likely): <value from section 1>
- Observed repo duration: <end-start from METRICS>
- Divergence Ratio (DR) = ObservedDuration / EstimatedDuration

Interpretation:
- DR ~ 1.0 → estimate aligned
- DR >> 1.0 → repo took longer than estimated
- DR << 1.0 → repo completed faster than estimated

If ObservedDuration cannot be computed:
- **"Not provided in metrics"** (and skip ratio-based claims)

### 9.2 Divergence Classification
Use thresholds:
- DR <= 0.50   → "Much faster than estimated"
- DR 0.51–0.85 → "Faster than estimated"
- DR 0.86–1.15 → "Aligned"
- DR 1.16–1.75 → "Slower than estimated"
- DR > 1.75    → "Much slower than estimated"

Output:
- Divergence classification: <label>
- DR value: <number>
- Confidence: <0–100> (lower if merge breakdown missing)

### 9.3 Risks if Observed Duration is MUCH LARGER than Estimated (DR > 1.75)
Provide 6–10 risks focusing on workload/process (NOT code security).
Mandatory themes (include if supported):
1) Underestimated integration tax (merge-heavy)
2) Rework tax (high churn / repeated revisits)
3) Review bottleneck (oversized commits)
4) Single point of failure / bus factor (concentration)
5) Low test adjacency causing rework
6) Process debt (vague commits reducing auditability → slows team)

### 9.4 Risks if Observed Duration is MUCH SMALLER than Estimated (DR <= 0.50)
Provide 6–10 risks focusing on sustainability/quality gate risks (NOT moralizing).
Mandatory themes (include if supported):
1) Burnout risk (burst-driven output intensity)
2) Oversized commits increase defect probability
3) Reduced review rigor (commit volume spikes)
4) Low test adjacency suggests quality debt
5) AI-acceleration risk post-scaffolding (only if evidenced)
6) Auditability risk (vague messages despite high velocity)

### 9.5 Calibration Actions (Model Improvement Loop)
Output 4–8 automated calibration steps:
- adjust ProductivityRate using observed churn/week
- discount churn for renames/moves if detectable
- separate merge churn from author churn
- exclude scaffolding window from calibration
- introduce per-repo baseline productivity derived from stable periods
  If needed data missing: **"Not provided in metrics"**

### 9.6 Final Determination
Conclude with:
- "Estimation reliability: High / Medium / Low"
- "Primary reason" (metrics-cited)
- "Next calibration step" (one actionable item)

---

## FINAL REQUIREMENTS
- Must output deterministic estimates from existing METRICS.
- Must not require any new file inputs.
- Must explicitly state missing metrics with: **"Not provided in metrics"**
- Must include section "LLM Estimate vs Repo Reality" and output an Accuracy Grade.
- Must include "Workload & Delivery Risks" section with workload-focused risks AND per-contributor throughput red flag logic.
- Must include Step E comparable-systems cross-check.
- Must end with:
  "Top 3 Metrics Additions That Would Most Improve Effort Accuracy"
  (3 bullets)
