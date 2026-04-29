import java.util.List;

final class ParsedSql {
    final String sql;
    final List<String> bindNames;

    ParsedSql(String sql, List<String> bindNames) {
        this.sql = sql;
        this.bindNames = bindNames;
    }
}
