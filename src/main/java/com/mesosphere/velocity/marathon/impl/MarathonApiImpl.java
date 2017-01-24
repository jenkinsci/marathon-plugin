package com.mesosphere.velocity.marathon.impl;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mesosphere.velocity.marathon.interfaces.MarathonApi;
import com.mesosphere.velocity.marathon.util.MarathonApiConstant;
import hudson.remoting.Base64;
import mesosphere.marathon.client.utils.MarathonException;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.logging.Logger;

/**
 * Marathon API Implementation
 *
 * @author luketornquist
 * @since 1/21/17
 */
public class MarathonApiImpl implements MarathonApi {

    private static final Logger LOGGER = Logger.getLogger(MarathonApiImpl.class.getName());
    private static final String UPDATE_APP_TEMPLATE = "%s/v2/apps/%s";
    private static final String JENKINS_TOKEN_KEY = "jenkins_token";
    private final String baseUrl;
    private final ContentType contentType;
    private final HttpClientBuilder client;
    private final HttpClientContext context;
    private Header authorizationHeader;

    private MarathonApiImpl(String baseUrl) {
        this(baseUrl, ContentType.APPLICATION_JSON, HttpClientBuilder.create(), new HttpClientContext());
    }

    MarathonApiImpl(String baseUrl, Credentials credentials) {
        this(baseUrl);
        setAuthorizationHeader(credentials);
    }

    private MarathonApiImpl(String baseUrl, ContentType contentType, HttpClientBuilder client, HttpClientContext context) {
        this.baseUrl = baseUrl;
        this.contentType = contentType;
        this.client = client;
        this.context = context;
    }

    @Override
    public String update(String appId, String jsonPayload, boolean forceUpdate) throws MarathonException {
        final HttpEntity stringPayload = new StringEntity(jsonPayload, this.contentType);
        String url;
        try {
            url = String.format(UPDATE_APP_TEMPLATE, this.baseUrl, URLEncoder.encode(appId, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // Fall back to no encoding
            LOGGER.warning("UTF-8 unsupported encoding for URL Encoding, fallback to no URL Encoding");
            url = String.format(UPDATE_APP_TEMPLATE, this.baseUrl, appId);
        }

        // build request
        final HttpUriRequest request = RequestBuilder
                .put(url)
                .addHeader(this.authorizationHeader)
                .addParameter(MarathonApiConstant.FORCE_UPDATE_QUERY_PARAM, Boolean.toString(forceUpdate))
                .setEntity(stringPayload)
                .build();

        CloseableHttpResponse httpResponse = null;
        try {
            httpResponse = client.build().execute(request, context);
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode >= 400 && statusCode < 600) {
                final String message = String.format("%s%n%s", httpResponse.getStatusLine().getReasonPhrase(),
                                                     prettyPrintJson(EntityUtils.toString(httpResponse.getEntity())));
                throw new MarathonException(statusCode, message);
            }
        } catch (IOException e) {
            final String errorMessage = "Failed to execute web request to login endpoint.\n" + e.getMessage();
            LOGGER.warning(errorMessage);
            throw new MarathonException(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorMessage);
        }
        finally {
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                }
                catch (IOException e) {
                }
            }
        }
        return null;
    }

    private void setAuthorizationHeader(final Credentials credentials) {
        String authorization = null;
        if (credentials instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials usernamePasswordCredentials = (UsernamePasswordCredentials) credentials;
            String username = usernamePasswordCredentials.getUsername();
            String password = usernamePasswordCredentials.getPassword().getPlainText();
            authorization = "Basic " + Base64.encode((username + ":" + password).getBytes(Charset.defaultCharset()));
        } else if (credentials instanceof StringCredentials) {
            Optional<String> token = getToken((StringCredentials) credentials);
            if (token.isPresent()) {
                authorization = "token=" + token.get();
            }
        }
        if (authorization != null) {
            this.authorizationHeader = new BasicHeader(MarathonApiConstant.AUTHORIZATION_HEADER, authorization);
        }
    }


    /**
     * Get the token within provided credentials. If the content of
     * credentials is JSON, this will use the "jenkins_token" field;
     * if the content is just a string, that will be
     * used as the token value.
     *
     * @param credentials String credentials
     * @return Token for Service Account Authentication
     */
    private Optional<String> getToken(StringCredentials credentials) {
        String token = null;
        try {
            final JSONObject json = JSONObject.fromObject(credentials.getSecret().getPlainText());
            if (json.has(JENKINS_TOKEN_KEY)) {
                token = json.getString(JENKINS_TOKEN_KEY);
            }
        } catch (JSONException jse) {
            token = credentials.getSecret().getPlainText();
        }
        if (StringUtils.isNotEmpty(token)) {
            return Optional.of(token);
        }
        return Optional.absent();
    }

    private String prettyPrintJson(String jsonString) {
        try {
            JsonParser parser = new JsonParser();
            JsonObject json = parser.parse(jsonString).getAsJsonObject();

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(json);
        } catch (JsonSyntaxException jse) {
            LOGGER.warning("JSON Syntax issue while trying to pretty print JSON. Defaulting to standard JSON format.\n" + jse.getMessage());
            return jsonString;
        }
    }
}
