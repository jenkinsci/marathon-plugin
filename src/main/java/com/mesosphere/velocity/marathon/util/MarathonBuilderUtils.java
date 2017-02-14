package com.mesosphere.velocity.marathon.util;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.util.Collections;

public class MarathonBuilderUtils {
    /**
     * Default Marathon Application Definition file.
     */
    public static final String MARATHON_JSON          = "marathon.json";
    /**
     * Default filepath for the rendered JSON that was sent to the Marathon instance.
     */
    public static final String MARATHON_RENDERED_JSON = "marathon-rendered-${BUILD_NUMBER}.json";

    /**
     * Remove the trailing slash from url.
     *
     * @param url the URL
     * @return URL with the trailing slash removed, if it exists.
     */
    public static String rmSlashFromUrl(final String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Get the token from the credentials identified by the given id.
     *
     * @param credentialsId The id for the credentials
     * @return Jenkins credentials
     */
    public static StringCredentials getTokenCredentials(final String credentialsId) {
        return getJenkinsCredentials(credentialsId, StringCredentials.class);
    }

    /**
     * Get the user/pass from the credentials identified by the given id.
     *
     * @param credentialsId The id for the credentials
     * @return Jenkins credentials
     */
    public static UsernamePasswordCredentials getUsernamePasswordCredentials(final String credentialsId) {
        return getJenkinsCredentials(credentialsId, UsernamePasswordCredentials.class);
    }

    /**
     * Get the credentials identified by the given id from the Jenkins credential store.
     *
     * @param credentialsId    The id for the credentials
     * @param credentialsClass The class of credentials to return
     * @return Jenkins credentials
     */
    public static <T extends Credentials> T getJenkinsCredentials(final String credentialsId, final Class<T> credentialsClass) {
        if (StringUtils.isEmpty(credentialsId))
            return null;
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(credentialsClass,
                        Jenkins.getInstance(), ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId)
        );
    }

}
