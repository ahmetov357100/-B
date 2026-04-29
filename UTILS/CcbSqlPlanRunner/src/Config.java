import java.util.Collections;
import java.util.Map;

final class Config {
    final String user;
    final String password;
    final String owner;
    final String jdbcUrl;
    final Map<String, String> bindValues;

    Config(String user, String password, String owner, String jdbcUrl, Map<String, String> bindValues) {
        this.user = user;
        this.password = password;
        this.owner = owner;
        this.jdbcUrl = jdbcUrl;
        this.bindValues = Collections.unmodifiableMap(bindValues);
    }
}
