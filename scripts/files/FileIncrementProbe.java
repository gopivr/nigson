import java.nio.file.Path;
import java.sql.DriverManager;
import liquibase.*;
import liquibase.database.*;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
/** Isolated migrator-only probe: rollback/reapply only the new empty file increment. */
public class FileIncrementProbe {
 public static void main(String[] args)throws Exception {
  try(var connection=DriverManager.getConnection("jdbc:postgresql://postgres:5432/nigson","nigson_commerce_migrator",System.getenv("MIGRATION_PASSWORD"));var resources=new DirectoryResourceAccessor(Path.of("/app/source"))){
   var database=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));database.setDefaultSchemaName("commerce");database.setLiquibaseSchemaName("commerce");
   try(var migration=new Liquibase("services/commerce/src/main/resources/db/changelog/master.yaml",resources,database)){
    migration.setChangeLogParameter("schema.release","BP1");migration.rollback(1,new Contexts(),new LabelExpression());
    try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT to_regclass('commerce.file_transfer') IS NULL AND EXISTS(SELECT 1 FROM commerce.product WHERE created_by='synthetic-upgrade-marker')")){rows.next();if(!rows.getBoolean(1))throw new IllegalStateException("EMPTY_INCREMENT_ROLLBACK_FAILED");}
    migration.update(new Contexts(),new LabelExpression());migration.validate();System.out.println("PASS: only new file increment rolled back/reapplied; prior BP1 product preserved");
   }
  }
 }
}
