# Introduction
This report provides an exhaustive, technical analysis of the repository’s development history and contributor activity based on the provided GIT METRICS and file change data. The focus is to identify high-value contributions, assess project stability, and evaluate technical risk across the codebase.

## Instructions for the LLM (STRICT)
- Use ONLY repository structure, directory paths, contributor names, and metrics explicitly provided in the METRICS section.
- Do NOT invent or assume folders, technologies, services, commit IDs, or contributor names that are not present in the METRICS.
- If repository structure or a specific metric is missing, write: **"Not provided in metrics"** (do not guess).
- Any high/low ranking statements MUST be numerically correct based on the provided metrics.

## Required Output
### 1) Executive Summary (high-level)
Provide 5–10 bullets covering:
- overall repository health signals (granularity of commits, tests touched, churn, risk hotspots)
- key technical risks identified from commit patterns
- evidence-backed statements referencing the metrics (counts, lines/commit, file categories)

### 2) Repository Structure & Architecture Mapping
Analyze the directory patterns included in METRICS and classify work into:
- **Backend** (e.g., `backend/`, `server/`, `api/`, `src/main/`, etc. *only if present*)
- **Frontend** (e.g., `frontend/`, `web/`, `ui/`, `src/app/`, etc. *only if present*)
- **Infrastructure / Config** (e.g., `.github/`, `docker/`, `k8s/`, `*.yml`, `*.yaml`, `*.toml`, etc. *only if present*)
- **Tests** (e.g., `test/`, `tests/`, `__tests__/`, `src/test/` *only if present*)

For each category:
- list the relevant directories observed
- summarize what kind of work appears to occur there (based on file change activity in METRICS)
- avoid assumptions about frameworks/tools unless explicitly stated in METRICS

### 3) Architectural Distribution of Work
Explain how development effort is distributed across the architecture by:
- referencing directory-level change concentrations (top directories by commits/LOC if provided)
- highlighting whether work is primarily backend/frontend/infra/test-heavy
- describing potential architectural implications (e.g., heavy infra churn vs minimal core logic changes), using metric evidence
