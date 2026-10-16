import java.nio.file.Path;
import java.sql.DriverManager;
import liquibase.*;
import liquibase.database.*;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
/** Isolated owner migrator: only the empty additive cancellation changeset is removed/reapplied. */
public class CancellationIncrementProbe {
 public static void main(String[] args)throws Exception{String owner=args[0];if(!java.util.Set.of("commerce","integration").contains(owner))throw new IllegalArgumentException();try(var connection=DriverManager.getConnection("jdbc:postgresql://postgres:5432/nigson","nigson_"+owner+"_migrator",System.getenv("MIGRATION_PASSWORD"));var resources=new DirectoryResourceAccessor(Path.of("/app/source"))){var database=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));database.setDefaultSchemaName(owner);database.setLiquibaseSchemaName(owner);try(var migration=new Liquibase("services/"+owner+"/src/main/resources/db/changelog/master.yaml",resources,database)){migration.setChangeLogParameter("schema.release","BP1");migration.rollback(1,new Contexts(),new LabelExpression());try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT to_regclass('"+owner+".carrier_cancellation') IS NULL")){rows.next();if(!rows.getBoolean(1))throw new IllegalStateException("EMPTY_INCREMENT_ROLLBACK_FAILED");}migration.update(new Contexts(),new LabelExpression());migration.validate();System.out.println("PASS: empty owned cancellation increment rolled back/reapplied: "+owner);}}}
}
