import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VersionUtil {
    private static final Pattern VERSION = Pattern.compile(".*(?:_|\\s)(v\\d+)\\.sql$", Pattern.CASE_INSENSITIVE);

    private VersionUtil() {
    }

    static String versionOf(Path queryFile) {
        Matcher matcher = VERSION.matcher(queryFile.getFileName().toString());
        return matcher.matches() ? matcher.group(1).toLowerCase() : "v0";
    }
}
