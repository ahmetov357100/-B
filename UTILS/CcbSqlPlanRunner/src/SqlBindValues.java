import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SqlBindValues {
    private static final Pattern LEADING_BLOCK_COMMENT = Pattern.compile("^\\s*/\\*(.*?)\\*/", Pattern.DOTALL);
    private static final Pattern TYPE_VALUE = Pattern.compile(
            "\"type\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"value\"\\s*:\\s*(null|\"((?:\\\\.|[^\"])*)\")",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private SqlBindValues() {
    }

    static Map<String, String> fromLeadingJsonComment(String sql) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher block = LEADING_BLOCK_COMMENT.matcher(sql);
        if (!block.find()) {
            return values;
        }

        Matcher pair = TYPE_VALUE.matcher(block.group(1));
        while (pair.find()) {
            String name = pair.group(1).trim().toUpperCase();
            String rawValue = pair.group(2);
            String value = "null".equalsIgnoreCase(rawValue) ? null : unescape(pair.group(3));
            values.put(name, value);
        }
        return values;
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case '"':
                    case '\\':
                    case '/':
                        out.append(next);
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    default:
                        out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
