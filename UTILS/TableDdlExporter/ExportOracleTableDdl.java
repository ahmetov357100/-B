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
import java.util.ArrayList;
import java.util.List;

public class ExportOracleTableDdl {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException("Usage: java ExportOracleTableDdl <url> <user> <password> <owner> <table> <outputFile>");
        }

        String url = args[0];
        String user = args[1];
        String password = args[2];
        String owner = args[3].toUpperCase();
        String table = args[4].toUpperCase();
        Path outputFile = Path.of(args[5]);

        exportTableDdl(url, user, password, owner, table, outputFile);
    }

    public static void exportTableDdl(String url, String user, String password, String owner, String table, Path outputFile) throws Exception {
        owner = owner.toUpperCase();
        table = table.toUpperCase();

        Class.forName("oracle.jdbc.OracleDriver");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            exportTableDdl(connection, owner, table, outputFile);
        }
    }

    public static void exportTableDdl(Connection connection, String owner, String table, Path outputFile) throws Exception {
        owner = owner.toUpperCase();
        table = table.toUpperCase();

        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter out = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            configureMetadata(connection);

            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - OBJECT VISIBILITY CHECK");
            writeLine(out, "======================================================================");
            writeQuery(out, connection,
                    "select owner, object_name, object_type, status\n" +
                            "from all_objects\n" +
                            "where object_name = ?\n" +
                            "order by owner, object_type",
                    table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - TABLE DETAILS");
            writeLine(out, "======================================================================");
            writeQuery(out, connection, """
                    select
                      owner,
                      table_name,
                      tablespace_name,
                      num_rows,
                      blocks,
                      avg_row_len,
                      last_analyzed,
                      partitioned,
                      temporary,
                      duration,
                      iot_type
                    from all_tables
                    where owner = ?
                      and table_name = ?""",
                    owner, table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - TABLE DDL");
            writeLine(out, "======================================================================");
            writeClobQuery(out, connection,
                    "select dbms_metadata.get_ddl('TABLE', '" + table + "', '" + owner + "') as ddl from dual");

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - RECONSTRUCTED TABLE DDL FROM ALL_TAB_COLUMNS");
            writeLine(out, "======================================================================");
            writeManualTableDdl(out, connection, owner, table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - COLUMNS");
            writeLine(out, "======================================================================");
            writeQuery(out, connection, """
                    select
                      c.column_id,
                      c.column_name,
                      case
                        when c.data_type in ('CHAR', 'VARCHAR2', 'NCHAR', 'NVARCHAR2') then
                          c.data_type || '(' || c.char_length || case when c.char_used = 'C' then ' CHAR)' else ')' end
                        when c.data_type = 'NUMBER' and c.data_precision is not null and c.data_scale is not null then
                          c.data_type || '(' || c.data_precision || ',' || c.data_scale || ')'
                        when c.data_type = 'NUMBER' and c.data_precision is not null then
                          c.data_type || '(' || c.data_precision || ')'
                        when c.data_type like 'TIMESTAMP%' and c.data_scale is not null then
                          c.data_type || '(' || c.data_scale || ')'
                        else
                          c.data_type
                      end as data_type,
                      c.nullable,
                      c.data_default,
                      cc.comments
                    from all_tab_columns c
                    left join all_col_comments cc
                      on cc.owner = c.owner
                     and cc.table_name = c.table_name
                     and cc.column_name = c.column_name
                    where c.owner = ?
                      and c.table_name = ?
                    order by c.column_id""",
                    owner, table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - CONSTRAINTS");
            writeLine(out, "======================================================================");
            writeQuery(out, connection, """
                    select
                      c.constraint_name,
                      c.constraint_type,
                      case c.constraint_type
                        when 'P' then 'PRIMARY KEY'
                        when 'R' then 'FOREIGN KEY'
                        when 'U' then 'UNIQUE'
                        when 'C' then 'CHECK'
                        else c.constraint_type
                      end as constraint_kind,
                      c.status,
                      c.validated,
                      c.rely,
                      c.deferrable,
                      c.deferred,
                      c.delete_rule,
                      c.r_owner,
                      c.r_constraint_name,
                      c.search_condition
                    from all_constraints c
                    where c.owner = ?
                      and c.table_name = ?
                    order by c.constraint_type, c.constraint_name""",
                    owner, table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - CONSTRAINT COLUMNS");
            writeLine(out, "======================================================================");
            writeQuery(out, connection, """
                    select
                      cc.constraint_name,
                      listagg(cc.column_name, ', ') within group (order by cc.position) as columns
                    from all_cons_columns cc
                    where cc.owner = ?
                      and cc.table_name = ?
                    group by cc.constraint_name
                    order by cc.constraint_name""",
                    owner, table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - CONSTRAINT DDL");
            writeLine(out, "======================================================================");
            writeClobQuery(out, connection,
                    "select dbms_metadata.get_dependent_ddl('CONSTRAINT', ?, ?) as ddl from dual",
                    table, owner);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - REFERENCED BY FOREIGN KEYS");
            writeLine(out, "======================================================================");
            writeQuery(out, connection, """
                    select
                      fk.owner,
                      fk.table_name,
                      fk.constraint_name,
                      fk.status,
                      fk.validated,
                      listagg(fkc.column_name, ', ') within group (order by fkc.position) as columns
                    from all_constraints pk
                    join all_constraints fk
                      on fk.r_owner = pk.owner
                     and fk.r_constraint_name = pk.constraint_name
                     and fk.constraint_type = 'R'
                    join all_cons_columns fkc
                      on fkc.owner = fk.owner
                     and fkc.constraint_name = fk.constraint_name
                     and fkc.table_name = fk.table_name
                    where pk.owner = ?
                      and pk.table_name = ?
                    group by fk.owner, fk.table_name, fk.constraint_name, fk.status, fk.validated
                    order by fk.owner, fk.table_name, fk.constraint_name""",
                    owner, table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - INDEXES");
            writeLine(out, "======================================================================");
            writeQuery(out, connection, """
                    select
                      i.index_name,
                      i.uniqueness,
                      i.index_type,
                      i.status,
                      i.tablespace_name,
                      i.generated,
                      i.visibility
                    from all_indexes i
                    where i.owner = ?
                      and i.table_owner = ?
                      and i.table_name = ?
                    order by i.index_name""",
                    owner, owner, table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - INDEX COLUMNS");
            writeLine(out, "======================================================================");
            writeQuery(out, connection, """
                    select
                      ic.index_name,
                      listagg(ic.column_name || case when ic.descend = 'DESC' then ' DESC' else '' end, ', ')
                        within group (order by ic.column_position) as columns
                    from all_ind_columns ic
                    where ic.index_owner = ?
                      and ic.table_owner = ?
                      and ic.table_name = ?
                    group by ic.index_name
                    order by ic.index_name""",
                    owner, owner, table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - FUNCTION-BASED INDEX EXPRESSIONS");
            writeLine(out, "======================================================================");
            writeQuery(out, connection, """
                    select
                      ie.index_name,
                      ie.column_position,
                      ie.column_expression
                    from all_ind_expressions ie
                    where ie.index_owner = ?
                      and ie.table_owner = ?
                      and ie.table_name = ?
                    order by ie.index_name, ie.column_position""",
                    owner, owner, table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - INDEX DDL");
            writeLine(out, "======================================================================");
            writeClobQuery(out, connection, """
                    select dbms_metadata.get_ddl('INDEX', i.index_name, i.owner) as ddl
                    from all_indexes i
                    where i.owner = ?
                      and i.table_owner = ?
                      and i.table_name = ?
                    order by i.index_name""",
                    owner, owner, table);

            writeLine(out, "");
            writeLine(out, "======================================================================");
            writeLine(out, owner + "." + table + " - RECONSTRUCTED INDEX DDL FROM ALL_INDEXES");
            writeLine(out, "======================================================================");
            writeManualIndexDdl(out, connection, owner, table);
        }
    }

    private static void configureMetadata(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    begin
                      dbms_metadata.set_transform_param(dbms_metadata.session_transform, 'SQLTERMINATOR', true);
                      dbms_metadata.set_transform_param(dbms_metadata.session_transform, 'PRETTY', true);
                      dbms_metadata.set_transform_param(dbms_metadata.session_transform, 'SEGMENT_ATTRIBUTES', false);
                      dbms_metadata.set_transform_param(dbms_metadata.session_transform, 'STORAGE', false);
                    end;""");
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

    private static void writeManualTableDdl(BufferedWriter out, Connection connection, String owner, String table) throws SQLException, IOException {
        String sql = """
                select
                  c.column_name,
                  case
                    when c.data_type in ('CHAR', 'VARCHAR2', 'NCHAR', 'NVARCHAR2') then
                      c.data_type || '(' || c.char_length || case when c.char_used = 'C' then ' CHAR)' else ')' end
                    when c.data_type = 'NUMBER' and c.data_precision is not null and c.data_scale is not null then
                      c.data_type || '(' || c.data_precision || ',' || c.data_scale || ')'
                    when c.data_type = 'NUMBER' and c.data_precision is not null then
                      c.data_type || '(' || c.data_precision || ')'
                    when c.data_type like 'TIMESTAMP%' and c.data_scale is not null then
                      c.data_type || '(' || c.data_scale || ')'
                    else
                      c.data_type
                  end as data_type,
                  c.nullable,
                  c.data_default
                from all_tab_columns c
                where c.owner = '""" + owner + "'\n" + """
                  and c.table_name = '""" + table + "'\n" + """
                order by c.column_id""";

        List<String> lines = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                StringBuilder line = new StringBuilder();
                line.append("  ").append(rs.getString("COLUMN_NAME")).append(" ").append(rs.getString("DATA_TYPE"));
                String defaultValue = rs.getString("DATA_DEFAULT");
                if (defaultValue != null && !defaultValue.isBlank()) {
                    line.append(" DEFAULT ").append(defaultValue.trim());
                }
                if ("N".equals(rs.getString("NULLABLE"))) {
                    line.append(" NOT NULL");
                }
                lines.add(line.toString());
            }
        }

        if (lines.isEmpty()) {
            writeLine(out, "<no visible columns>");
            return;
        }

        writeLine(out, "CREATE TABLE " + owner + "." + table + " (");
        for (int i = 0; i < lines.size(); i++) {
            writeLine(out, lines.get(i) + (i + 1 == lines.size() ? "" : ","));
        }
        writeLine(out, ");");
    }

    private static void writeManualIndexDdl(BufferedWriter out, Connection connection, String owner, String table) throws SQLException, IOException {
        String sql = """
                select
                  i.index_name,
                  i.uniqueness,
                  i.tablespace_name,
                  listagg(ic.column_name || case when ic.descend = 'DESC' then ' DESC' else '' end, ', ')
                    within group (order by ic.column_position) as columns
                from all_indexes i
                join all_ind_columns ic
                  on ic.index_owner = i.owner
                 and ic.index_name = i.index_name
                 and ic.table_owner = i.table_owner
                 and ic.table_name = i.table_name
                where i.owner = '""" + owner + "'\n" + """
                  and i.table_owner = '""" + owner + "'\n" + """
                  and i.table_name = '""" + table + "'\n" + """
                group by i.index_name, i.uniqueness, i.tablespace_name
                order by i.index_name""";

        int rows = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                rows++;
                String unique = "UNIQUE".equals(rs.getString("UNIQUENESS")) ? "UNIQUE " : "";
                String tablespace = rs.getString("TABLESPACE_NAME");
                String ddl = "CREATE " + unique + "INDEX " + owner + "." + rs.getString("INDEX_NAME") +
                        " ON " + owner + "." + table + " (" + rs.getString("COLUMNS") + ")" +
                        (tablespace == null || tablespace.isBlank() ? "" : " TABLESPACE " + tablespace) + ";";
                writeLine(out, ddl);
            }
        }
        if (rows == 0) {
            writeLine(out, "<no visible indexes>");
        }
    }

    private static void writeLine(BufferedWriter out, String value) throws IOException {
        out.write(value);
        out.newLine();
    }
}
