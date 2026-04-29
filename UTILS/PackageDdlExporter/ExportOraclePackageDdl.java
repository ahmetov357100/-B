import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class ExportOraclePackageDdl {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException("Usage: java ExportOraclePackageDdl <url> <user> <password> <owner> <package> <outputFile>");
        }

        String url = args[0];
        String user = args[1];
        String password = args[2];
        String owner = args[3].toUpperCase();
        String packageName = args[4].toUpperCase();
        Path outputFile = Path.of(args[5]);

        Class.forName("oracle.jdbc.OracleDriver");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            exportPackageDdl(connection, owner, packageName, outputFile);
        }
    }

    public static void exportPackageDdl(Connection connection, String owner, String packageName, Path outputFile) throws Exception {
        owner = owner.toUpperCase();
        packageName = packageName.toUpperCase();

        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter out = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            configureMetadata(connection);

            writeSection(out, owner + "." + packageName + " - OBJECT VISIBILITY CHECK");
            writeQuery(out, connection, """
                    select owner, object_name, object_type, status
                    from all_objects
                    where owner = ?
                      and object_name = ?
                      and object_type in ('PACKAGE', 'PACKAGE BODY')
                    order by object_type""",
                    owner, packageName);

            writeSection(out, owner + "." + packageName + " - PACKAGE SPEC DDL");
            if (objectExists(connection, owner, packageName, "PACKAGE")) {
                writeClobQuery(out, connection,
                        "select dbms_metadata.get_ddl('PACKAGE', ?, ?) as ddl from dual",
                        packageName, owner);
            } else {
                writeLine(out, "<no package spec>");
            }

            writeSection(out, owner + "." + packageName + " - PACKAGE BODY DDL");
            if (objectExists(connection, owner, packageName, "PACKAGE BODY")) {
                writeClobQuery(out, connection,
                        "select dbms_metadata.get_ddl('PACKAGE_BODY', ?, ?) as ddl from dual",
                        packageName, owner);
            } else {
                writeLine(out, "<no package body>");
            }

            writeSection(out, owner + "." + packageName + " - PACKAGE SOURCE FROM ALL_SOURCE");
            writeSource(out, connection, owner, packageName);

            writeSection(out, owner + "." + packageName + " - DEPENDENCIES");
            writeQuery(out, connection, """
                    select
                      type,
                      referenced_owner,
                      referenced_name,
                      referenced_type,
                      dependency_type
                    from all_dependencies
                    where owner = ?
                      and name = ?
                      and type in ('PACKAGE', 'PACKAGE BODY')
                    order by type, referenced_owner, referenced_type, referenced_name""",
                    owner, packageName);

            writeSection(out, owner + "." + packageName + " - ERRORS");
            writeQuery(out, connection, """
                    select
                      type,
                      sequence,
                      line,
                      position,
                      text
                    from all_errors
                    where owner = ?
                      and name = ?
                      and type in ('PACKAGE', 'PACKAGE BODY')
                    order by type, sequence""",
                    owner, packageName);
        }
    }

    private static void configureMetadata(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    begin
                      dbms_metadata.set_transform_param(dbms_metadata.session_transform, 'SQLTERMINATOR', true);
                      dbms_metadata.set_transform_param(dbms_metadata.session_transform, 'PRETTY', true);
                    end;""");
        }
    }

    private static boolean objectExists(Connection connection, String owner, String objectName, String objectType) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select 1
                from all_objects
                where owner = ?
                  and object_name = ?
                  and object_type = ?
                  and rownum = 1""")) {
            statement.setString(1, owner);
            statement.setString(2, objectName);
            statement.setString(3, objectType);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void writeSource(BufferedWriter out, Connection connection, String owner, String packageName) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select type, line, text
                from all_source
                where owner = ?
                  and name = ?
                  and type in ('PACKAGE', 'PACKAGE BODY')
                order by case type when 'PACKAGE' then 1 else 2 end, line""")) {
            statement.setString(1, owner);
            statement.setString(2, packageName);
            try (ResultSet rs = statement.executeQuery()) {
                String currentType = null;
                int rows = 0;
                while (rs.next()) {
                    rows++;
                    String type = rs.getString("TYPE");
                    if (!type.equals(currentType)) {
                        if (currentType != null) {
                            writeLine(out, "");
                        }
                        currentType = type;
                        writeLine(out, "-- " + type);
                    }
                    out.write(rs.getString("TEXT") == null ? "" : rs.getString("TEXT"));
                }
                if (rows == 0) {
                    writeLine(out, "<no source rows>");
                }
                writeLine(out, "");
                writeLine(out, "-- rows: " + rows);
            }
        }
    }

    private static void writeClobQuery(BufferedWriter out, Connection connection, String sql, String... params) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    String value = rs.getString(1);
                    writeLine(out, value == null ? "" : value);
                }
                if (!any) {
                    writeLine(out, "<no rows>");
                }
            }
        } catch (SQLException e) {
            writeLine(out, "<metadata error>");
            writeLine(out, e.getMessage());
        }
    }

    private static void writeQuery(BufferedWriter out, Connection connection, String sql, String... params) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) {
                        out.write("\t");
                    }
                    out.write(meta.getColumnLabel(i));
                }
                out.newLine();

                int rows = 0;
                while (rs.next()) {
                    rows++;
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) {
                            out.write("\t");
                        }
                        String value = rs.getString(i);
                        out.write(value == null ? "" : value.replace("\r", " ").replace("\n", " "));
                    }
                    out.newLine();
                }
                writeLine(out, "-- rows: " + rows);
            }
        }
    }

    private static void setParams(PreparedStatement statement, String... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setString(i + 1, params[i]);
        }
    }

    private static void writeSection(BufferedWriter out, String title) throws IOException {
        writeLine(out, "");
        writeLine(out, "======================================================================");
        writeLine(out, title);
        writeLine(out, "======================================================================");
    }

    private static void writeLine(BufferedWriter out, String value) throws IOException {
        out.write(value);
        out.newLine();
    }
}
