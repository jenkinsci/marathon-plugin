package com.mesosphere.velocity.marathon.auth;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
import hudson.ExtensionList;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Provides an abstraction for authentication providers that return a token that can be used
 * when authenticating against the target Marathon instance.
 */
public abstract class TokenAuthProvider {
    private static final Logger LOGGER = Logger.getLogger(TokenAuthProvider.class.getName());

    public static TokenAuthProvider getTokenAuthProvider(final Providers provider, final Credentials credentials) {
        switch (provider) {
            case DCOS:
                return new DcosAuthImpl((StringCredentials) credentials);
            default:
                return null;
        }
    }

    /**
     * Get a new authentication token that will be used to deploy or update a Marathon application.
     *
     * @return token to be used in the Authorization HTTP header
     * @throws AuthenticationException If the token retrieval process encounters an error.
     */
    public abstract String getToken() throws AuthenticationException;

    public abstract void setPayload(HttpEntity entity);

    public abstract void setContentType(ContentType contentType);

    public abstract void setContentType(String contentType);

    public abstract boolean updateTokenCredentials(final Credentials tokenCredentials) throws AuthenticationException;

    /**
     * Helper method to update a credential stored within the Jenkins Credential Store. This creates a new credential
     * to replace tokenCredentials and then calls another helper to do the actual update.
     *
     * @param tokenCredentials The current, existing credential to update.
     * @param token            New token value for credential
     * @return The new credentials that were updated
     * @throws IOException Thrown if issues accessing the credential store.
     */
    StringCredentials updateTokenCredentials(final StringCredentials tokenCredentials, final String token) throws IOException {
        // retrieved a new token, now to update the existing credential in `tokenCredentials`
        final StringCredentials newTokenCredentials = new StringCredentialsImpl(
                tokenCredentials.getScope(),
                tokenCredentials.getId(),
                tokenCredentials.getDescription(),
                Secret.fromString(token));
        return doTokenUpdate(tokenCredentials, newTokenCredentials) ? newTokenCredentials : tokenCredentials;
    }

    /**
     * Helper method to update tokenCredentials with contents of creds.
     * <p>
     * This searches all domains for the id associated with tokenCredentials and updates the first credential it finds.
     *
     * @param tokenCredentials Existing credentials that should be updated.
     * @param creds            New credentials
     * @throws IOException If problems reading or writing to Jenkins Credential Store
     */
    private boolean doTokenUpdate(final StringCredentials tokenCredentials, final Credentials creds) throws IOException {
        final SystemCredentialsProvider.ProviderImpl systemProvider = ExtensionList.lookup(CredentialsProvider.class)
                .get(SystemCredentialsProvider.ProviderImpl.class);
        final CredentialsStore credentialsStore = systemProvider.getStore(Jenkins.getInstance());

        /*
            Walk through all domains and credentials for each domain to find a credential with the matching id.
         */
        for (Domain d : credentialsStore.getDomains()) {
            for (Credentials c : credentialsStore.getCredentials(d)) {
                if (!(c instanceof StringCredentials)) continue;

                final StringCredentials stringCredentials = (StringCredentials) c;
                if (tokenCredentials.getId().equals(stringCredentials.getId())) {
                    final boolean wasUpdated = credentialsStore.updateCredentials(d, c, creds);
                    if (!wasUpdated) {
                        LOGGER.warning("Updating Token credential failed during update call.");
                    }
                    return wasUpdated;
                }
            }
        }

        // if the credential was not found, then put a warning in the console log.
        LOGGER.warning("Token credential was not found in the Credentials Store.");
        return false;
    }

    public enum Providers {
        DCOS;
    }
}
