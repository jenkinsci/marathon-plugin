package com.mesosphere.velocity.marathon.auth;

import com.auth0.jwt.Algorithm;
import com.auth0.jwt.JWTAlgorithmException;
import com.auth0.jwt.JWTSigner;
import com.auth0.jwt.internal.org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.auth0.jwt.internal.org.bouncycastle.util.io.pem.PemReader;
import com.cloudbees.plugins.credentials.Credentials;
import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class DcosAuthImpl extends TokenAuthProvider {
    /**
     * The name of the cookie that contains the authentication token needed for future requests.
     */
    public final static    String DCOS_AUTH_COOKIE     = "dcos-acs-auth-cookie";
    /**
     * The JSON payload expected by the DC/OS login end point.
     */
    protected final static String DCOS_AUTH_PAYLOAD    = "{\"uid\":\"%s\",\"token\":\"%s\"}";
    private static final   Logger LOGGER               = Logger.getLogger(DcosAuthImpl.class.getName());
    /**
     * The JSON field that holds the user id required by DC/OS
     */
    private final static   String DCOS_AUTH_USER_FIELD = "uid";

    /**
     * The JSON field that holds the algorithm used to create "private_key".
     */
    private final static String DCOS_AUTH_SCHEME_FIELD = "scheme";

    /**
     * The JSON field that holds the private key.
     */
    private final static String DCOS_AUTH_PRIVATEKEY_FIELD = "private_key";

    /**
     * The JSON field that holds the DC/OS login end point.
     */
    private final static String DCOS_AUTH_LOGINENDPOINT_FIELD = "login_endpoint";

    // add the BouncyCastle provider if it does not already exist
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private JWTSigner.Options options;
    private ContentType       contentType;
    private HttpEntity        payload;
    private HttpClientBuilder client;
    private HttpClientContext context;
    private StringCredentials credentials;

    public DcosAuthImpl(final StringCredentials credentials) {
        this(
                credentials,
                new JWTSigner.Options(),
                ContentType.APPLICATION_JSON,
                HttpClientBuilder.create(),
                new HttpClientContext()
        );
    }

    DcosAuthImpl(final StringCredentials credentials,
                 final JWTSigner.Options options,
                 final ContentType contentType,
                 final HttpClientBuilder clientBuilder,
                 final HttpClientContext clientContext) {
        this.options = options;

        this.contentType = contentType;
        this.client = clientBuilder;
        this.context = clientContext;
        this.credentials = credentials;
    }

    private String getTokenCookie(final HttpClientContext context) {
        final CookieStore  cookieStore = context.getCookieStore();
        final List<Cookie> cookies     = cookieStore.getCookies();

        String cookieValue = null;

        for (final Cookie c : cookies) {
            if (c.getName().equals(DCOS_AUTH_COOKIE)) {
                cookieValue = c.getValue();
                break;
            }
        }

        return cookieValue;
    }

    @Override
    public String getToken() throws AuthenticationException {
        final DcosLoginPayload payload        = createDcosLoginPayload();
        final HttpEntity       stringPayload  = new StringEntity(payload.toString(), this.contentType);
        final RequestBuilder   requestBuilder = RequestBuilder.post().setUri(payload.getLoginURL());

        this.setPayload(stringPayload);
        if (this.payload != null) {
            requestBuilder.setEntity(this.payload);
        }

        final HttpUriRequest request = requestBuilder.build();
        try {
            client.build().execute(request, context).close();
        } catch (IOException e) {
            LOGGER.warning(e.getMessage());
            throw new AuthenticationException("Failed to execute web request to login endpoint.\n" + e.getMessage());
        }
        return getTokenCookie(context);
    }

    @Override
    public void setPayload(HttpEntity entity) {
        this.payload = entity;
    }

    @Override
    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    @Override
    public void setContentType(String contentType) {
        this.contentType = ContentType.create(contentType);
    }

    @Override
    public boolean updateTokenCredentials(final Credentials tokenCredentials) throws AuthenticationException {
        if (tokenCredentials instanceof StringCredentials) {
            final StringCredentials oldCredentials = (StringCredentials) tokenCredentials;

            if (credentials != null) {
                try {
                    final String token = getToken();
                    if (token == null) {
                        throw new AuthenticationException("Failed to retrieve authentication token from DC/OS.");
                    }
                    return !(updateTokenCredentials(oldCredentials, token).equals(oldCredentials));
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new AuthenticationException(e.getMessage());
                }
            }
        } else {
            LOGGER.warning("Invalid credential type, expected String Credentials, received: " + tokenCredentials.getClass().getName());
        }

        return false;
    }

    /**
     * Create a payload object for DC/OS. This contains the JSON payload for the web request and login endpoint (URL).
     *
     * @return DC/OS payload object
     * @throws AuthenticationException If error occurred during DC/OS authentication process
     */
    DcosLoginPayload createDcosLoginPayload() throws AuthenticationException {
        final JSONObject jsonObject    = constructJsonFromCredentials();
        final String     uid           = jsonObject.getString(DCOS_AUTH_USER_FIELD);
        final String     loginEndpoint = jsonObject.getString(DCOS_AUTH_LOGINENDPOINT_FIELD);
        final String     requestedAlg  = jsonObject.getString(DCOS_AUTH_SCHEME_FIELD);

        try {
            final Algorithm algorithm = Algorithm.findByName(requestedAlg);

            // try to set the algorithm to what was requested
            this.options.setAlgorithm(algorithm);
            this.options.setExpirySeconds(300); // 5 minutes expiration time
            this.options.setIssuedAt(true);

            final JWTSigner               signer = createSigner(jsonObject.getString(DCOS_AUTH_PRIVATEKEY_FIELD));
            final HashMap<String, Object> claims = createClaims(uid);
            final String                  jwt    = signer.sign(claims, this.options);
            return DcosLoginPayload.create(loginEndpoint, uid, jwt);
        } catch (JWTAlgorithmException e) {
            e.printStackTrace();
            throw new AuthenticationException("Unknown algorithm: " + e.getMessage());
        }
    }

    private JSONObject constructJsonFromCredentials() {
        final Secret secret = credentials.getSecret();
        return JSONObject.fromObject(Secret.toString(secret));
    }

    /**
     * Create the claims that will be signed by the JWT signer.
     *
     * @param userId the user id or Service Account name for this request
     * @return map of claims
     */
    private HashMap<String, Object> createClaims(final String userId) {
        final HashMap<String, Object> claims = new HashMap<String, Object>(1);
        claims.put("uid", userId);
        return claims;
    }

    /**
     * Create a JWT signer that will sign claims with key.
     *
     * @param key String representation of a private key
     * @return JWT signer
     * @throws AuthenticationException If an error occurs creating the signer
     */
    private JWTSigner createSigner(final String key) throws AuthenticationException {
        switch (this.options.getAlgorithm()) {
            case HS256:
            case HS384:
            case HS512:
                return new JWTSigner(key);
            case RS256:
            case RS384:
            case RS512:
                final PemReader pemParser = new PemReader(new StringReader(key));
                try {
                    final byte[]              content    = pemParser.readPemObject().getContent();
                    final PKCS8EncodedKeySpec keySpec    = new PKCS8EncodedKeySpec(content);
                    final KeyFactory          keyFactory = KeyFactory.getInstance("RSA", "BC");
                    return new JWTSigner(keyFactory.generatePrivate(keySpec));
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new AuthenticationException("Error encountered closing PEM reader:\n" + e.getMessage());
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                    throw new AuthenticationException("Unsupported algorithm: " + e.getMessage());
                } catch (NoSuchProviderException e) {
                    e.printStackTrace();
                    throw new AuthenticationException("Unknown provider: " + e.getMessage());
                } catch (InvalidKeySpecException e) {
                    e.printStackTrace();
                    throw new AuthenticationException("Unable to read key: " + e.getMessage());
                } finally {
                    try {
                        pemParser.close();
                    } catch (IOException e) {
                        LOGGER.warning(e.getMessage());
                    }
                }
            default:
                throw new AuthenticationException("Unsupported algorithm: " + this.options.getAlgorithm().getValue());
        }
    }
}
