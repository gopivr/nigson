/** Dependency-free regression checks for migration destination safeguards. */
public final class MigrationTargetPolicyTest {
    private static final String HOST = "nigson-stage-commerce.abc123.eu-west-1.rds.amazonaws.com";

    public static void main(String[] args) {
        acceptsLocalTargets();
        acceptsBoundRdsTarget();
        rejectsUnsafeTargets();
        System.out.println("Migration target policy tests PASS");
    }

    private static void acceptsLocalTargets() {
        MigrationTargetPolicy.validate("local", "commerce", "BP1", "jdbc:postgresql://postgres:5432/nigson", null);
        MigrationTargetPolicy.validate("test", "ai", "BP5", "jdbc:postgresql://postgres:5432/nigson", null);
        MigrationTargetPolicy.validate("test", "ai", "BP1", "jdbc:postgresql://postgres:5432/nigson", null);
    }

    private static void acceptsBoundRdsTarget() {
        MigrationTargetPolicy.validate("stage", "commerce", "BP1",
                "jdbc:postgresql://" + HOST + ":5432/nigson?sslmode=verify-full", HOST);
    }

    private static void rejectsUnsafeTargets() {
        rejects("stage", "commerce", "BP1", "jdbc:postgresql://localhost:5432/nigson?sslmode=verify-full", HOST);
        rejects("stage", "commerce", "BP1", "jdbc:postgresql://" + HOST + ":5432/nigson?sslmode=require", HOST);
        rejects("stage", "commerce", "BP1", "jdbc:postgresql://user:password@" + HOST + ":5432/nigson?sslmode=verify-full", HOST);
        rejects("prod", "commerce", "BP1", "jdbc:postgresql://other.abc123.eu-west-1.rds.amazonaws.com:5432/nigson?sslmode=verify-full", HOST);
        rejects("prod", "commerce", "BP1", "jdbc:postgresql://" + HOST + ":5432/other?sslmode=verify-full", HOST);
        rejects("prod", "ai", "BP4", "jdbc:postgresql://" + HOST + ":5432/nigson?sslmode=verify-full", HOST);
        rejects("prod", "commerce", "BP6", "jdbc:postgresql://" + HOST + ":5432/nigson?sslmode=verify-full", HOST);
        rejects("prod", "commerce", "BP1", "jdbc:postgresql://" + HOST + ":5432/nigson?sslmode=verify-full", "localhost");
    }

    private static void rejects(String environment, String owner, String release, String jdbcUrl, String expectedHost) {
        try {
            MigrationTargetPolicy.validate(environment, owner, release, jdbcUrl, expectedHost);
            throw new AssertionError("Unsafe migration target was accepted");
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed policy rejection.
        }
    }
}
