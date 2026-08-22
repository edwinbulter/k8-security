import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.ResultsHandler
import org.identityconnectors.framework.common.objects.SearchResult
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.objects.Name
import org.identityconnectors.framework.common.objects.filter.Filter

import java.sql.Connection

def log = log as Log
def operation = operation as OperationType
def options = options as OperationOptions
def objectClass = objectClass as ObjectClass
def configuration = configuration as ScriptedSQLConfiguration
def filter = filter as Filter
def connection = connection as Connection
def handler = handler as ResultsHandler

log.info("Entering " + operation + " Script for " + objectClass)

if (objectClass != ObjectClass.ACCOUNT) {
    throw new ConnectorException("Unsupported object class " + objectClass)
}

def sql = new Sql(connection)

// Filtering (by uid/name) is delegated to midPoint's own
// filteredResultsHandler (see enableFilteredResultsHandler=true in the
// resource connectorConfiguration), so this script always returns the
// full result set and lets the framework narrow it down.
sql.eachRow("SELECT id, username, email, phone FROM users") { row ->
    List<String> roleIds = []
    sql.eachRow("SELECT role_id FROM user_roles WHERE user_id = ?", [row.id]) { r ->
        roleIds.add(r.role_id as String)
    }

    ConnectorObjectBuilder builder = new ConnectorObjectBuilder()
    builder.setObjectClass(ObjectClass.ACCOUNT)
    builder.setUid(new Uid(row.id as String))
    builder.setName(new Name(row.id as String))
    builder.addAttribute('username', row.username)
    builder.addAttribute('email', row.email)
    builder.addAttribute('phone', row.phone)
    builder.addAttribute('roleIds', roleIds)

    handler.handle(builder.build())
}

return new SearchResult()
