import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class PlanFetcher {
    private PlanFetcher() {
    }

    static String fetch(Connection connection, SqlCursorInfo cursor) throws SQLException {
        if (cursor == null) {
            return "";
        }
        String sql = ""
                + "SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR("
                + "sql_id => ?, "
                + "cursor_child_no => ?, "
                + "format => 'ALLSTATS LAST +PREDICATE +PEEKED_BINDS +OUTLINE +ALIAS +PROJECTION +NOTE'))";

        StringBuilder plan = new StringBuilder();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, cursor.sqlId);
            ps.setInt(2, cursor.childNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    plan.append(rs.getString(1)).append(System.lineSeparator());
                }
            }
        }
        return plan.toString();
    }
}
