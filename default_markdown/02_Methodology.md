# Analysis Methodology
The analysis utilizes JGit for precise metric extraction and AI-driven heuristics to interpret qualitative development patterns.

INSTRUCTIONS FOR AI:
- Explain the risk scoring system. The primary metric is 'Lines Added per Commit' (1500+ VERY HIGH to <250 LOW).
- Mention secondary factors: High churn, lack of tests, and AI-generated code signals (if available) increase the risk score.
- Describe how the presence of tests (files in 'test' directories) and refactoring (deletions) mitigate risk scores.
- Explicitly state that risk is based on average lines added per commit, not lines in a single file.
- Explain that merge commits are tracked but their lines of code are excluded from total counts to prevent metric skewing.
- Detail the 'Meaningful Change' score logic:
  - Repository-wide score: Weighted by Source Code (70%) and Tests (30%) insertions.
  - Contributor-level score: Evaluates iterative development (bonus for <250-500 lines per commit), testing activity, and requirements alignment.
  - Filters out boilerplate, generated artifacts, and documentation noise.