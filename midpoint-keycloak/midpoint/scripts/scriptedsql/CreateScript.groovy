import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.*

import java.sql.Connection
import java.sql.SQLException

def log = log as Log
def operation = operation as OperationType
def options = options as OperationOptions
def objectClass = objectClass as ObjectClass
def attributes = attributes as Set<Attribute>
def connection = connection as Connection
def id = id as String
def configuration = configuration as ScriptedSQLConfiguration

log.info("Entering " + operation + " Script for " + objectClass)

if (objectClass != ObjectClass.ACCOUNT) {
    throw new ConnectorException("Unsupported object class " + objectClass)
}

def sql = new Sql(connection)

String getSingle(String name) {
    Attribute attr = AttributeUtil.find(name, attributes)
    return attr == null ? null : AttributeUtil.getStringValue(attr)
}

String getPassword() {
    Attribute attr = AttributeUtil.find(OperationalAttributes.PASSWORD_NAME, attributes)
    if (attr == null) {
        return null
    }
    GuardedString gs = AttributeUtil.getGuardedStringValue(attr)
    if (gs == null) {
        return null
    }
    String[] result = [null]
    gs.access({ chars -> result[0] = new String(chars) } as GuardedString.Accessor)
    return result[0]
}

List<String> getRoleIds() {
    Attribute attr = AttributeUtil.find("roleIds", attributes)
    if (attr == null || attr.getValue() == null) {
        return []
    }
    return attr.getValue().collect { it as String }
}

String username = getSingle("username")
String email = getSingle("email")
String phone = getSingle("phone")
String password = getPassword()
List<String> roleIds = getRoleIds()

sql.withTransaction {
    try {
        sql.executeInsert("INSERT INTO users (id, username, email, password, phone) VALUES (?, ?, ?, ?, ?)",
                [id, username, email, password, phone])
    } catch (SQLException ex) {
        throw new AlreadyExistsException("Account with id " + id + " already exists", ex)
    }

    roleIds.each { roleId ->
        sql.executeInsert("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", [id, roleId])
    }
}

return new Uid(id)
