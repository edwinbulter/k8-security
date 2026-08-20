package org.keycloak.quickstart.protocol.mapper;

import org.jboss.logging.Logger;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.ProtocolMapperUtils;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;
import org.keycloak.storage.StorageId;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

public class PermissionProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper {

    private static final Logger logger = Logger.getLogger(PermissionProtocolMapper.class);

    public static final String PROVIDER_ID = "permission-protocol-mapper";
    public static final String CLAIM_NAME = "permissions";

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties, PermissionProtocolMapper.class);

        ProviderConfigProperty multivalued = new ProviderConfigProperty();
        multivalued.setName(ProtocolMapperUtils.MULTIVALUED);
        multivalued.setLabel(ProtocolMapperUtils.MULTIVALUED_LABEL);
        multivalued.setHelpText(ProtocolMapperUtils.MULTIVALUED_HELP_TEXT);
        multivalued.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        multivalued.setDefaultValue("true");
        configProperties.add(multivalued);
    }

    @Override
    public String getDisplayCategory() {
        return "Token Mapper";
    }

    @Override
    public String getDisplayType() {
        return "Permission Mapper";
    }

    @Override
    public String getHelpText() {
        return "Maps permissions from external database roles into a JWT claim";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
                            UserSessionModel userSession, KeycloakSession keycloakSession,
                            ClientSessionContext clientSessionCtx) {
        String externalId = StorageId.externalId(userSession.getUser().getId());
        logger.info("PermissionProtocolMapper: setClaim for user " + userSession.getUser().getUsername() + ", external ID: " + externalId);

        EntityManager em = keycloakSession
                .getProvider(JpaConnectionProvider.class, "user-store")
                .getEntityManager();

        @SuppressWarnings("unchecked")
        List<String> permissions = em.createNativeQuery(
                "SELECT DISTINCT p.name FROM permissions p " +
                "JOIN role_permissions rp ON p.id = rp.permission_id " +
                "JOIN user_roles ur ON rp.role_id = ur.role_id " +
                "WHERE ur.user_id = :userId " +
                "ORDER BY p.name")
                .setParameter("userId", externalId)
                .getResultList();

        logger.info("Found permissions: " + permissions);

        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, permissions);
    }

}
