#!/usr/bin/env python3
"""
GitHub Repo Analyzer
--------------------
Author : Krish Kumar | 24BAI10940
Project: BYOP — Build Your Own Project
Course : Open Source Software

Problem: Developers and students often want a quick overview of any public
GitHub repository — language breakdown, activity, contributors, recent commits
— without having to click through multiple GitHub pages. This tool fetches
all of that in one command and saves a report locally.

Usage:
    python3 analyzer.py <github_repo_url>
    python3 analyzer.py https://github.com/torvalds/linux
    python3 analyzer.py https://github.com/python/cpython
"""

import sys
import json
import os
import csv
from datetime import datetime, timezone
import urllib.request
import urllib.error


# ── Configuration ─────────────────────────────────────────────────────────────
GITHUB_API   = "https://api.github.com"
REPORTS_DIR  = "reports"
HISTORY_FILE = "search_history.csv"

# Optional: set your GitHub token as env var for higher rate limits
# export GITHUB_TOKEN=your_token_here
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")


# ── Helpers ───────────────────────────────────────────────────────────────────

def build_headers():
    """Build request headers, including auth token if available."""
    headers = {
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "GitHubRepoAnalyzer/1.0"
    }
    if GITHUB_TOKEN:
        headers["Authorization"] = f"token {GITHUB_TOKEN}"
    return headers


def api_get(endpoint):
    """
    Make a GET request to the GitHub API.
    Returns parsed JSON or raises an error with a clear message.
    """
    url = f"{GITHUB_API}{endpoint}"
    req = urllib.request.Request(url, headers=build_headers())
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            data = response.read().decode("utf-8")
            return json.loads(data)
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print(f"\n  [ERROR] Repository not found. Check the URL and make sure it's public.")
        elif e.code == 403:
            print(f"\n  [ERROR] Rate limit exceeded. Set GITHUB_TOKEN env var for more requests.")
            print(f"          export GITHUB_TOKEN=your_personal_access_token")
        elif e.code == 401:
            print(f"\n  [ERROR] Invalid GitHub token. Check your GITHUB_TOKEN.")
        else:
            print(f"\n  [ERROR] GitHub API returned HTTP {e.code}")
        sys.exit(1)
    except urllib.error.URLError as e:
        print(f"\n  [ERROR] Network error: {e.reason}")
        print(f"          Check your internet connection.")
        sys.exit(1)


def parse_repo_url(url):
    """
    Extract owner and repo name from a GitHub URL.
    Handles formats:
      https://github.com/owner/repo
      https://github.com/owner/repo.git
      github.com/owner/repo
    """
    url = url.strip().rstrip("/")
    if url.endswith(".git"):
        url = url[:-4]

    # Strip protocol
    for prefix in ["https://github.com/", "http://github.com/", "github.com/"]:
        if url.startswith(prefix):
            url = url[len(prefix):]
            break

    parts = url.split("/")
    if len(parts) < 2:
        print("\n  [ERROR] Invalid GitHub URL.")
        print("  Expected format: https://github.com/owner/repository")
        sys.exit(1)

    return parts[0], parts[1]


def fmt_number(n):
    """Format large numbers with commas."""
    return f"{n:,}"


def fmt_date(iso_string):
    """Convert ISO 8601 date to readable format."""
    if not iso_string:
        return "N/A"
    try:
        dt = datetime.fromisoformat(iso_string.replace("Z", "+00:00"))
        now = datetime.now(timezone.utc)
        diff = now - dt
        days = diff.days
        if days == 0:
            return "Today"
        elif days == 1:
            return "Yesterday"
        elif days < 30:
            return f"{days} days ago"
        elif days < 365:
            months = days // 30
            return f"{months} month{'s' if months > 1 else ''} ago"
        else:
            years = days // 365
            return f"{years} year{'s' if years > 1 else ''} ago"
    except Exception:
        return iso_string[:10]


def bar_chart(value, total, width=30, char="█"):
    """Generate a simple ASCII bar chart segment."""
    if total == 0:
        return " " * width
    filled = int((value / total) * width)
    return char * filled + "░" * (width - filled)


def save_report(owner, repo, data):
    """Save the analysis report to a text file in the reports/ directory."""
    os.makedirs(REPORTS_DIR, exist_ok=True)
    filename = f"{REPORTS_DIR}/{owner}_{repo}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"

    with open(filename, "w", encoding="utf-8") as f:
        f.write(data)

    return filename


def log_history(owner, repo, stars, forks):
    """Append this search to the history CSV file."""
    file_exists = os.path.exists(HISTORY_FILE)
    with open(HISTORY_FILE, "a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        if not file_exists:
            writer.writerow(["timestamp", "owner", "repo", "stars", "forks"])
        writer.writerow([
            datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            owner, repo, stars, forks
        ])


def show_history():
    """Display past searches from the history CSV."""
    if not os.path.exists(HISTORY_FILE):
        print("\n  No search history yet.")
        return

    print("\n" + "="*60)
    print("  SEARCH HISTORY")
    print("="*60)
    with open(HISTORY_FILE, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
        if not rows:
            print("  No searches recorded yet.")
            return
        for row in rows[-10:]:  # Show last 10
            print(f"  {row['timestamp']}  |  {row['owner']}/{row['repo']}  |  ⭐ {row['stars']}")
    print("="*60)


# ── Core Analysis Functions ───────────────────────────────────────────────────

def fetch_repo_info(owner, repo):
    """Fetch basic repository information."""
    return api_get(f"/repos/{owner}/{repo}")


def fetch_languages(owner, repo):
    """Fetch language breakdown (bytes per language)."""
    return api_get(f"/repos/{owner}/{repo}/languages")


def fetch_contributors(owner, repo, limit=10):
    """Fetch top contributors by commit count."""
    return api_get(f"/repos/{owner}/{repo}/contributors?per_page={limit}&anon=false")


def fetch_recent_commits(owner, repo, limit=5):
    """Fetch the most recent commits."""
    return api_get(f"/repos/{owner}/{repo}/commits?per_page={limit}")


def fetch_open_issues(owner, repo, limit=5):
    """Fetch recent open issues (excludes pull requests)."""
    data = api_get(f"/repos/{owner}/{repo}/issues?state=open&per_page={limit}&pulls=false")
    # GitHub issues endpoint includes PRs; filter them out
    return [i for i in data if "pull_request" not in i][:limit]


def fetch_releases(owner, repo):
    """Fetch the latest release."""
    data = api_get(f"/repos/{owner}/{repo}/releases?per_page=1")
    return data[0] if data else None


# ── Display Functions ─────────────────────────────────────────────────────────

def display_and_capture(lines):
    """Print lines to terminal and return them as a string for saving."""
    output = []
    for line in lines:
        print(line)
        output.append(line)
    return "\n".join(output)


def analyze(repo_url):
    """Main analysis pipeline."""

    print("\n" + "="*62)
    print("  GITHUB REPO ANALYZER  |  Krish Kumar  |  24BAI10940")
    print("="*62)
    print(f"\n  Fetching data for: {repo_url}")
    print("  Please wait...\n")

    # Parse URL
    owner, repo = parse_repo_url(repo_url)

    # Fetch all data
    print("  [1/5] Loading repository info...")
    info = fetch_repo_info(owner, repo)

    print("  [2/5] Fetching language breakdown...")
    languages = fetch_languages(owner, repo)

    print("  [3/5] Loading top contributors...")
    contributors = fetch_contributors(owner, repo)

    print("  [4/5] Fetching recent commits...")
    commits = fetch_recent_commits(owner, repo)

    print("  [5/5] Loading issues and releases...")
    issues   = fetch_open_issues(owner, repo)
    release  = fetch_releases(owner, repo)

    print("\n  Done. Generating report...\n")

    lines = []
    lines.append("=" * 62)
    lines.append(f"  GITHUB REPO ANALYZER — Full Report")
    lines.append(f"  Generated: {datetime.now().strftime('%d %B %Y, %H:%M:%S')}")
    lines.append(f"  By: Krish Kumar | 24BAI10940")
    lines.append("=" * 62)

    # ── SECTION 1: Basic Info ──────────────────────────────────────
    lines.append("")
    lines.append("  ── REPOSITORY OVERVIEW ─────────────────────────────────────")
    lines.append(f"  Name        : {info.get('full_name', 'N/A')}")
    lines.append(f"  Description : {info.get('description') or 'No description provided'}")
    lines.append(f"  URL         : {info.get('html_url', 'N/A')}")
    lines.append(f"  Visibility  : {'Private' if info.get('private') else 'Public'}")
    lines.append(f"  License     : {info.get('license', {}).get('name', 'No license') if info.get('license') else 'No license'}")
    lines.append(f"  Default Branch: {info.get('default_branch', 'N/A')}")
    lines.append(f"  Homepage    : {info.get('homepage') or 'N/A'}")
    lines.append(f"  Topics      : {', '.join(info.get('topics', [])) or 'None'}")

    # ── SECTION 2: Stats ───────────────────────────────────────────
    lines.append("")
    lines.append("  ── STATISTICS ───────────────────────────────────────────────")
    lines.append(f"  ⭐ Stars         : {fmt_number(info.get('stargazers_count', 0))}")
    lines.append(f"  🍴 Forks         : {fmt_number(info.get('forks_count', 0))}")
    lines.append(f"  👀 Watchers      : {fmt_number(info.get('watchers_count', 0))}")
    lines.append(f"  🐛 Open Issues   : {fmt_number(info.get('open_issues_count', 0))}")
    lines.append(f"  📦 Repo Size     : {fmt_number(info.get('size', 0))} KB")
    lines.append(f"  🕐 Created       : {fmt_date(info.get('created_at'))}")
    lines.append(f"  🔄 Last Updated  : {fmt_date(info.get('updated_at'))}")
    lines.append(f"  📤 Last Pushed   : {fmt_date(info.get('pushed_at'))}")

    # ── SECTION 3: Languages ───────────────────────────────────────
    lines.append("")
    lines.append("  ── LANGUAGE BREAKDOWN ───────────────────────────────────────")
    if languages:
        total_bytes = sum(languages.values())
        sorted_langs = sorted(languages.items(), key=lambda x: x[1], reverse=True)
        for lang, byte_count in sorted_langs[:10]:
            pct = (byte_count / total_bytes) * 100
            bar = bar_chart(byte_count, total_bytes, width=25)
            lines.append(f"  {lang:<20} {bar}  {pct:5.1f}%  ({fmt_number(byte_count)} bytes)")
    else:
        lines.append("  No language data available.")

    # ── SECTION 4: Contributors ────────────────────────────────────
    lines.append("")
    lines.append("  ── TOP CONTRIBUTORS ─────────────────────────────────────────")
    if contributors:
        max_commits = contributors[0].get("contributions", 1)
        for i, c in enumerate(contributors[:10], 1):
            username = c.get("login", "unknown")
            contribs = c.get("contributions", 0)
            bar = bar_chart(contribs, max_commits, width=20)
            lines.append(f"  {i:2}. {username:<25} {bar}  {fmt_number(contribs)} commits")
    else:
        lines.append("  No contributor data available.")

    # ── SECTION 5: Recent Commits ──────────────────────────────────
    lines.append("")
    lines.append("  ── RECENT COMMITS ───────────────────────────────────────────")
    if commits:
        for c in commits:
            commit     = c.get("commit", {})
            message    = commit.get("message", "").split("\n")[0][:55]
            author     = commit.get("author", {}).get("name", "Unknown")
            date_str   = commit.get("author", {}).get("date", "")
            sha        = c.get("sha", "")[:7]
            lines.append(f"  [{sha}] {fmt_date(date_str):<15} {author:<20}")
            lines.append(f"          {message}")
            lines.append("")
    else:
        lines.append("  No commit data available.")

    # ── SECTION 6: Open Issues ─────────────────────────────────────
    lines.append("")
    lines.append("  ── RECENT OPEN ISSUES ───────────────────────────────────────")
    if issues:
        for issue in issues[:5]:
            title  = issue.get("title", "")[:55]
            number = issue.get("number", "")
            user   = issue.get("user", {}).get("login", "unknown")
            opened = fmt_date(issue.get("created_at", ""))
            lines.append(f"  #{number:<6} {title}")
            lines.append(f"          Opened by @{user}  |  {opened}")
            lines.append("")
    else:
        lines.append("  No open issues found.")

    # ── SECTION 7: Latest Release ──────────────────────────────────
    lines.append("")
    lines.append("  ── LATEST RELEASE ───────────────────────────────────────────")
    if release:
        lines.append(f"  Tag     : {release.get('tag_name', 'N/A')}")
        lines.append(f"  Name    : {release.get('name') or 'N/A'}")
        lines.append(f"  Date    : {fmt_date(release.get('published_at'))}")
        lines.append(f"  URL     : {release.get('html_url', 'N/A')}")
        body = (release.get("body") or "").strip()[:200]
        if body:
            lines.append(f"  Notes   : {body[:100]}...")
    else:
        lines.append("  No releases found for this repository.")

    # ── SECTION 8: Quick Summary ───────────────────────────────────
    lines.append("")
    lines.append("  ── QUICK SUMMARY ────────────────────────────────────────────")

    stars = info.get("stargazers_count", 0)
    forks = info.get("forks_count", 0)
    issues_count = info.get("open_issues_count", 0)
    age_days = 0
    created = info.get("created_at")
    if created:
        try:
            dt = datetime.fromisoformat(created.replace("Z", "+00:00"))
            age_days = (datetime.now(timezone.utc) - dt).days
        except Exception:
            pass

    if stars > 50000:
        popularity = "Extremely popular (50k+ stars)"
    elif stars > 10000:
        popularity = "Very popular (10k+ stars)"
    elif stars > 1000:
        popularity = "Popular (1k+ stars)"
    elif stars > 100:
        popularity = "Growing (100+ stars)"
    else:
        popularity = "Early stage or niche project"

    if age_days > 0:
        fork_ratio = round(forks / max(stars, 1) * 100, 1)
    else:
        fork_ratio = 0

    lines.append(f"  Popularity   : {popularity}")
    lines.append(f"  Fork ratio   : {fork_ratio}% of stars have been forked")
    lines.append(f"  Repo age     : {age_days} days ({age_days // 365} years)")
    if contributors:
        lines.append(f"  Top contributor: @{contributors[0].get('login','?')} with {fmt_number(contributors[0].get('contributions',0))} commits")
    if languages:
        top_lang = list(languages.keys())[0]
        lines.append(f"  Primary language: {top_lang}")

    lines.append("")
    lines.append("=" * 62)
    lines.append(f"  Report saved to: reports/ directory")
    lines.append("=" * 62)

    # Print and save
    report_text = display_and_capture(lines)

    # Save report file
    filename = save_report(owner, repo, report_text)
    print(f"\n  [SAVED] Report written to: {filename}")

    # Log to history
    log_history(owner, repo, info.get("stargazers_count", 0), info.get("forks_count", 0))
    print(f"  [LOGGED] Added to search_history.csv")
    print()


# ── Entry Point ───────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 2:
        print("\n" + "="*62)
        print("  GITHUB REPO ANALYZER  |  Krish Kumar  |  24BAI10940")
        print("="*62)
        print("\n  Usage:")
        print("    python3 analyzer.py <github_url>")
        print("    python3 analyzer.py --history")
        print("\n  Examples:")
        print("    python3 analyzer.py https://github.com/python/cpython")
        print("    python3 analyzer.py https://github.com/torvalds/linux")
        print("    python3 analyzer.py https://github.com/microsoft/vscode")
        print("\n  Optional: Set GitHub token for higher API rate limits:")
        print("    export GITHUB_TOKEN=your_personal_access_token")
        print("="*62 + "\n")
        sys.exit(0)

    if sys.argv[1] == "--history":
        show_history()
        sys.exit(0)

    analyze(sys.argv[1])


if __name__ == "__main__":
    main()
