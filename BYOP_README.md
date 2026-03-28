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

- Python 3.7 or higher
- Internet connection
- No external packages needed — uses Python standard library only

---

## Setup

```bash
# Clone the repository
git clone https://github.com/krish0455/oss-audit-24bai10940.git

# Go to the BYOP project folder
cd oss-audit-24bai10940/byop
```

That's it. No pip install, no virtual environment needed.

---

## How to Use

### Analyze any public GitHub repository

```bash
python3 analyzer.py https://github.com/python/cpython
python3 analyzer.py https://github.com/torvalds/linux
python3 analyzer.py https://github.com/microsoft/vscode
python3 analyzer.py https://github.com/pallets/flask
```

All of these URL formats work:
```bash
python3 analyzer.py https://github.com/owner/repo
python3 analyzer.py github.com/owner/repo
python3 analyzer.py https://github.com/owner/repo.git
```

### View your search history

```bash
python3 analyzer.py --history
```

Shows the last 10 repositories you analyzed, with timestamps and star counts.

### Show help

```bash
python3 analyzer.py
```

---

## Optional: GitHub Token (for higher rate limits)

Without a token, GitHub allows 60 API requests per hour — enough for normal use.
With a token, the limit rises to 5,000 requests per hour.

```bash
# Set your token as an environment variable (do NOT put it in the code)
export GITHUB_TOKEN=your_personal_access_token

# Then run as normal
python3 analyzer.py https://github.com/python/cpython
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
  Name        : pallets/flask
  Description : The Python micro framework for building web apps
  License     : BSD 3-Clause License
  Topics      : flask, python, web, wsgi

  ── STATISTICS ───────────────────────────────────────────
  ⭐ Stars        : 67,423
  🍴 Forks        : 16,891
  🐛 Open Issues  : 8
  🔄 Last Pushed  : 2 days ago

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
├── analyzer.py          # Main tool — the only file you need to run
├── reports/             # Auto-created — timestamped report files saved here
└── search_history.csv   # Auto-created — log of every search you run
```

---

## How It Works (Brief)

The tool calls five GitHub API endpoints in sequence:

| Endpoint | Data Fetched |
|----------|-------------|
| `/repos/{owner}/{repo}` | Stars, forks, dates, license, description |
| `/repos/{owner}/{repo}/languages` | Language byte counts |
| `/repos/{owner}/{repo}/contributors` | Top contributors by commits |
| `/repos/{owner}/{repo}/commits` | Recent commit messages and authors |
| `/repos/{owner}/{repo}/issues` | Open issues (pull requests filtered out) |

All HTTP requests use Python's built-in `urllib` — no external libraries required.

---

## Troubleshooting

| Error | Fix |
|-------|-----|
| `Repository not found` | Check the URL is correct and the repo is public |
| `Rate limit exceeded` | Set `GITHUB_TOKEN` environment variable |
| `Network error` | Check your internet connection |
| `Invalid URL` | Use format: `https://github.com/owner/repo` |

---

## Project Context

This project was built for the BYOP component of the Open Source Software course at VIT. The problem it solves is real: evaluating a GitHub repository currently requires visiting multiple pages. This tool consolidates that into one command with a saved report.

Built entirely with Python's standard library — no dependencies, runs anywhere Python 3 is installed.
