package org.keycloak.quickstart.protocol.mapper;

import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;

import java.util.List;

public class PermissionProtocolMapperFactory implements ProtocolMapper {

    @Override
    public String getId() {
        return PermissionProtocolMapper.PROVIDER_ID;
    }

    @Override
    public PermissionProtocolMapper create() {
        return new PermissionProtocolMapper();
    }

    @Override
    public void init() {
    }

    @Override
    public void postInit() {
    }

    @Override
    public void close() {
    }

    @Override
    public List<String> getCompliantProtocols() {
        return List.of(OIDCLoginProtocol.LOGIN_PROTOCOL);
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
}
