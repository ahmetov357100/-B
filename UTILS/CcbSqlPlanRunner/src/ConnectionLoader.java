import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ConnectionLoader {
    private ConnectionLoader() {
    }

    static Config load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Map<String, String> values = new LinkedHashMap<>();

        int descriptorStart = -1;
        String alias = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                String lowerKey = key.toLowerCase();
                if ("user".equals(lowerKey) || "password".equals(lowerKey)
                        || "owner".equals(lowerKey) || "jdbc_url".equals(lowerKey)) {
                    values.put(lowerKey, value);
                } else if (lowerKey.startsWith("bind.")) {
                    values.put(lowerKey, value);
                } else {
                    descriptorStart = i;
                    alias = key;
                    break;
                }
            }
        }

        String user = required(values, "user", file);
        String password = required(values, "password", file);
        String owner = values.getOrDefault("owner", "");
        String jdbcUrl = values.get("jdbc_url");
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            if (descriptorStart < 0) {
                throw new IOException("jdbc_url or TNS descriptor is required in " + file);
            }
            jdbcUrl = toJdbcUrl(alias, lines.subList(descriptorStart, lines.size()));
        }
        Map<String, String> bindValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().startsWith("bind.")) {
                bindValues.put(entry.getKey().substring("bind.".length()).toUpperCase(), entry.getValue());
            }
        }
        return new Config(user, password, owner, jdbcUrl, bindValues);
    }

    static Connection connect(Config config) throws SQLException {
        Connection connection = DriverManager.getConnection(config.jdbcUrl, config.user, config.password);
        if (config.owner != null && !config.owner.isBlank()) {
            setCurrentSchema(connection, config.owner);
        }
        return connection;
    }

    private static String required(Map<String, String> values, String key, Path file) throws IOException {
        String value = values.get(key);
        if (value == null || value.isEmpty()) {
            throw new IOException(key + " is required in " + file);
        }
        return value;
    }

    private static String toJdbcUrl(String alias, List<String> descriptorLines) {
        StringBuilder descriptor = new StringBuilder();
        boolean afterAlias = false;
        for (String line : descriptorLines) {
            if (!afterAlias) {
                int eq = line.indexOf('=');
                if (eq >= 0) {
                    String right = line.substring(eq + 1).trim();
                    if (!right.isEmpty()) {
                        descriptor.append(right);
                    }
                    afterAlias = true;
                }
            } else {
                descriptor.append(line.trim());
            }
        }
        if (descriptor.length() == 0) {
            throw new IllegalArgumentException("Empty TNS descriptor for alias " + alias);
        }
        return "jdbc:oracle:thin:@" + descriptor;
    }

    private static void setCurrentSchema(Connection connection, String owner) throws SQLException {
        String schema = owner.trim().toUpperCase();
        if (!schema.matches("[A-Z][A-Z0-9_$#]*")) {
            throw new SQLException("Invalid owner/current_schema value: " + owner);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER SESSION SET CURRENT_SCHEMA = " + schema);
        }
    }
}
