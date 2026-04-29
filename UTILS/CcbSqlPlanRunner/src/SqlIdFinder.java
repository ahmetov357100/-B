import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class SqlIdFinder {
    private SqlIdFinder() {
    }

    static SqlCursorInfo findLatest(Connection connection, String marker) throws SQLException {
        if (marker == null || marker.isEmpty()) {
            return null;
        }

        String sql = ""
                + "SELECT sql_id, child_number, executions, "
                + "       TO_CHAR(last_active_time, 'YYYY-MM-DD HH24:MI:SS') AS last_active_time, "
                + "       elapsed_time, cpu_time, buffer_gets, disk_reads, rows_processed, "
                + "       fetches, parse_calls, loads, invalidations, plan_hash_value "
                + "FROM v$sql "
                + "WHERE DBMS_LOB.INSTR(sql_fulltext, ?) > 0 "
                + "  AND LOWER(TRIM(sql_text)) NOT LIKE 'select count(1)%' "
                + "  AND sql_text NOT LIKE '%v$sql%' "
                + "ORDER BY last_active_time DESC "
                + "FETCH FIRST 5 ROWS ONLY";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, marker);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new SqlCursorInfo(
                        rs.getString("sql_id"),
                        rs.getInt("child_number"),
                        rs.getLong("executions"),
                        rs.getString("last_active_time"),
                        rs.getLong("elapsed_time"),
                        rs.getLong("cpu_time"),
                        rs.getLong("buffer_gets"),
                        rs.getLong("disk_reads"),
                        rs.getLong("rows_processed"),
                        rs.getLong("fetches"),
                        rs.getLong("parse_calls"),
                        rs.getLong("loads"),
                        rs.getLong("invalidations"),
                        rs.getLong("plan_hash_value"));
            }
        }
    }
}
