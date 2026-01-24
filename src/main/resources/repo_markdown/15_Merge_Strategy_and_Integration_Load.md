# Merge Strategy and Integration Load

Goal:
Analyze merge strategy and quantify integration load (integration tax) using ONLY Git METRICS.
This section MUST assess workload concentration, merge-driven churn, and audit impact of merge behavior.

---

## INSTRUCTIONS FOR AI (STRICT)
- Use ONLY contributor names, file paths/directories, commit refs, commit message text (if provided),
  merge commit counts, timestamps (if provided), and numeric values explicitly present in METRICS.
- Do NOT inspect source code content.
- Do NOT invent PR numbers, branches, tags, or merge strategies not evidenced in METRICS.
- If data needed is missing, write: **"Not provided in metrics"**.

---

## MERGE COMMIT RULE (MANDATORY)
- Merge commits MUST be treated separately from standard commits when interpreting:
    - commit frequency
    - churn and bulk-change patterns
    - directory impact
    - contributor responsibility
- If merge commit breakdown is NOT provided, explicitly state:
  **"Merge commit breakdown not provided; integration load may be conflated with authoring work."**

---

## REQUIRED OUTPUT FORMAT

### 1) Merge Strategy Summary
- State the likely merge strategy IF supported by METRICS.
- If not supported: **"Not provided in metrics"**

### 2) Integration Load Score (0–100)
- Provide score + severity band:
    - 0–25 low
    - 26–50 moderate
    - 51–75 high
    - 76–100 extreme

### 3) Integration Burden Distribution
- Identify top integration contributors.
- If contributor merge metrics missing: **"Not provided in metrics"**

### 4) Merge Risk Hotspots
- Directories / file types most impacted by merges.

### 5) Workload & Delivery Risks (Merge-driven)
Provide **6–10 risks** with:
- Risk Title
- Severity (High/Medium/Low)
- Evidence Observed (2–5 bullets from METRICS)
- Why It Matters (workload/integration/quality/schedule)
- Mitigation
- Where (dirs from METRICS)

### 6) Recommendations (6–10)
Each recommendation MUST include:
Title / Severity / Effort / Evidence Observed / Why It Matters / Action Plan / Where / Success Criteria

---

## SCORING GUIDANCE
Integration Load Score MUST consider:
- merge ratio vs total commits
- merge churn share
- merge concentration by contributor
- merges touching hotspot/high-risk directories
- merge time clustering (if timestamps exist)
  If timestamps missing: **"Not provided in metrics"**
