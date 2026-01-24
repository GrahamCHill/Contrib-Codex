# Change Coupling and Blast Radius

Goal:
Detect architectural coupling and blast radius risk WITHOUT inspecting source code.
Use file/directory co-change patterns to infer coupling boundaries and change propagation.

---

## INSTRUCTIONS FOR AI (STRICT)
- Use ONLY file paths/directories, commit refs, timestamps (if provided), churn totals, and co-change metrics from METRICS.
- Do NOT inspect code contents.
- Do NOT invent architecture, services, or ownership beyond what directory relationships support.
- If required metrics are missing, write: **"Not provided in metrics"**.

---

## REQUIRED OUTPUT FORMAT

### 1) Coupling Summary
- Describe whether changes are isolated or cross-cutting (METRICS-cited).

### 2) Blast Radius Score (0–100)
- Provide overall repo score.
- If supported, provide per-directory score for top N directories.

### 3) Top Co-Change Clusters
Provide **5–12 clusters**, each:
- Cluster Name (auto)
- Directories involved
- Evidence Observed (commit refs, frequency, churn)
- Impact (why it increases blast radius)

### 4) Cross-Cutting Change Patterns
- Identify patterns like "core always changes with infra" based ONLY on METRICS.

### 5) Recommendations (6–10)
Each recommendation MUST include:
Title / Severity / Effort / Evidence Observed / Why It Matters / Action Plan / Where / Success Criteria

---

## BLAST RADIUS GUIDANCE
Higher risk when:
- commits regularly touch many top-level directories
- hotspot directories co-change frequently
- merges cause broad multi-dir churn
  If file count per commit is missing: **"Not provided in metrics"**
