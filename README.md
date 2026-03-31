# GitHub Repo Analyzer

**BYOP — Build Your Own Project**
**Student:** Krish Kumar | **Reg:** 24BAI10940
**Course:** Open Source Software

---

## What It Does

GitHub Repo Analyzer is a command-line tool that gives you a complete overview of any public GitHub repository in a single command.

Instead of clicking through multiple GitHub pages to find stars, languages, contributors, recent commits, and open issues — you get all of it at once, formatted cleanly in your terminal, and saved automatically as a report file.

**What it shows you:**
- Repository overview (name, description, license, topics)
- Stats (stars, forks, watchers, open issues, repo size, age)
- Language breakdown with ASCII bar chart and percentages
- Top 10 contributors ranked by commit count
- Last 5 commits with author and message
- Recent open issues
- Latest release info
- Quick summary that interprets the data in plain language

**What it saves:**
- A timestamped `.txt` report in the `reports/` folder
- A running `search_history.csv` log of every repo you've analyzed

---

## Requirements

- Java 11 or higher (JDK)
- Internet connection
- No external libraries needed — uses Java standard library only (`java.net.http`, `java.nio`, `java.time`)

---

## Setup

```bash
# Clone the repository
git clone https://github.com/krish0455/oss-audit-24bai10940.git

# Go to the BYOP project folder
cd oss-audit-24bai10940/byop

# Compile
javac GitHubAnalyzer.java
```

That's it. No Maven, no Gradle, no external JARs.

---

## How to Use

### Analyze any public GitHub repository

```bash
java GitHubAnalyzer https://github.com/python/cpython
java GitHubAnalyzer https://github.com/torvalds/linux
java GitHubAnalyzer https://github.com/microsoft/vscode
java GitHubAnalyzer https://github.com/pallets/flask
```

All of these URL formats work:
```bash
java GitHubAnalyzer https://github.com/owner/repo
java GitHubAnalyzer github.com/owner/repo
java GitHubAnalyzer https://github.com/owner/repo.git
```

### View your search history

```bash
java GitHubAnalyzer --history
```

Shows the last 10 repositories you analyzed, with timestamps and star counts.

### Show help

```bash
java GitHubAnalyzer
```

---

## Optional: GitHub Token (for higher rate limits)

Without a token, GitHub allows 60 API requests per hour — enough for normal use.
With a token, the limit rises to 5,000 requests per hour.

```bash
# Set your token as an environment variable (do NOT put it in the code)
export GITHUB_TOKEN=your_personal_access_token

# Then run as normal
java GitHubAnalyzer https://github.com/python/cpython
```

Get a free token at: https://github.com/settings/tokens
(No special permissions needed — just a basic personal access token)

---

## Output Example

```
==============================================================
  GITHUB REPO ANALYZER — Full Report
  Generated: 29 March 2026, 14:32:10
  By: Krish Kumar | 24BAI10940
==============================================================

  ── REPOSITORY OVERVIEW ──────────────────────────────────
  Name          : pallets/flask
  Description   : The Python micro framework for building web apps
  License       : BSD 3-Clause License
  Topics        : flask, python, web, wsgi

  ── STATISTICS ───────────────────────────────────────────
  Stars         : 67,423
  Forks         : 16,891
  Open Issues   : 8
  Last Pushed   : 2 days ago

  ── LANGUAGE BREAKDOWN ───────────────────────────────────
  Python               ████████████████████████░   97.3%
  Makefile             █░░░░░░░░░░░░░░░░░░░░░░░░    1.4%

  ── TOP CONTRIBUTORS ─────────────────────────────────────
   1. davidism           ████████████████████  2,341 commits

  ── QUICK SUMMARY ────────────────────────────────────────
  Popularity   : Extremely popular (50k+ stars)
  Fork ratio   : 25.1% of stars have been forked
  Repo age     : 14 years
==============================================================
```

---

## File Structure

```
byop/
├── GitHubAnalyzer.java  # Main tool — compile and run this
├── reports/             # Auto-created — timestamped report files saved here
└── search_history.csv   # Auto-created — log of every search you run
```

---

## How It Works (Brief)

The tool calls five GitHub API endpoints in sequence using `java.net.http.HttpClient`:

| Endpoint | Data Fetched |
|----------|-------------|
| `/repos/{owner}/{repo}` | Stars, forks, dates, license, description |
| `/repos/{owner}/{repo}/languages` | Language byte counts |
| `/repos/{owner}/{repo}/contributors` | Top contributors by commits |
| `/repos/{owner}/{repo}/commits` | Recent commit messages and authors |
| `/repos/{owner}/{repo}/issues` | Open issues (pull requests filtered out) |

All HTTP requests use Java's built-in `java.net.http.HttpClient` (JDK 11+). JSON parsing is handled by a lightweight recursive-descent parser bundled inside the same file — no external libraries required.

---

## Why No External Libraries?

The JDK does not include a built-in JSON parser. Rather than importing a dependency (like Gson or Jackson), this tool ships a minimal recursive-descent JSON parser (~120 lines) inside the same file. This keeps the zero-dependency promise: any machine with JDK 11 can compile and run it with a single `javac` command.

---

## Troubleshooting

| Error | Fix |
|-------|-----|
| `Repository not found` | Check the URL is correct and the repo is public |
| `Rate limit exceeded` | Set `GITHUB_TOKEN` environment variable |
| `Network error` | Check your internet connection |
| `Invalid URL` | Use format: `https://github.com/owner/repo` |
| `javac not found` | Install JDK 11+: `sudo apt install openjdk-17-jdk` |

---

## Project Context

This project was built for the BYOP component of the Open Source Software course at VIT. The problem it solves is real: evaluating a GitHub repository currently requires visiting multiple pages. This tool consolidates that into one command with a saved report.

Originally written in Python, the project has been ported entirely to Java using only the JDK standard library — no Maven, no Gradle, no external JARs. It demonstrates the same zero-dependency philosophy using `java.net.http`, `java.nio.file`, `java.time`, and a hand-rolled JSON parser.
