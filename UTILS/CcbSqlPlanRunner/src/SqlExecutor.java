import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

final class SqlExecutor {
    private SqlExecutor() {
    }

    static String loadSql(Path file) throws IOException {
        String sql = Files.readString(file, StandardCharsets.UTF_8);
        sql = stripBom(sql).trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.isEmpty()) {
            throw new IOException("SQL file is empty: " + file);
        }
        return sql;
    }

    static String findMarker(String sql) {
        String[] lines = sql.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") && trimmed.length() > 2) {
                return trimmed.substring(2).trim();
            }
        }
        return "";
    }

    static SqlExecutionResult executeTwiceAndCount(Connection connection, String sql, Map<String, String> bindValues)
            throws SQLException {
        ParsedSql parsedSql = NamedBindParser.parse(sql);
        TimedRows run1 = executeAndDrain(connection, parsedSql, bindValues);
        TimedRows run2 = executeAndDrain(connection, parsedSql, bindValues);
        long rowCount = countRows(connection, parsedSql, bindValues, run2.rows);
        return new SqlExecutionResult(run1.rows, run2.rows, rowCount, run1.elapsedMs, run2.elapsedMs);
    }

    private static TimedRows executeAndDrain(Connection connection, ParsedSql sql, Map<String, String> bindValues)
            throws SQLException {
        long started = System.nanoTime();
        long rows = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql.sql)) {
            NamedBindParser.bind(statement, sql, bindValues);
            statement.setFetchSize(1000);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows++;
                }
            }
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        return new TimedRows(rows, elapsedMs);
    }

    private static long countRows(Connection connection, ParsedSql sql, Map<String, String> bindValues, long fallback)
            throws SQLException {
        ParsedSql countSql = new ParsedSql("SELECT COUNT(1) FROM (\n" + sql.sql + "\n)", sql.bindNames);
        try (PreparedStatement statement = connection.prepareStatement(countSql.sql)) {
            NamedBindParser.bind(statement, countSql, bindValues);
            try (ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : fallback;
            }
        } catch (SQLException ex) {
            return fallback;
        }
    }

    private static String stripBom(String value) {
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private static final class TimedRows {
        final long rows;
        final long elapsedMs;

        TimedRows(long rows, long elapsedMs) {
            this.rows = rows;
            this.elapsedMs = elapsedMs;
        }
    }
}
