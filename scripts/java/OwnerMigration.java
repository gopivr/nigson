import java.io.StringWriter;
import java.nio.file.Path;
import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;

/** One-shot owner migration tool; never used by an application runtime identity. */
public class OwnerMigration {
    public static void main(String[] args) throws Exception {
        String environment = required("APP_ENV");
        String release = required("MIGRATION_RELEASE");
        String owner = required("MIGRATION_OWNER");
        String url = required("MIGRATION_JDBC_URL");
        String expectedHost = environment.equals("stage") || environment.equals("prod")
                ? required("MIGRATION_EXPECTED_HOST") : null;
        MigrationTargetPolicy.validate(environment, owner, release, url, expectedHost);
        String username = "nigson_" + owner + "_migrator";
        String password = required("MIGRATION_PASSWORD");
        String root = required("MIGRATION_ROOT");
        String changelog = owner.equals("ai") ? "database/changelog/ai/master.yaml"
                : "services/" + owner + "/src/main/resources/db/changelog/master.yaml";
        try (var connection = DriverManager.getConnection(url, username, password);
             var resources = new DirectoryResourceAccessor(Path.of(root))) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(owner);
            database.setLiquibaseSchemaName(owner);
            try (var migration = new Liquibase(changelog, resources, database)) {
                migration.setChangeLogParameter("schema.release", release);
                // Refuse a lower release than already deployed. Changelog is owner-local.
                try (var statement = connection.prepareStatement("SELECT to_regclass(?::text)")) {
                    statement.setString(1, owner + ".databasechangelog");
                    try (var result = statement.executeQuery()) {
                        result.next();
                        if (result.getString(1) != null) {
                            try (var version = connection.prepareStatement("SELECT count(*) FROM " + owner
                                    + ".databasechangelog WHERE filename ~ ?")) {
                                version.setString(1, "-bp[" + (Integer.parseInt(release.substring(2)) + 1) + "-9]-");
                                try (var deployed = version.executeQuery()) {
                                    deployed.next();
                                    if (deployed.getInt(1) != 0) throw new IllegalStateException("Schema release downgrade refused");
                                }
                            }
                        }
                    }
                }
                migration.validate();
                var contexts = new Contexts();
                var labels = new LabelExpression();
                var pending = migration.listUnrunChangeSets(contexts, labels);
                // Generate SQL before applying without emitting SQL or credentials into logs.
                migration.update(contexts, labels, new StringWriter());
                migration.update(contexts, labels);
                if (!migration.listUnrunChangeSets(contexts, labels).isEmpty()) {
                    throw new IllegalStateException("Owner migrations still pending");
                }
                verifyBootstrap(connection, owner);
                System.out.println("Owner migration verified: " + owner + "; applied=" + pending.size());
            }
        }
    }

    private static void verifyBootstrap(java.sql.Connection connection, String owner) throws Exception {
        // Owner-local checks also detect manual privilege/schema drift after changesets were applied.
        try (var statement = connection.prepareStatement("""
                SELECT pg_get_userbyid(nspowner) = current_user
                    AND has_schema_privilege(?, ?, 'USAGE')
                    AND NOT has_schema_privilege(?, ?, 'CREATE')
                    AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname IN (?, current_user)
                        AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls))
                FROM pg_namespace WHERE nspname = ?
                """)) {
            String app = "nigson_" + owner + "_app";
            statement.setString(1, app);
            statement.setString(2, owner);
            statement.setString(3, app);
            statement.setString(4, owner);
            statement.setString(5, app);
            statement.setString(6, owner);
            try (var result = statement.executeQuery()) {
                if (!result.next() || !result.getBoolean(1)) {
                    throw new IllegalStateException("Owner bootstrap privilege/schema drift: " + owner);
                }
            }
        }
        if (owner.equals("commerce")) {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("""
                    SELECT to_regclass('commerce.spring_session') IS NOT NULL
                        AND to_regclass('commerce.spring_session_attributes') IS NOT NULL
                        AND (SELECT count(*) = 4 FROM pg_constraint
                            WHERE connamespace = 'commerce'::regnamespace AND convalidated
                            AND conname IN ('spring_session_primary_id_pk', 'spring_session_interval_ck',
                                'spring_session_attributes_pk', 'spring_session_attributes_session_fk'))
                        AND EXISTS (SELECT 1 FROM pg_constraint WHERE connamespace = 'commerce'::regnamespace
                            AND conname = 'spring_session_attributes_session_fk' AND confdeltype = 'c'
                            AND confrelid = 'commerce.spring_session'::regclass)
                        AND EXISTS (SELECT 1 FROM pg_constraint WHERE connamespace = 'commerce'::regnamespace
                            AND conname = 'spring_session_interval_ck'
                            AND pg_get_constraintdef(oid) = 'CHECK ((max_inactive_interval > 0))')
                        AND to_regclass('commerce.spring_session_session_id_uq') IS NOT NULL
                        AND to_regclass('commerce.spring_session_expiry_time_ix') IS NOT NULL
                        AND to_regclass('commerce.spring_session_principal_name_ix') IS NOT NULL
                        AND NOT EXISTS (SELECT 1 FROM unnest(ARRAY['SELECT','INSERT','UPDATE','DELETE']) AS p(privilege)
                            WHERE NOT has_table_privilege('nigson_commerce_app', 'commerce.spring_session', privilege)
                               OR NOT has_table_privilege('nigson_commerce_app', 'commerce.spring_session_attributes', privilege))
                    """)) {
                if (!result.next() || !result.getBoolean(1)) {
                    throw new IllegalStateException("Framework session schema drift");
                }
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required migration setting: " + name);
        }
        return value;
    }
}
