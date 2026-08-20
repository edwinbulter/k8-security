package org.keycloak.quickstart.storage.user;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.storage.UserStorageProviderFactory;

public class MyExampleUserStorageProviderFactory implements UserStorageProviderFactory<MyUserStorageProvider> {

    public static final String PROVIDER_ID = "example-user-permissions-jpa";

    private static final Logger logger = Logger.getLogger(MyExampleUserStorageProviderFactory.class);

    @Override
    public MyUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        return new MyUserStorageProvider(session, model);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "JPA User Storage Provider (PostgreSQL)";
    }

    @Override
    public void close() {
        logger.info("<<<<<< Closing factory");
    }
}
