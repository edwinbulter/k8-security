import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.*

import java.sql.Connection

def log = log as Log
def operation = operation as OperationType
def options = options as OperationOptions
def objectClass = objectClass as ObjectClass
def attributes = attributes as Set<Attribute>
def uid = uid as Uid
def configuration = configuration as ScriptedSQLConfiguration
def connection = connection as Connection

log.info("Entering " + operation + " Script for " + objectClass)

if (objectClass != ObjectClass.ACCOUNT) {
    throw new ConnectorException("Unsupported object class " + objectClass)
}

def sql = new Sql(connection)
String id = uid.getUidValue()

Map<String, Object> columns = [:]
List<String> roleIds = null

for (Attribute attribute : attributes) {
    switch (attribute.getName()) {
        case Uid.NAME:
        case Name.NAME:
            break
        case OperationalAttributes.PASSWORD_NAME:
            GuardedString gs = AttributeUtil.getGuardedStringValue(attribute)
            if (gs != null) {
                String[] result = [null]
                gs.access({ chars -> result[0] = new String(chars) } as GuardedString.Accessor)
                columns.put("password", result[0])
            }
            break
        case "roleIds":
            roleIds = attribute.getValue() == null ? [] : attribute.getValue().collect { it as String }
            break
        default:
            columns.put(attribute.getName(), AttributeUtil.getStringValue(attribute))
            break
    }
}

sql.withTransaction {
    if (!columns.isEmpty()) {
        String setClause = columns.keySet().collect { it + " = ?" }.join(", ")
        List params = new ArrayList(columns.values())
        params.add(id)
        sql.executeUpdate("UPDATE users SET " + setClause + " WHERE id = ?", params)
    }

    if (roleIds != null) {
        sql.executeUpdate("DELETE FROM user_roles WHERE user_id = ?", [id])
        roleIds.each { roleId ->
            sql.executeInsert("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", [id, roleId])
        }
    }
}

return uid
