# Review Hygiene
Evaluate the health of review processes: PR size, review turnaround time, and merge behavior (approvals vs direct merges).

INSTRUCTIONS FOR AI (STRICT):
- Use ONLY metrics explicitly present in METRICS.
- Do NOT invent PR counts, turnaround times, approvals, or policies if not provided.
- If PR metrics are missing, state: **"Not provided in metrics"**.
- **Merge Commit Rule:** Distinguish integration activity (merge commits) from direct change authorship. Do not treat frequent merges as direct code output without supporting evidence.
- **NUMERIC LOGIC RULE**: You MUST correctly compare numeric values. Ensure you understand that 327 is a smaller number than 1160. Do NOT swap "highest" and "lowest" if the numbers clearly indicate otherwise. Verify all rankings before outputting.

REQUIRED OUTPUT:

## 1) Review Hygiene Summary
Provide 5–10 bullets describing:
- whether changes are reviewable (small vs large changes)
- risk implications of large changes without review signals
- merge behavior implications

## 2) PR Size / Change Size Table
If PR data exists:
- show PR size distribution (small/medium/large) and any thresholds.

If PR data does NOT exist:
- approximate using commit size distribution only if METRICS provides it, and label clearly:
  **"Commit-size proxy used; PR metrics not provided."**

Table format:

| Metric | Observed Value | Interpretation | Risk |
|---|---:|---|---|

## 3) Review Turnaround (Time-to-Review)
If turnaround metrics exist:
- provide median/mean and highlight outliers
- identify risk hotspots where large changes have slow review

If missing:
- state clearly it is not measurable.

## 4) Approvals vs Direct Merges
If approval/merge method metrics exist:
- highlight proportion of:
    - approved merges
    - direct merges
    - squash merges
    - merge commits
- interpret risk (direct merge into main on risky areas)

If missing:
- state "Not provided in metrics".

## 5) Process Improvements
Provide 5–10 actionable improvements:
- PR size guardrails
- mandatory review rules for core directories
- CI + testing gates
- merge strategy recommendations (squash vs merge commit)
  Only recommend items that match evidence in METRICS or explicitly label as general best practice if missing.

FINAL NOTE:
If review metrics are absent, state:
**"Review hygiene could not be evaluated from git metrics alone; PR platform metadata is required."**
