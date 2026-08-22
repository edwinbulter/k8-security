import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.ICFObjectBuilder
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos

import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.MULTIVALUED

def log = log as Log
def operation = operation as OperationType
def builder = builder as ICFObjectBuilder
def configuration = configuration as ScriptedSQLConfiguration

log.info("Entering " + operation + " Script")

// Single ACCOUNT object class backed by the "users" table.
// Role membership (many-to-many via "user_roles") is exposed as a
// plain multivalued "roleIds" attribute - not a formal ConnId
// association - which keeps the resource/schema simple while still
// allowing Create/Update/Delete scripts to fully manage the join table.
builder.schema({
    objectClass {
        type ObjectClass.ACCOUNT_NAME
        attributes {
            username()
            email()
            phone()
            roleIds String.class, MULTIVALUED
            OperationalAttributeInfos.PASSWORD
        }
    }
})
