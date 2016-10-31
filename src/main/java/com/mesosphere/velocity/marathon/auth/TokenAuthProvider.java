package com.mesosphere.velocity.marathon.auth;

import com.auth0.jwt.JWTAlgorithmException;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.logging.Logger;

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

    public abstract String getToken() throws IOException, JWTAlgorithmException, InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException;

    public abstract void setPayload(HttpEntity entity);

    public abstract void setContentType(ContentType contentType);

    public abstract void setContentType(String contentType);

    public abstract void updateTokenCredentials(final Credentials tokenCredentials) throws AuthenticationException;

    protected StringCredentials updateTokenCredentials(final StringCredentials tokenCredentials, final String token) throws IOException {
        // retrieved a new token, now to update the existing credential in `tokenCredentials`
        final StringCredentials newTokenCredentials = new StringCredentialsImpl(
                tokenCredentials.getScope(),
                tokenCredentials.getId(),
                tokenCredentials.getDescription(),
                Secret.fromString(token));
        doTokenUpdate(tokenCredentials, newTokenCredentials);
        return newTokenCredentials;
    }

    private void doTokenUpdate(final StringCredentials tokenCredentials, final Credentials creds) throws IOException {
        final SystemCredentialsProvider.ProviderImpl systemProvider = ExtensionList.lookup(CredentialsProvider.class)
                .get(SystemCredentialsProvider.ProviderImpl.class);
        final CredentialsStore credentialsStore = systemProvider.getStore(Jenkins.getInstance());

        // there should only be one credential to update, but there are multiple domains
        // that need to be searched. wasFound will track whether the proper credential
        // was located, and if so break out of both loops.
        boolean wasFound = false;
        for (Domain d : credentialsStore.getDomains()) {
            List<Credentials> credentialsList = credentialsStore.getCredentials(d);
            for (Credentials c : credentialsList) {
                if (c instanceof StringCredentials) {
                    // cast
                    final StringCredentials stringCredentials = (StringCredentials) c;
                    if (tokenCredentials.getId().equals(stringCredentials.getId())) {
                        final boolean wasUpdated = credentialsStore.updateCredentials(d, c, creds);
                        if (!wasUpdated) {
                            LOGGER.warning("Updating Token credential failed during update call.");
                        }
                        // set this as found, even if update itself failed.
                        wasFound = true;
                        break;
                    }
                }
            }

            // if the target credential was found, no need to check more domains.
            if (wasFound) {
                break;
            }
        }

        if (!wasFound) {
            LOGGER.warning("Token credential was not found in the Credentials Store.");
        }
    }

    public enum Providers {
        DCOS;
    }
}
