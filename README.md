# Contrib Codex

> **Transform Git noise into engineering signal.**  
> Contrib Codex isn't just a stats generator—it's a repository forensics tool that bridges the gap between raw commit data and actionable technical insights using AI.

Contrib Codex is a Java-based Git analytics tool that generates contributor graphs and change metrics from your repositories, then uses an LLM provider (Groq/OpenAI/etc.) to turn those metrics into engineering audits.

<!-- 
<p align="center">
  <img src="docs/images/main_ui_snapshot.png" alt="Contrib Codex UI" width="800">
</p>
-->

## Features

- **Meaningful Change Detection:** Heuristic-based scoring that prioritizes core logic and tests over boilerplate and lockfile noise.
- **Refactoring Recognition:** Rewards technical debt reduction by identifying significant deletions and architectural cleanups.
- **AI Audit Reports:** Seamlessly integrates with OpenAI, Groq, or Ollama to generate deep-dive contributor reviews and risk assessments.
- **Visual Analytics:** Interactive Pie, Bar, and Line charts tracking impact, activity, and commit density over time.
- **Privacy First:** All analysis is performed locally; API keys are encrypted with AES-256, and metrics are stored in a local SQLite database.
- **Open Source:** Licensed under the GNU General Public License v3.0 (GPL-3.0).

<!-- 
<p align="center">
  <img src="docs/images/pdf_report_preview.png" alt="PDF Report Preview" width="600">
</p>
-->

## Why Contrib Codex? (Scenarios)

*   **Engineering Leaders:** Perform non-intrusive "health checks" on repositories to identify bottlenecks or high-risk bulk commit patterns.
*   **Onboarding:** Quickly understand the "who" and "where" of a new codebase by seeing which contributors own specific architectural components.
*   **Code Quality Audits:** Supplement manual reviews with AI-driven heuristics that highlight suspicious activity or lack of test coverage relative to feature complexity.
*   **Technical Debt Management:** Identify contributors who excel at refactoring and cleaning up code, not just those adding the most lines.

## Setup Instructions

### Prerequisites

- **Java 25** or higher.
- **Maven** (for building and running).

### Building the Project

Clone the repository and run the following command in the root directory:

```bash
mvn clean compile
```

### Running the Application

To launch the GUI, use the JavaFX Maven plugin:

```bash
mvn javafx:run
```

## How to Use

1. **Select Repository:** Click "Browse..." to select a local Git repository.
2. **Configure Settings:**
    - **Git Tree Commits:** Set the number of recent commits to display. Set to `0` to view all commits back to the initial commit.
    - **Ignore Extensions:** Comma-separated list of file extensions to exclude from analysis (e.g., `json, csv`).
    - **Ignore Folders:** Comma-separated list of folders to exclude from analysis (e.g., `node_modules, target`).
    - **MD Folder:** Optional path to a folder containing `.md` files. Each file will be added as a section in the PDF and included as context for the LLM.
3. **Analyze:** Click "Analyze" to process the repository.
4. **Merge Users:** Right-click a user in the table to merge them with another identity (useful for multiple emails).
5. **API Keys:** Use the **Settings -> API Keys...** menu to set your OpenAI or Groq keys for LLM report generation.
6. **Export:** Click "Export to PDF" to save a visual and detailed report.

## Technical Details

- **Language:** Java 25
- **UI Framework:** JavaFX 23
- **Git Library:** JGit
- **Database:** SQLite (JDBC)
- **PDF Library:** OpenPDF
- **Charts:** JavaFX Charts (Pie, Bar)
