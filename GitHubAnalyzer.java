/**
 * GitHub Repo Analyzer
 * --------------------
 * Author : Krish Kumar | 24BAI10940
 * Project: BYOP — Build Your Own Project
 * Course : Open Source Software
 *
 * Rewritten in Java (JDK 11+) — zero external dependencies.
 * Uses java.net.http (HttpClient), org.json replaced by manual JSON
 * parsing via javax.json / hand-rolled parser — actually uses only
 * java.net.http + built-in javax.json from JDK 11 JSON-P via
 * the simpler approach of bundling a minimal JSON reader.
 *
 * Since JDK has NO built-in JSON parser, this file ships a lightweight
 * recursive-descent JSON parser (~120 lines) so the tool stays
 * dependency-free, exactly matching the spirit of the Python original.
 *
 * Compile:  javac GitHubAnalyzer.java
 * Run:      java GitHubAnalyzer <github_url>
 *           java GitHubAnalyzer --history
 *           java GitHubAnalyzer https://github.com/pallets/flask
 *
 * Optional: export GITHUB_TOKEN=your_personal_access_token
 */

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.*;

public class GitHubAnalyzer {

    // ── Configuration ─────────────────────────────────────────────────────────
    private static final String GITHUB_API   = "https://api.github.com";
    private static final String REPORTS_DIR  = "reports";
    private static final String HISTORY_FILE = "search_history.csv";
    private static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN") != null
            ? System.getenv("GITHUB_TOKEN") : "";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ── Entry Point ───────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            System.exit(0);
        }
        if (args[0].equals("--history")) {
            showHistory();
            System.exit(0);
        }
        analyze(args[0]);
    }

    // ── Usage ─────────────────────────────────────────────────────────────────
    private static void printUsage() {
        System.out.println();
        System.out.println("=".repeat(62));
        System.out.println("  GITHUB REPO ANALYZER  |  Krish Kumar  |  24BAI10940");
        System.out.println("=".repeat(62));
        System.out.println();
        System.out.println("  Usage:");
        System.out.println("    java GitHubAnalyzer <github_url>");
        System.out.println("    java GitHubAnalyzer --history");
        System.out.println();
        System.out.println("  Examples:");
        System.out.println("    java GitHubAnalyzer https://github.com/python/cpython");
        System.out.println("    java GitHubAnalyzer https://github.com/torvalds/linux");
        System.out.println("    java GitHubAnalyzer https://github.com/microsoft/vscode");
        System.out.println();
        System.out.println("  Optional: Set GitHub token for higher API rate limits:");
        System.out.println("    export GITHUB_TOKEN=your_personal_access_token");
        System.out.println("=".repeat(62));
        System.out.println();
    }

    // ── URL Parser ────────────────────────────────────────────────────────────
    private static String[] parseRepoUrl(String url) {
        url = url.strip();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);

        for (String prefix : new String[]{"https://github.com/", "http://github.com/", "github.com/"}) {
            if (url.startsWith(prefix)) {
                url = url.substring(prefix.length());
                break;
            }
        }

        String[] parts = url.split("/");
        if (parts.length < 2) {
            System.err.println("\n  [ERROR] Invalid GitHub URL.");
            System.err.println("  Expected: https://github.com/owner/repository");
            System.exit(1);
        }
        return new String[]{parts[0], parts[1]};
    }

    // ── HTTP + Auth ───────────────────────────────────────────────────────────
    private static HttpRequest.Builder baseRequest(String endpoint) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API + endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "GitHubRepoAnalyzer-Java/1.0")
                .GET();
        if (!GITHUB_TOKEN.isEmpty()) {
            builder.header("Authorization", "token " + GITHUB_TOKEN);
        }
        return builder;
    }

    private static Object apiGet(String endpoint) {
        try {
            var req = baseRequest(endpoint).build();
            var resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code == 404) {
                System.err.println("\n  [ERROR] Repository not found. Check the URL and make sure it's public.");
                System.exit(1);
            } else if (code == 403) {
                System.err.println("\n  [ERROR] Rate limit exceeded. Set GITHUB_TOKEN env var for more requests.");
                System.err.println("          export GITHUB_TOKEN=your_personal_access_token");
                System.exit(1);
            } else if (code == 401) {
                System.err.println("\n  [ERROR] Invalid GitHub token. Check your GITHUB_TOKEN.");
                System.exit(1);
            } else if (code != 200) {
                System.err.println("\n  [ERROR] GitHub API returned HTTP " + code);
                System.exit(1);
            }
            return JsonParser.parse(resp.body());
        } catch (InterruptedException | IOException e) {
            System.err.println("\n  [ERROR] Network error: " + e.getMessage());
            System.err.println("          Check your internet connection.");
            System.exit(1);
            return null;
        }
    }

    // ── Fetch helpers ─────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private static Map<String, Object> fetchRepoInfo(String owner, String repo) {
        return (Map<String, Object>) apiGet("/repos/" + owner + "/" + repo);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fetchLanguages(String owner, String repo) {
        return (Map<String, Object>) apiGet("/repos/" + owner + "/" + repo + "/languages");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fetchContributors(String owner, String repo) {
        Object r = apiGet("/repos/" + owner + "/" + repo + "/contributors?per_page=10&anon=false");
        return r instanceof List ? (List<Map<String, Object>>) r : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fetchRecentCommits(String owner, String repo) {
        Object r = apiGet("/repos/" + owner + "/" + repo + "/commits?per_page=5");
        return r instanceof List ? (List<Map<String, Object>>) r : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fetchOpenIssues(String owner, String repo) {
        Object r = apiGet("/repos/" + owner + "/" + repo + "/issues?state=open&per_page=10");
        List<Map<String, Object>> all = r instanceof List ? (List<Map<String, Object>>) r : new ArrayList<>();
        // Filter out pull requests
        return all.stream()
                .filter(i -> !i.containsKey("pull_request"))
                .limit(5)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fetchRelease(String owner, String repo) {
        Object r = apiGet("/repos/" + owner + "/" + repo + "/releases?per_page=1");
        if (r instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) r;
            return list.isEmpty() ? null : list.get(0);
        }
        return null;
    }

    // ── Formatters ────────────────────────────────────────────────────────────
    private static String fmtNumber(Object n) {
        if (n == null) return "0";
        long val = toLong(n);
        return String.format("%,d", val);
    }

    private static String fmtDate(Object iso) {
        if (iso == null || iso.toString().isEmpty()) return "N/A";
        try {
            OffsetDateTime dt = OffsetDateTime.parse(iso.toString(),
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            long days = ChronoUnit.DAYS.between(dt.toInstant(), Instant.now());
            if (days == 0)  return "Today";
            if (days == 1)  return "Yesterday";
            if (days < 30)  return days + " days ago";
            if (days < 365) {
                long m = days / 30;
                return m + " month" + (m > 1 ? "s" : "") + " ago";
            }
            long y = days / 365;
            return y + " year" + (y > 1 ? "s" : "") + " ago";
        } catch (Exception e) {
            return iso.toString().substring(0, Math.min(10, iso.toString().length()));
        }
    }

    private static String barChart(long value, long total, int width) {
        if (total == 0) return " ".repeat(width);
        int filled = (int) ((double) value / total * width);
        filled = Math.min(filled, width);
        return "█".repeat(filled) + "░".repeat(width - filled);
    }

    private static long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0L; }
    }

    @SuppressWarnings("unchecked")
    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Map ? (Map<String, Object>) v : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    // ── Core Analysis ─────────────────────────────────────────────────────────
    private static void analyze(String repoUrl) throws IOException {
        System.out.println();
        System.out.println("=".repeat(62));
        System.out.println("  GITHUB REPO ANALYZER  |  Krish Kumar  |  24BAI10940");
        System.out.println("=".repeat(62));
        System.out.println("\n  Fetching data for: " + repoUrl);
        System.out.println("  Please wait...\n");

        String[] parts = parseRepoUrl(repoUrl);
        String owner = parts[0];
        String repo  = parts[1];

        System.out.println("  [1/5] Loading repository info...");
        Map<String, Object> info = fetchRepoInfo(owner, repo);

        System.out.println("  [2/5] Fetching language breakdown...");
        Map<String, Object> languages = fetchLanguages(owner, repo);

        System.out.println("  [3/5] Loading top contributors...");
        List<Map<String, Object>> contributors = fetchContributors(owner, repo);

        System.out.println("  [4/5] Fetching recent commits...");
        List<Map<String, Object>> commits = fetchRecentCommits(owner, repo);

        System.out.println("  [5/5] Loading issues and releases...");
        List<Map<String, Object>> issues = fetchOpenIssues(owner, repo);
        Map<String, Object> release = fetchRelease(owner, repo);

        System.out.println("\n  Done. Generating report...\n");

        List<String> lines = new ArrayList<>();

        // Header
        lines.add("=".repeat(62));
        lines.add("  GITHUB REPO ANALYZER — Full Report");
        lines.add("  Generated: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm:ss")));
        lines.add("  By: Krish Kumar | 24BAI10940");
        lines.add("=".repeat(62));

        // ── Section 1: Overview ──────────────────────────────────────
        lines.add("");
        lines.add("  ── REPOSITORY OVERVIEW ─────────────────────────────────────");
        lines.add("  Name          : " + str(info, "full_name"));
        String desc = str(info, "description");
        lines.add("  Description   : " + (desc.isEmpty() ? "No description provided" : desc));
        lines.add("  URL           : " + str(info, "html_url"));
        lines.add("  Visibility    : " + (Boolean.TRUE.equals(info.get("private")) ? "Private" : "Public"));
        Map<String, Object> lic = map(info, "license");
        lines.add("  License       : " + (lic.isEmpty() ? "No license" : str(lic, "name")));
        lines.add("  Default Branch: " + str(info, "default_branch"));
        String homepage = str(info, "homepage");
        lines.add("  Homepage      : " + (homepage.isEmpty() ? "N/A" : homepage));
        List<Object> topics = list(info, "topics");
        lines.add("  Topics        : " + (topics.isEmpty() ? "None" :
                topics.stream().map(Object::toString).collect(Collectors.joining(", "))));

        // ── Section 2: Stats ─────────────────────────────────────────
        lines.add("");
        lines.add("  ── STATISTICS ───────────────────────────────────────────────");
        lines.add("  Stars         : " + fmtNumber(info.get("stargazers_count")));
        lines.add("  Forks         : " + fmtNumber(info.get("forks_count")));
        lines.add("  Watchers      : " + fmtNumber(info.get("watchers_count")));
        lines.add("  Open Issues   : " + fmtNumber(info.get("open_issues_count")));
        lines.add("  Repo Size     : " + fmtNumber(info.get("size")) + " KB");
        lines.add("  Created       : " + fmtDate(info.get("created_at")));
        lines.add("  Last Updated  : " + fmtDate(info.get("updated_at")));
        lines.add("  Last Pushed   : " + fmtDate(info.get("pushed_at")));

        // ── Section 3: Languages ─────────────────────────────────────
        lines.add("");
        lines.add("  ── LANGUAGE BREAKDOWN ───────────────────────────────────────");
        if (!languages.isEmpty()) {
            long totalBytes = languages.values().stream().mapToLong(GitHubAnalyzer::toLong).sum();
            languages.entrySet().stream()
                    .sorted((a, b) -> Long.compare(toLong(b.getValue()), toLong(a.getValue())))
                    .limit(10)
                    .forEach(e -> {
                        long bytes = toLong(e.getValue());
                        double pct = (double) bytes / totalBytes * 100;
                        String bar = barChart(bytes, totalBytes, 25);
                        lines.add(String.format("  %-20s %s  %5.1f%%  (%s bytes)",
                                e.getKey(), bar, pct, fmtNumber(bytes)));
                    });
        } else {
            lines.add("  No language data available.");
        }

        // ── Section 4: Contributors ───────────────────────────────────
        lines.add("");
        lines.add("  ── TOP CONTRIBUTORS ─────────────────────────────────────────");
        if (!contributors.isEmpty()) {
            long maxCommits = toLong(contributors.get(0).get("contributions"));
            for (int i = 0; i < Math.min(10, contributors.size()); i++) {
                Map<String, Object> c = contributors.get(i);
                String username = str(c, "login");
                long contribs   = toLong(c.get("contributions"));
                String bar      = barChart(contribs, maxCommits, 20);
                lines.add(String.format("  %2d. %-25s %s  %s commits",
                        i + 1, username, bar, fmtNumber(contribs)));
            }
        } else {
            lines.add("  No contributor data available.");
        }

        // ── Section 5: Recent Commits ─────────────────────────────────
        lines.add("");
        lines.add("  ── RECENT COMMITS ───────────────────────────────────────────");
        if (!commits.isEmpty()) {
            for (Map<String, Object> c : commits) {
                Map<String, Object> commitObj = map(c, "commit");
                Map<String, Object> author    = map(commitObj, "author");
                String message = str(commitObj, "message");
                if (message.contains("\n")) message = message.substring(0, message.indexOf('\n'));
                if (message.length() > 55) message = message.substring(0, 55);
                String authorName = str(author, "name");
                String dateStr    = str(author, "date");
                String sha        = str(c, "sha");
                if (sha.length() > 7) sha = sha.substring(0, 7);
                lines.add(String.format("  [%s] %-15s %-20s", sha, fmtDate(dateStr), authorName));
                lines.add("          " + message);
                lines.add("");
            }
        } else {
            lines.add("  No commit data available.");
        }

        // ── Section 6: Open Issues ────────────────────────────────────
        lines.add("");
        lines.add("  ── RECENT OPEN ISSUES ───────────────────────────────────────");
        if (!issues.isEmpty()) {
            for (Map<String, Object> issue : issues) {
                String title  = str(issue, "title");
                if (title.length() > 55) title = title.substring(0, 55);
                String number = str(issue, "number");
                String user   = str(map(issue, "user"), "login");
                String opened = fmtDate(issue.get("created_at"));
                lines.add(String.format("  #%-6s %s", number, title));
                lines.add("          Opened by @" + user + "  |  " + opened);
                lines.add("");
            }
        } else {
            lines.add("  No open issues found.");
        }

        // ── Section 7: Latest Release ─────────────────────────────────
        lines.add("");
        lines.add("  ── LATEST RELEASE ───────────────────────────────────────────");
        if (release != null) {
            lines.add("  Tag     : " + str(release, "tag_name"));
            String relName = str(release, "name");
            lines.add("  Name    : " + (relName.isEmpty() ? "N/A" : relName));
            lines.add("  Date    : " + fmtDate(release.get("published_at")));
            lines.add("  URL     : " + str(release, "html_url"));
            String body = str(release, "body").strip();
            if (!body.isEmpty()) {
                if (body.length() > 100) body = body.substring(0, 100) + "...";
                lines.add("  Notes   : " + body);
            }
        } else {
            lines.add("  No releases found for this repository.");
        }

        // ── Section 8: Quick Summary ──────────────────────────────────
        lines.add("");
        lines.add("  ── QUICK SUMMARY ────────────────────────────────────────────");

        long stars  = toLong(info.get("stargazers_count"));
        long forks  = toLong(info.get("forks_count"));
        long ageDays = 0;
        String createdAt = str(info, "created_at");
        if (!createdAt.isEmpty()) {
            try {
                OffsetDateTime dt = OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                ageDays = ChronoUnit.DAYS.between(dt.toInstant(), Instant.now());
            } catch (Exception ignored) {}
        }

        String popularity;
        if (stars > 50000)     popularity = "Extremely popular (50k+ stars)";
        else if (stars > 10000) popularity = "Very popular (10k+ stars)";
        else if (stars > 1000)  popularity = "Popular (1k+ stars)";
        else if (stars > 100)   popularity = "Growing (100+ stars)";
        else                    popularity = "Early stage or niche project";

        double forkRatio = stars > 0 ? Math.round((double) forks / stars * 1000.0) / 10.0 : 0;

        lines.add("  Popularity    : " + popularity);
        lines.add(String.format("  Fork ratio    : %.1f%% of stars have been forked", forkRatio));
        lines.add("  Repo age      : " + ageDays + " days (" + ageDays / 365 + " years)");
        if (!contributors.isEmpty()) {
            lines.add("  Top contributor: @" + str(contributors.get(0), "login") +
                    " with " + fmtNumber(contributors.get(0).get("contributions")) + " commits");
        }
        if (!languages.isEmpty()) {
            String topLang = languages.entrySet().stream()
                    .max(Comparator.comparingLong(e -> toLong(e.getValue())))
                    .map(Map.Entry::getKey).orElse("N/A");
            lines.add("  Primary language: " + topLang);
        }

        lines.add("");
        lines.add("=".repeat(62));
        lines.add("  Report saved to: " + REPORTS_DIR + "/ directory");
        lines.add("=".repeat(62));

        // Print to terminal
        String report = String.join("\n", lines);
        System.out.println(report);

        // Save report
        saveReport(owner, repo, report);

        // Log history
        logHistory(owner, repo, stars, forks);
    }

    // ── File I/O ──────────────────────────────────────────────────────────────
    private static void saveReport(String owner, String repo, String data) throws IOException {
        Files.createDirectories(Paths.get(REPORTS_DIR));
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = REPORTS_DIR + "/" + owner + "_" + repo + "_" + ts + ".txt";
        Files.writeString(Paths.get(filename), data);
        System.out.println("\n  [SAVED] Report written to: " + filename);
    }

    private static void logHistory(String owner, String repo, long stars, long forks) throws IOException {
        boolean exists = Files.exists(Paths.get(HISTORY_FILE));
        try (var writer = new FileWriter(HISTORY_FILE, true);
             var bw = new BufferedWriter(writer)) {
            if (!exists) {
                bw.write("timestamp,owner,repo,stars,forks");
                bw.newLine();
            }
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            bw.write(ts + "," + owner + "," + repo + "," + stars + "," + forks);
            bw.newLine();
        }
        System.out.println("  [LOGGED] Added to search_history.csv\n");
    }

    private static void showHistory() throws IOException {
        if (!Files.exists(Paths.get(HISTORY_FILE))) {
            System.out.println("\n  No search history yet.");
            return;
        }
        List<String> rows = Files.readAllLines(Paths.get(HISTORY_FILE));
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  SEARCH HISTORY");
        System.out.println("=".repeat(60));
        if (rows.size() <= 1) {
            System.out.println("  No searches recorded yet.");
        } else {
            // Show last 10 (skip header)
            List<String> data = rows.subList(1, rows.size());
            int start = Math.max(0, data.size() - 10);
            for (String row : data.subList(start, data.size())) {
                String[] cols = row.split(",");
                if (cols.length >= 4) {
                    System.out.printf("  %s  |  %s/%s  |  Stars: %s%n",
                            cols[0], cols[1], cols[2], cols[3]);
                }
            }
        }
        System.out.println("=".repeat(60));
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Minimal recursive-descent JSON parser (no external dependencies)
    // Supports: objects {}, arrays [], strings "", numbers, booleans, null
    // ────────────────────────────────────────────────────────────────────────────
    static class JsonParser {
        private final String src;
        private int pos = 0;

        private JsonParser(String src) { this.src = src; }

        static Object parse(String json) {
            return new JsonParser(json.strip()).parseValue();
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't') { pos += 4; return Boolean.TRUE; }
            if (c == 'f') { pos += 5; return Boolean.FALSE; }
            if (c == 'n') { pos += 4; return null; }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // skip '{'
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') { pos++; return map; }
            while (pos < src.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                pos++; // skip ':'
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (pos >= src.length()) break;
                char ch = src.charAt(pos);
                if (ch == '}') { pos++; break; }
                if (ch == ',') pos++;
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // skip '['
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') { pos++; return list; }
            while (pos < src.length()) {
                list.add(parseValue());
                skipWhitespace();
                if (pos >= src.length()) break;
                char ch = src.charAt(pos);
                if (ch == ']') { pos++; break; }
                if (ch == ',') pos++;
            }
            return list;
        }

        private String parseString() {
            pos++; // skip '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') break;
                if (c == '\\' && pos < src.length()) {
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            String hex = src.substring(pos, Math.min(pos + 4, src.length()));
                            try { sb.append((char) Integer.parseInt(hex, 16)); pos += 4; }
                            catch (NumberFormatException e) { sb.append('u'); }
                            break;
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Number parseNumber() {
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n' || c == '\r' || c == '\t')
                    break;
                pos++;
            }
            String num = src.substring(start, pos);
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E"))
                    return Double.parseDouble(num);
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }
    }
}
