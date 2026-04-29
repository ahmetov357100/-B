import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CcbSqlPlanRunner {
    private CcbSqlPlanRunner() {
    }

    public static void main(String[] args) {
        Path queryFile = Path.of(args.length > 0
                ? args[0]
                : "sql/DZ_TODO_LIST_CLAIM/DZ_TODO_LIST_CLAIM v0.sql");
        Path connectionFile = Path.of(args.length > 1 ? args[1] : "config/tnsnames.txt");
        Path outputDir = Path.of(args.length > 2 ? args[2] : "runs/DZ_TODO_LIST_CLAIM/manual");
        String version = VersionUtil.versionOf(queryFile);

        try {
            System.out.println("Loading query...");
            String rawSql = SqlExecutor.loadSql(queryFile);
            String marker = SqlExecutor.findMarker(rawSql);
            if (marker.isEmpty()) {
                throw new IllegalArgumentException("SQL marker comment is required, for example: --MY_QUERY_MARKER v0");
            }

            Config config = ConnectionLoader.load(connectionFile);
            Map<String, String> bindValues = new LinkedHashMap<>(config.bindValues);
            bindValues.putAll(SqlBindValues.fromLeadingJsonComment(rawSql));
            applyDefaultBindValues(rawSql, bindValues);
            boolean freemarker = FreemarkerSqlRenderer.hasFreemarker(rawSql);
            String sql = freemarker ? FreemarkerSqlRenderer.render(rawSql, bindValues) : rawSql;
            List<String> missingBindValues = missingBindValues(sql, bindValues);

            System.out.println("Connecting Oracle...");
            try (Connection connection = ConnectionLoader.connect(config)) {
                System.out.println("Executing run #1...");
                System.out.println("Executing run #2...");
                SqlExecutionResult execution = SqlExecutor.executeTwiceAndCount(connection, sql, bindValues);

                System.out.println("Counting rows...");
                System.out.println("Finding SQL_ID...");
                SqlCursorInfo cursor = SqlIdFinder.findLatest(connection, marker);

                System.out.println("Fetching plan...");
                String plan = PlanFetcher.fetch(connection, cursor);

                System.out.println("Writing files...");
                OutputWriter.writeSuccess(outputDir, queryFile, version, execution, cursor, plan,
                        bindValues, missingBindValues);
                if (freemarker) {
                    Files.createDirectories(outputDir);
                    Files.writeString(outputDir.resolve("query_rendered_" + version + ".sql"),
                            sql, StandardCharsets.UTF_8);
                }
            }
            System.out.println("Done.");
        } catch (Throwable error) {
            try {
                OutputWriter.writeError(outputDir, queryFile, version, error);
            } catch (Exception writeError) {
                error.addSuppressed(writeError);
            }
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static List<String> missingBindValues(String sql, Map<String, String> bindValues) {
        ParsedSql parsedSql = NamedBindParser.parse(sql);
        Set<String> uniqueNames = new LinkedHashSet<>(parsedSql.bindNames);
        List<String> missing = new ArrayList<>();
        for (String name : uniqueNames) {
            if (!bindValues.containsKey(name)) {
                missing.add(name);
            }
        }
        return missing;
    }

    private static void applyDefaultBindValues(String sql, Map<String, String> bindValues) {
        ParsedSql parsedSql = NamedBindParser.parse(sql);
        if (parsedSql.bindNames.contains("USER_ID") && !bindValues.containsKey("USER_ID")) {
            bindValues.put("USER_ID", "TEK1TST");
        }
    }
}
