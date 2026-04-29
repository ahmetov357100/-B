import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExportOracleTableDdlsFromList {
    private static final Pattern USER_COMMENT = Pattern.compile("^\\s*--\\s*user\\s*:\\s*(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD_COMMENT = Pattern.compile("^\\s*--\\s*password\\s*:\\s*(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST = Pattern.compile("\\(\\s*HOST\\s*=\\s*([^\\)\\s]+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PORT = Pattern.compile("\\(\\s*PORT\\s*=\\s*([^\\)\\s]+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVICE = Pattern.compile("\\(\\s*SERVICE_NAME\\s*=\\s*([^\\)\\s]+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws Exception {
        if (args.length > 3) {
            throw new IllegalArgumentException("""
                    Usage:
                      java ExportOracleTableDdlsFromList [tableListFile] [connectionFile] [outputDir]

                    Defaults:
                      tableListFile = table_list.txt
                      connectionFile = tnsnames.txt
                      outputDir = current directory
                    """);
        }

        Path baseDir = Path.of("").toAbsolutePath().normalize();
        Path tableListFile = resolveNearBase(baseDir, args.length >= 1 ? args[0] : "table_list.txt");
        Path connectionFile = resolveNearBase(baseDir, args.length >= 2 ? args[1] : "tnsnames.txt");
        Path outputDir = resolveNearBase(baseDir, args.length >= 3 ? args[2] : ".");

        ConnectionConfig config = readConnectionConfig(connectionFile);
        List<TableRef> tables = readTableList(tableListFile, config.owner);
        Files.createDirectories(outputDir);

        Path logFile = outputDir.resolve("ddl_export_summary.txt");
        int success = 0;
        int failed = 0;

        try (BufferedWriter log = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
            writeLog(log, "Table list: " + tableListFile);
            writeLog(log, "Connection file: " + connectionFile);
            writeLog(log, "Output directory: " + outputDir);
            writeLog(log, "JDBC URL: " + config.url);
            writeLog(log, "Default owner: " + config.owner);
            writeLog(log, "Tables found: " + tables.size());
            writeLog(log, "");

            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection connection = DriverManager.getConnection(config.url, config.user, config.password)) {
                for (TableRef table : tables) {
                    Path outputFile = outputDir.resolve(table.table + ".txt");
                    System.out.println("Exporting " + table.owner + "." + table.table + " -> " + outputFile);
                    try {
                        ExportOracleTableDdl.exportTableDdl(connection, table.owner, table.table, outputFile);
                        VerificationResult verification = verifyOutput(outputFile, table);
                        if (verification.ok()) {
                            success++;
                            writeLog(log, "OK     " + table.owner + "." + table.table + " -> " + outputFile.getFileName());
                        } else {
                            failed++;
                            writeLog(log, "FAILED " + table.owner + "." + table.table + " -> " + outputFile.getFileName());
                            for (String issue : verification.issues) {
                                writeLog(log, "       " + issue);
                            }
                        }
                    } catch (Exception e) {
                        failed++;
                        writeLog(log, "FAILED " + table.owner + "." + table.table + " -> " + outputFile.getFileName());
                        writeLog(log, "       " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                    log.flush();
                }
            }

            writeLog(log, "");
            writeLog(log, "Success: " + success);
            writeLog(log, "Failed: " + failed);
        }

        System.out.println("Done. Success: " + success + ", failed: " + failed);
        System.out.println("Summary: " + logFile);
        if (failed > 0) {
            System.exit(2);
        }
    }

    private static Path resolveNearBase(Path baseDir, String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path.normalize() : baseDir.resolve(path).normalize();
    }

    private static ConnectionConfig readConnectionConfig(Path file) throws IOException {
        List<String> lines = readAllLines(file);
        String text = String.join("\n", lines);

        String url = null;
        String user = null;
        String password = null;
        String owner = "CISADM";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Matcher userMatcher = USER_COMMENT.matcher(trimmed);
            Matcher passwordMatcher = PASSWORD_COMMENT.matcher(trimmed);
            if (userMatcher.matches()) {
                user = userMatcher.group(1);
                continue;
            }
            if (passwordMatcher.matches()) {
                password = passwordMatcher.group(1);
                continue;
            }

            int equals = trimmed.indexOf('=');
            if (equals > 0 && !trimmed.startsWith("(")) {
                String key = trimmed.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                String value = unquote(trimmed.substring(equals + 1).trim());
                switch (key) {
                    case "url", "jdbc_url" -> url = value;
                    case "user", "username" -> user = value;
                    case "password" -> password = value;
                    case "owner", "schema" -> owner = value.toUpperCase(Locale.ROOT);
                    default -> {
                    }
                }
            }
        }

        if (url == null || url.isBlank()) {
            String host = firstMatch(HOST, text);
            String port = firstMatch(PORT, text);
            String service = firstMatch(SERVICE, text);
            if (host != null && port != null && service != null) {
                url = "jdbc:oracle:thin:@//" + host + ":" + port + "/" + service;
            }
        }

        require(url, "url/jdbc_url or HOST+PORT+SERVICE_NAME", file);
        require(user, "user", file);
        require(password, "password", file);
        return new ConnectionConfig(url, user, password, owner);
    }

    private static List<TableRef> readTableList(Path file, String defaultOwner) throws IOException {
        List<TableRef> tables = new ArrayList<>();
        for (String line : readAllLines(file)) {
            String candidate = extractTableName(line);
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String upper = candidate.toUpperCase(Locale.ROOT);
            if ("TABLE_NAME".equals(upper) || upper.startsWith("--") || upper.startsWith("#")) {
                continue;
            }

            String owner = defaultOwner;
            String table = upper;
            int dot = upper.indexOf('.');
            if (dot >= 0) {
                owner = upper.substring(0, dot);
                table = upper.substring(dot + 1);
            }
            if (!owner.isBlank() && !table.isBlank()) {
                tables.add(new TableRef(owner, table));
            }
        }
        return tables;
    }

    private static String extractTableName(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        List<String> columns = splitSemicolonCsv(trimmed);
        String value = columns.isEmpty() ? trimmed : columns.get(columns.size() - 1);
        return unquote(value.trim());
    }

    private static List<String> splitSemicolonCsv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
                value.append(ch);
            } else if (ch == ';' && !quoted) {
                result.add(value.toString());
                value.setLength(0);
            } else {
                value.append(ch);
            }
        }
        result.add(value.toString());
        return result;
    }

    private static VerificationResult verifyOutput(Path file, TableRef table) throws IOException {
        VerificationResult result = new VerificationResult();
        if (!Files.exists(file)) {
            result.issues.add("output file does not exist");
            return result;
        }
        if (Files.size(file) == 0) {
            result.issues.add("output file is empty");
            return result;
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);
        requireContains(result, content, table.owner + "." + table.table + " - OBJECT VISIBILITY CHECK");
        requireContains(result, content, table.owner + "\t" + table.table + "\tTABLE\tVALID");
        requireContains(result, content, table.owner + "." + table.table + " - TABLE DETAILS");
        requireContains(result, content, table.owner + "." + table.table + " - TABLE DDL");
        requireContains(result, content, table.owner + "." + table.table + " - COLUMNS");
        requireContains(result, content, table.owner + "." + table.table + " - CONSTRAINTS");
        requireContains(result, content, table.owner + "." + table.table + " - INDEXES");
        if (content.contains("<metadata error>")) {
            result.issues.add("metadata error found");
        }
        return result;
    }

    private static void requireContains(VerificationResult result, String content, String expected) {
        if (!content.contains(expected)) {
            result.issues.add("missing: " + expected);
        }
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static void require(String value, String name, Path file) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + name + " in " + file);
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }

    private static List<String> readAllLines(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8.lines().toList();
        }
        return new String(bytes, Charset.defaultCharset()).lines().toList();
    }

    private static void writeLog(BufferedWriter log, String value) throws IOException {
        log.write(value);
        log.newLine();
    }

    private record ConnectionConfig(String url, String user, String password, String owner) {
    }

    private record TableRef(String owner, String table) {
    }

    private static class VerificationResult {
        private final List<String> issues = new ArrayList<>();

        private boolean ok() {
            return issues.isEmpty();
        }
    }
}
