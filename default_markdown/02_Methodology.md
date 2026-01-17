# Analysis Methodology
The analysis utilizes JGit for precise metric extraction and AI-driven heuristics to interpret qualitative development patterns.

INSTRUCTIONS FOR AI:
- Explain the 'Lines Added per Commit' risk scoring system (1500+ VERY HIGH to <250 LOW).
- Describe how the presence of tests (files in 'test' directories) mitigates risk scores.
- Explicitly state that risk is based on average lines added per commit, not lines in a single file.
- Detail the 'Meaningful Change' detection logic which filters out boilerplate and automated code generation.