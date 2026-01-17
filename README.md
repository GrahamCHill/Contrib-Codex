# Git Contributor Metrics

A desktop application to analyze Git repositories and generate contributor metrics, including visual charts and PDF reports.

## Features

- **Detailed Contributor Stats:** Track commits, lines added/deleted, and file change types (new, edited, deleted).
- **Language Analysis:** Automatic detection of top languages used per contributor.
- **AI Detection:** Heuristic-based analysis of likely AI-generated commits.
- **Visual Charts:** Pie charts for commit distribution and Bar charts for impact analysis.
- **User Aliases:** Combine multiple git emails into a single identity.
- **PDF Export:** Generate professional reports including charts and detailed tables.
- **LLM Integration:** Prepare comprehensive prompts for further analysis by AI models (OpenAI, Groq).
- **SQLite Persistence:** History of analysis is stored locally.

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
