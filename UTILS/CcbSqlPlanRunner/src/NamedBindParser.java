import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class NamedBindParser {
    private NamedBindParser() {
    }

    static ParsedSql parse(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        List<String> bindNames = new ArrayList<>();
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (lineComment) {
                out.append(c);
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                out.append(c);
                if (c == '*' && next == '/') {
                    out.append(next);
                    i++;
                    blockComment = false;
                }
                continue;
            }
            if (singleQuote) {
                out.append(c);
                if (c == '\'' && next == '\'') {
                    out.append(next);
                    i++;
                } else if (c == '\'') {
                    singleQuote = false;
                }
                continue;
            }
            if (doubleQuote) {
                out.append(c);
                if (c == '"') {
                    doubleQuote = false;
                }
                continue;
            }

            if (c == '-' && next == '-') {
                out.append(c).append(next);
                i++;
                lineComment = true;
            } else if (c == '/' && next == '*') {
                out.append(c).append(next);
                i++;
                blockComment = true;
            } else if (c == '\'') {
                out.append(c);
                singleQuote = true;
            } else if (c == '"') {
                out.append(c);
                doubleQuote = true;
            } else if (c == ':' && isBindStart(next)) {
                int start = i + 1;
                int end = start + 1;
                while (end < sql.length() && isBindPart(sql.charAt(end))) {
                    end++;
                }
                bindNames.add(sql.substring(start, end).toUpperCase());
                out.append('?');
                i = end - 1;
            } else {
                out.append(c);
            }
        }
        return new ParsedSql(out.toString(), bindNames);
    }

    static void bind(java.sql.PreparedStatement statement, ParsedSql sql, Map<String, String> values)
            throws java.sql.SQLException {
        for (int i = 0; i < sql.bindNames.size(); i++) {
            String name = sql.bindNames.get(i);
            String value = values.get(name);
            if (value == null || "null".equalsIgnoreCase(value)) {
                statement.setNull(i + 1, java.sql.Types.VARCHAR);
            } else {
                statement.setString(i + 1, value);
            }
        }
    }

    private static boolean isBindStart(char c) {
        return c == '_' || Character.isLetter(c);
    }

    private static boolean isBindPart(char c) {
        return c == '_' || c == '$' || c == '#' || Character.isLetterOrDigit(c);
    }
}
