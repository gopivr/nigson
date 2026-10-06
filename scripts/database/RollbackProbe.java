import java.nio.file.Path;
import java.sql.DriverManager;
import liquibase.Liquibase;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
public class RollbackProbe {
 public static void main(String[] args) throws Exception {
  String owner=System.getenv("MIGRATION_OWNER");
  try(var connection=DriverManager.getConnection("jdbc:postgresql://postgres:5432/nigson","nigson_"+owner+"_migrator",System.getenv("MIGRATION_PASSWORD"));var resources=new DirectoryResourceAccessor(Path.of("/app/source"))) {
   var database=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));database.setDefaultSchemaName(owner);database.setLiquibaseSchemaName(owner);
   try(var migration=new Liquibase(owner.equals("ai")?"database/changelog/ai/master.yaml":"services/"+owner+"/src/main/resources/db/changelog/master.yaml",resources,database)) {
    migration.setChangeLogParameter("schema.release","BP5");
    int count;try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT count(*) FROM "+owner+".databasechangelog WHERE id LIKE '%20261006%'") ){rows.next();count=rows.getInt(1);}
    migration.rollback(count,new Contexts(),new LabelExpression());
    migration.update(new Contexts(),new LabelExpression());
    migration.validate();
    System.out.println("PASS: actual Liquibase additive rollback and reapply: "+owner);
   }
  }
 }
}
