import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.Uid

import java.sql.Connection

def log = log as Log
def operation = operation as OperationType
def options = options as OperationOptions
def objectClass = objectClass as ObjectClass
def uid = uid as Uid
def configuration = configuration as ScriptedSQLConfiguration
def connection = connection as Connection

log.info("Entering " + operation + " Script for " + objectClass)

if (objectClass != ObjectClass.ACCOUNT) {
    throw new ConnectorException("Unsupported object class " + objectClass)
}

def sql = new Sql(connection)
String id = uid.getUidValue()

// Remove the join-table rows first to avoid violating the
// user_roles_user_id_fkey foreign key constraint.
sql.withTransaction {
    sql.executeUpdate("DELETE FROM user_roles WHERE user_id = ?", [id])
    sql.executeUpdate("DELETE FROM users WHERE id = ?", [id])
}
