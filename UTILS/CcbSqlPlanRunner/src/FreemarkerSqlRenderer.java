import java.util.Map;

final class FreemarkerSqlRenderer {
    private FreemarkerSqlRenderer() {
    }

    static String render(String template, Map<String, String> parameters) {
        return renderRange(template, 0, template.length(), parameters);
    }

    static boolean hasFreemarker(String sql) {
        return sql.contains("<#if") || sql.contains("</#if>");
    }

    private static String renderRange(String text, int start, int end, Map<String, String> parameters) {
        StringBuilder out = new StringBuilder(end - start);
        int position = start;
        while (position < end) {
            int ifStart = text.indexOf("<#if", position);
            if (ifStart < 0 || ifStart >= end) {
                out.append(text, position, end);
                break;
            }
            out.append(text, position, ifStart);

            int tagEnd = text.indexOf('>', ifStart);
            if (tagEnd < 0 || tagEnd >= end) {
                throw new IllegalArgumentException("Unclosed FreeMarker if tag near offset " + ifStart);
            }

            String condition = text.substring(ifStart + 4, tagEnd).trim();
            int ifEnd = matchingEndIf(text, tagEnd + 1, end);
            if (ifEnd < 0) {
                throw new IllegalArgumentException("Missing </#if> for FreeMarker if near offset " + ifStart);
            }

            if (eval(condition, parameters)) {
                out.append(renderRange(text, tagEnd + 1, ifEnd, parameters));
            }
            position = ifEnd + "</#if>".length();
        }
        return out.toString();
    }

    private static int matchingEndIf(String text, int start, int end) {
        int depth = 1;
        int position = start;
        while (position < end) {
            int nextIf = text.indexOf("<#if", position);
            int nextEnd = text.indexOf("</#if>", position);
            if (nextEnd < 0 || nextEnd >= end) {
                return -1;
            }
            if (nextIf >= 0 && nextIf < nextEnd && nextIf < end) {
                depth++;
                position = nextIf + 4;
            } else {
                depth--;
                if (depth == 0) {
                    return nextEnd;
                }
                position = nextEnd + "</#if>".length();
            }
        }
        return -1;
    }

    private static boolean eval(String expression, Map<String, String> parameters) {
        String[] orParts = expression.split("\\s*\\|\\|\\s*");
        for (String orPart : orParts) {
            if (evalAnd(orPart, parameters)) {
                return true;
            }
        }
        return false;
    }

    private static boolean evalAnd(String expression, Map<String, String> parameters) {
        String[] andParts = expression.split("\\s*&&\\s*");
        for (String andPart : andParts) {
            if (!evalAtom(andPart.trim(), parameters)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evalAtom(String atom, Map<String, String> parameters) {
        if (atom.startsWith("(") && atom.endsWith(")")) {
            return eval(atom.substring(1, atom.length() - 1).trim(), parameters);
        }
        if (atom.startsWith("!")) {
            return !evalAtom(atom.substring(1).trim(), parameters);
        }

        String notNullSuffix = ".notNull()";
        if (atom.startsWith("parameters.") && atom.endsWith(notNullSuffix)) {
            String name = atom.substring("parameters.".length(), atom.length() - notNullSuffix.length());
            return isNotNull(parameters.get(name.toUpperCase()));
        }

        String valuePrefix = "parameters.";
        String valueMarker = ".value";
        int valueIndex = atom.indexOf(valueMarker);
        if (atom.startsWith(valuePrefix) && valueIndex > valuePrefix.length()) {
            String name = atom.substring(valuePrefix.length(), valueIndex).toUpperCase();
            String rest = atom.substring(valueIndex + valueMarker.length()).trim();
            String operator;
            if (rest.startsWith("==")) {
                operator = "==";
            } else if (rest.startsWith("!=")) {
                operator = "!=";
            } else {
                throw new IllegalArgumentException("Unsupported FreeMarker condition atom: " + atom);
            }
            String expected = stripQuoted(rest.substring(operator.length()).trim());
            String actual = parameters.get(name);
            boolean equals = expected.equals(actual);
            return "==".equals(operator) ? equals : !equals;
        }

        throw new IllegalArgumentException("Unsupported FreeMarker condition atom: " + atom);
    }

    private static boolean isNotNull(String value) {
        return value != null && !"null".equalsIgnoreCase(value) && !value.isBlank();
    }

    private static String stripQuoted(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
