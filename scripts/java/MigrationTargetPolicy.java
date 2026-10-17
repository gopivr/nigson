import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

/** Fail-closed target checks shared by local and one-shot ECS owner migration runs. */
final class MigrationTargetPolicy {
    private static final Set<String> ENVIRONMENTS = Set.of("local", "test", "stage", "prod");
    private static final Set<String> OWNERS = Set.of("commerce", "experience", "integration", "ai");
    private static final Pattern RDS_HOST = Pattern.compile("^(?:[a-z0-9-]+\\.)+[a-z0-9-]+\\.rds\\.amazonaws\\.com$");

    private MigrationTargetPolicy() {}

    static void validate(String environment, String owner, String release, String jdbcUrl, String expectedHost) {
        if (!ENVIRONMENTS.contains(environment)) throw new IllegalArgumentException("Unsupported migration environment");
        if (!OWNERS.contains(owner)) throw new IllegalArgumentException("Unsupported migration owner");
        if (!release.matches("BP[1-5]")) throw new IllegalArgumentException("Migration release must be BP1..BP5");
        if (environment.equals("local") || environment.equals("test")) {
            String expected = owner.equals("ai") ? "jdbc:postgresql://vector-db:5432/nigson_vector_test"
                    : "jdbc:postgresql://postgres:5432/nigson";
            boolean sharedAiTest = owner.equals("ai") && environment.equals("test")
                    && jdbcUrl.equals("jdbc:postgresql://postgres:5432/nigson");
            if (!jdbcUrl.equals(expected) && !sharedAiTest) {
                throw new IllegalArgumentException("Refusing nonlocal migration target");
            }
            return;
        }

        if (owner.equals("ai") && !release.equals("BP5")) throw new IllegalArgumentException("AI migrations are BP5-only");

        if (expectedHost == null || !RDS_HOST.matcher(expectedHost).matches()) {
            throw new IllegalArgumentException("Expected private RDS hostname is required");
        }
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Production migration requires PostgreSQL JDBC");
        }
        final URI target;
        try {
            target = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid production migration JDBC URL");
        }
        if (!"postgresql".equals(target.getScheme()) || target.getRawUserInfo() != null
                || target.getPort() != 5432 || !"/nigson".equals(target.getPath())
                || target.getRawQuery() == null || !target.getRawQuery().equals("sslmode=verify-full")
                || target.getFragment() != null || !expectedHost.equals(target.getHost())
                || !RDS_HOST.matcher(target.getHost()).matches()) {
            throw new IllegalArgumentException("Refusing migration target without exact RDS host binding and verified TLS");
        }
    }
}
