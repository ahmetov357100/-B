import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class OutputWriter {
    private OutputWriter() {
    }

    static void writeSuccess(Path outputDir, Path queryFile, String version, SqlExecutionResult execution,
                             SqlCursorInfo cursor, String plan, Map<String, String> bindValues,
                             List<String> missingBindValues) throws IOException {
        Files.createDirectories(outputDir);
        writeResult(outputDir.resolve("result_" + version + ".txt"), queryFile, version, execution, cursor,
                bindValues, missingBindValues);
        writeCount(outputDir.resolve("count_" + version + ".txt"), execution);
        writeSqlId(outputDir.resolve("sql_id_" + version + ".txt"), cursor);
        Files.writeString(outputDir.resolve("plan_" + version + ".txt"), plan == null ? "" : plan, StandardCharsets.UTF_8);
        writeMetrics(outputDir.resolve("metrics_" + version + ".json"), queryFile, version, execution, cursor,
                bindValues, missingBindValues);
    }

    static void writeError(Path outputDir, Path queryFile, String version, Throwable error) throws IOException {
        Files.createDirectories(outputDir);
        StringBuilder text = new StringBuilder();
        text.append("status=ERROR").append(System.lineSeparator());
        text.append("query_file=").append(queryFile.getFileName()).append(System.lineSeparator());
        text.append("version=").append(version).append(System.lineSeparator());
        text.append("message=").append(error.getMessage()).append(System.lineSeparator());
        text.append("exception=").append(error.getClass().getName()).append(System.lineSeparator());
        text.append("stacktrace=").append(stackTrace(error)).append(System.lineSeparator());
        Files.writeString(outputDir.resolve("result_" + version + ".txt"), text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeResult(Path file, Path queryFile, String version, SqlExecutionResult execution,
                                    SqlCursorInfo cursor, Map<String, String> bindValues,
                                    List<String> missingBindValues) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append(cursor == null ? "status=WARNING" : "status=OK").append(System.lineSeparator());
        text.append("query_file=").append(queryFile.getFileName()).append(System.lineSeparator());
        text.append("version=").append(version).append(System.lineSeparator());
        text.append("rows_run1=").append(execution.rowsRun1).append(System.lineSeparator());
        text.append("rows_run2=").append(execution.rowsRun2).append(System.lineSeparator());
        text.append("row_count=").append(execution.rowCount).append(System.lineSeparator());
        text.append("java_elapsed_run1_ms=").append(execution.javaElapsedRun1Ms).append(System.lineSeparator());
        text.append("java_elapsed_run2_ms=").append(execution.javaElapsedRun2Ms).append(System.lineSeparator());
        if (!bindValues.isEmpty()) {
            text.append("bind_values=").append(bindValues).append(System.lineSeparator());
        }
        if (!missingBindValues.isEmpty()) {
            text.append("missing_bind_values=").append(missingBindValues).append(System.lineSeparator());
        }
        text.append(System.lineSeparator());
        if (cursor == null) {
            text.append("sql_id_not_found=true").append(System.lineSeparator());
        } else {
            appendCursorSummary(text, cursor);
        }
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeCount(Path file, SqlExecutionResult execution) throws IOException {
        String text = "row_count=" + execution.rowCount + System.lineSeparator()
                + "rows_run1=" + execution.rowsRun1 + System.lineSeparator()
                + "rows_run2=" + execution.rowsRun2 + System.lineSeparator();
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static void writeSqlId(Path file, SqlCursorInfo cursor) throws IOException {
        StringBuilder text = new StringBuilder();
        if (cursor == null) {
            text.append("sql_id_not_found=true").append(System.lineSeparator());
        } else {
            appendCursorSummary(text, cursor);
        }
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeMetrics(Path file, Path queryFile, String version, SqlExecutionResult execution,
                                     SqlCursorInfo cursor, Map<String, String> bindValues,
                                     List<String> missingBindValues) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"status\": \"OK\",\n");
        json.append("  \"query_file\": ").append(JsonUtil.quote(queryFile.getFileName().toString())).append(",\n");
        json.append("  \"version\": ").append(JsonUtil.quote(version)).append(",\n");
        json.append("  \"rows_run1\": ").append(execution.rowsRun1).append(",\n");
        json.append("  \"rows_run2\": ").append(execution.rowsRun2).append(",\n");
        json.append("  \"row_count\": ").append(execution.rowCount).append(",\n");
        json.append("  \"java_elapsed_run1_ms\": ").append(execution.javaElapsedRun1Ms).append(",\n");
        json.append("  \"java_elapsed_run2_ms\": ").append(execution.javaElapsedRun2Ms).append(",\n");
        json.append("  \"bind_values\": ").append(bindValuesJson(bindValues)).append(",\n");
        json.append("  \"missing_bind_values\": ").append(stringArrayJson(missingBindValues)).append(",\n");
        json.append("  \"cursor\": ");
        if (cursor == null) {
            json.append("null\n");
        } else {
            json.append("{\n");
            json.append("    \"sql_id\": ").append(JsonUtil.quote(cursor.sqlId)).append(",\n");
            json.append("    \"child_number\": ").append(cursor.childNumber).append(",\n");
            json.append("    \"executions\": ").append(cursor.executions).append(",\n");
            json.append("    \"last_active_time\": ").append(JsonUtil.quote(cursor.lastActiveTime)).append(",\n");
            json.append("    \"plan_hash_value\": ").append(cursor.planHashValue).append(",\n");
            json.append("    \"oracle_elapsed_ms\": ").append(cursor.elapsedTime / 1000L).append(",\n");
            json.append("    \"oracle_cpu_ms\": ").append(cursor.cpuTime / 1000L).append(",\n");
            json.append("    \"buffer_gets\": ").append(cursor.bufferGets).append(",\n");
            json.append("    \"disk_reads\": ").append(cursor.diskReads).append(",\n");
            json.append("    \"rows_processed\": ").append(cursor.rowsProcessed).append(",\n");
            json.append("    \"fetches\": ").append(cursor.fetches).append(",\n");
            json.append("    \"parse_calls\": ").append(cursor.parseCalls).append(",\n");
            json.append("    \"loads\": ").append(cursor.loads).append(",\n");
            json.append("    \"invalidations\": ").append(cursor.invalidations).append("\n");
            json.append("  }\n");
        }
        json.append("}\n");
        Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
    }

    private static String bindValuesJson(Map<String, String> bindValues) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : bindValues.entrySet()) {
            if (!first) {
                json.append(", ");
            }
            first = false;
            json.append(JsonUtil.quote(entry.getKey())).append(": ").append(JsonUtil.quote(entry.getValue()));
        }
        return json.append("}").toString();
    }

    private static String stringArrayJson(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(JsonUtil.quote(values.get(i)));
        }
        return json.append("]").toString();
    }

    private static void appendCursorSummary(StringBuilder text, SqlCursorInfo cursor) {
        text.append("sql_id=").append(cursor.sqlId).append(System.lineSeparator());
        text.append("child_number=").append(cursor.childNumber).append(System.lineSeparator());
        text.append("plan_hash_value=").append(cursor.planHashValue).append(System.lineSeparator());
        text.append("executions=").append(cursor.executions).append(System.lineSeparator());
        text.append("last_active_time=").append(cursor.lastActiveTime).append(System.lineSeparator());
        text.append("oracle_elapsed_ms=").append(cursor.elapsedTime / 1000L).append(System.lineSeparator());
        text.append("oracle_cpu_ms=").append(cursor.cpuTime / 1000L).append(System.lineSeparator());
        text.append("buffer_gets=").append(cursor.bufferGets).append(System.lineSeparator());
        text.append("disk_reads=").append(cursor.diskReads).append(System.lineSeparator());
        text.append("rows_processed=").append(cursor.rowsProcessed).append(System.lineSeparator());
        text.append("fetches=").append(cursor.fetches).append(System.lineSeparator());
        text.append("parse_calls=").append(cursor.parseCalls).append(System.lineSeparator());
        text.append("loads=").append(cursor.loads).append(System.lineSeparator());
        text.append("invalidations=").append(cursor.invalidations).append(System.lineSeparator());
    }

    private static String stackTrace(Throwable error) {
        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        return buffer.toString().replace("\r", "\\r").replace("\n", "\\n");
    }
}
