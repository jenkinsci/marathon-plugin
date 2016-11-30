package com.mesosphere.velocity.marathon.auth;

import com.auth0.jwt.JWTSigner;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.internal.org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.auth0.jwt.internal.org.bouncycastle.util.io.pem.PemReader;
import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;


@RunWith(PowerMockRunner.class)
@PrepareForTest({Secret.class})
@PowerMockIgnore("javax.crypto.*")
public class DcosAuthImplTest {

    private static final String DCOS_AUTH_JSON       = "{\"uid\":\"%s\",\"login_endpoint\":\"/login\",\"private_key\":\"%s\",\"scheme\":\"%s\"}";
    /*
        The following keys were generated for testing purposes and are not assigned
        or used anywhere. These are here to verify JSON content and expected responses.
     */
    private static final String RSAPrivateKeyForJSON = "-----BEGIN RSA PRIVATE KEY-----\\n" +
            "MIIEpAIBAAKCAQEAn9i20056OOoRdm5Kx8ZZAHvfQX0+KvB63dNUa6VgqQzmiq3p\\n" +
            "kdKliO5dDhX4+ci6IXKAV4jboJVV5I7UCLYXfa2maZY6N2tETcYqAjZ43whIjQry\\n" +
            "I8q6XUTZlIaX92boUFQZJ2XeZ7Dbvv3a9y7u8BP96FXl0YbNXeYS67JEUQ/LfT16\\n" +
            "TWstHaVthMRr3NBlO3nZ87IZTvWTHHcQr1dR35s53PRMZIRGHr2LYiVfBOcFck9r\\n" +
            "5O2dshBHYK9lP2OsskO1hyFKjekSAmleh9gUKyniwDuRB1xPFP3a7XOs12TxtiBG\\n" +
            "09P7H2uwdxBkNJR8LRXVtx4liTsrmYZqPbT61QIDAQABAoIBACgGQbEjY2NA6OJ4\\n" +
            "t2zSc5K1ca+aRqjF0l4c/nR90nhB7LAo3+VNk3l4BgDy64bQBhs96nkAoc3R1tIG\\n" +
            "GX2c6sDjbFnf7e/WgiHfTsGDFjzyfAglB4CC0KtuA/U2xnKCaAVFKY896LSaCkwG\\n" +
            "kH94VwfyWY+Fgqg2UtICPBacapLJNl8/P20BiEUcySTi8e1zhX9aE4n2Ukl09EtF\\n" +
            "iDdPNP0Eez0hHLDLZDI2j7gFFynlgYMQrSchPEjBQ/H57IvB3LBxzo1f75t4tukY\\n" +
            "G+hwnZpJcna84JdIa00bwjm6QQr+CxUK+Se+AKNagzAXXY8aO1GVGm2f+9FMy4kc\\n" +
            "oeU1PjkCgYEAzaCnDd+6Jsl4gzt4UZfW0wGDuOexQ5ofsVqGycXL83d9nP3I14VJ\\n" +
            "TMbfb8WRybEajAT57h1bLHTBdaeicT3uYB0Z0+ZaDJX2Faj8L67OUyLXDkGAQHL8\\n" +
            "1FANLJqsjRHwTyu86ZOnH91g1AVJDSbmAwKOHVoNLzE/6TdRrDaI7QMCgYEAxwEJ\\n" +
            "IyJzwh1O+2ulypPDxEohQ/Wqd8vfjtK6JvMpxN2YAh+X4Yt0rfAVPXx9YZzcHXBr\\n" +
            "e/kUxqKncuqZWOHAjDoU5io1dUPa3J8Uzr3isLtV6D34QoFzIHOFhIXQeORDxheZ\\n" +
            "xlqde/tS/PoluhuRqy70PDyUgyGm4VICplluFUcCgYEAhCIE1wx58SyrjSCs6zl3\\n" +
            "6PVjMHFp3jfuv6edT5ETwqp5BGWsJpnWhUiSEZ/SU9nDZlBYTiN9D+8i1bjX0I7s\\n" +
            "W8S3cQvnt4ixri99hiJ9IL0VlmqOwFtjjga2wH/P+4KYejdv2GRyEy7NZtDSpWnm\\n" +
            "ie6dZc0VBctO90z95XzRtgMCgYBFYNcOqLQyuIUQojbqqRlXFYEDcGI94ZXO9Shw\\n" +
            "69VkDN0x4FHTEAtdmJXPGdeccFNM8CSI1A2qMoquRZuwoQO/33/pvk1k1IM45z0Z\\n" +
            "1plxg94vWWtzxC2e4qdpzD0h6HK4XQH/ZRgCYVxNVehROctPIs+DMJuWG+VSKIVD\\n" +
            "+WmngQKBgQC2Rof3+obBkALf/4lX9UdZp9nnGahi3F6CGsD053nFmzrJzOgX0b/e\\n" +
            "PVLGsmfFXtRAVM473EzyJpWX8Y/MJImNOsOK5R6IaVUMsDJ98hfaPksds2I7bsQU\\n" +
            "PAyRVhqpzv+zEBEHon8dL3FgBGPHKZvDhEAUsu9iRKWhs68xaAThCQ==\\n" +
            "-----END RSA PRIVATE KEY-----";
    private static final String RSAPublicKey         = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn9i20056OOoRdm5Kx8ZZ\n" +
            "AHvfQX0+KvB63dNUa6VgqQzmiq3pkdKliO5dDhX4+ci6IXKAV4jboJVV5I7UCLYX\n" +
            "fa2maZY6N2tETcYqAjZ43whIjQryI8q6XUTZlIaX92boUFQZJ2XeZ7Dbvv3a9y7u\n" +
            "8BP96FXl0YbNXeYS67JEUQ/LfT16TWstHaVthMRr3NBlO3nZ87IZTvWTHHcQr1dR\n" +
            "35s53PRMZIRGHr2LYiVfBOcFck9r5O2dshBHYK9lP2OsskO1hyFKjekSAmleh9gU\n" +
            "KyniwDuRB1xPFP3a7XOs12TxtiBG09P7H2uwdxBkNJR8LRXVtx4liTsrmYZqPbT6\n" +
            "1QIDAQAB\n-----END PUBLIC KEY-----";
    private static final String testUser             = "testuser";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Mock
    private HttpClientContext     context;
    @Mock
    private HttpClientBuilder     builder;
    @Mock
    private CloseableHttpClient   closer;
    @Mock
    private CloseableHttpResponse response;
    @Mock
    private HttpEntity            entity;
    @Mock
    private StringCredentials     credentials;
    @Mock
    private CookieStore           store;
    private JWTSigner.Options options = new JWTSigner.Options();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Test that signs JWT with a private key and verifies the contents
     * using the matching public key.
     *
     * @throws Exception
     */
    @Test
    public void testTokenCanBeVerifiedWithPublicKey() throws Exception {
        final Secret secret = PowerMockito.mock(Secret.class);
        // final payload
        final String secretText = String.format(DCOS_AUTH_JSON, testUser, RSAPrivateKeyForJSON, "RS256");

        // convert pub key string to actual publickey
        final KeyFactory         keyFactory = KeyFactory.getInstance("RSA", "BC");
        final PemReader          pemReader  = new PemReader(new StringReader(RSAPublicKey));
        final byte[]             content    = pemReader.readPemObject().getContent();
        final X509EncodedKeySpec keySpec    = new X509EncodedKeySpec(content);
        final PublicKey          publicKey  = keyFactory.generatePublic(keySpec);

        Whitebox.setInternalState(secret, "value", secretText);

        when(credentials.getSecret()).thenReturn(secret);
        when(secret.getPlainText()).thenReturn(secretText);

        final DcosAuthImpl dcosAuth = new DcosAuthImpl(credentials,
                options,
                ContentType.APPLICATION_JSON,
                builder,
                context);
        final DcosLoginPayload payload = dcosAuth.createDcosLoginPayload();
        assertNotNull("Payload is null", payload);
        assertEquals("Uid does not match", testUser, payload.getUid());
        assertNotNull("Token was not created", payload.getToken());

        // this proves we can validate the JWT with public key
        final JWTVerifier         verifier = new JWTVerifier(publicKey);
        final Map<String, Object> claims   = verifier.verify(payload.getToken());
        assertFalse("Should be populated", claims.isEmpty());
        assertEquals("Users do not match", testUser, claims.get("uid"));
    }

    /**
     * Test that all other algorithms besides RS256 are rejected.
     *
     * @throws Exception
     */
    @Test
    public void testHSSecretKey() throws Exception {
        final Secret   secret = PowerMockito.mock(Secret.class);
        final String[] algs   = new String[]{"HS512", "HS256", "RS512"};
        for (final String alg : algs) {
            final String secretText = String.format(DCOS_AUTH_JSON, testUser, "a secret key", alg);

            Whitebox.setInternalState(secret, "value", secretText);

            when(credentials.getSecret()).thenReturn(secret);
            when(secret.getPlainText()).thenReturn(secretText);

            final DcosAuthImpl dcosAuth = new DcosAuthImpl(credentials,
                    options,
                    ContentType.APPLICATION_JSON,
                    builder,
                    context);
            try {
                dcosAuth.createDcosLoginPayload();
                assertFalse("Invalid algorithm was accepted", true);
            } catch (AuthenticationException e) {
                assertTrue("Does not list valid algorithm in message", e.getMessage().contains("RS256"));
            }
        }
    }


    /**
     * Test that an invalid JSON payload does not leak the content of the credentials to the error log.
     *
     * @throws Exception
     */
    @Test
    public void testSecretIsNotLeakedInException() throws Exception {
        final Secret secret        = PowerMockito.mock(Secret.class);
        final String credentialsId = "cred-id";
        // final payload
        final String secretText = "this is not a valid json{}";
        Whitebox.setInternalState(secret, "value", secretText);

        when(credentials.getSecret()).thenReturn(secret);
        when(credentials.getId()).thenReturn(credentialsId);
        when(secret.getPlainText()).thenReturn(secretText);

        final DcosAuthImpl dcosAuth = new DcosAuthImpl(credentials,
                options,
                ContentType.APPLICATION_JSON,
                builder,
                context);

        try {
            dcosAuth.createDcosLoginPayload();
            assertTrue("Invalid JSON", false);
        } catch (AuthenticationException ae) {
            assertFalse("Contains secret", ae.getMessage().contains(secretText));
            assertTrue("Does not have the credential id", ae.getMessage().contains(credentialsId));
        } catch (Exception e) {
            assertTrue("Wrong exception was thrown", false);
        }
    }

    /**
     * Test the flow of JWT signing, request, and subsequently retrieving the
     * proper cookie from a successful JWT handshake. This does not verify that
     * the passed in certificate or key was legitimate, beyond its conversion
     * to PKCS8.
     *
     * @throws Exception
     */
    @Test
    public void testGetToken() throws Exception {
        final Secret                  secret        = PowerMockito.mock(Secret.class);
        final List<Cookie>            cookies       = new ArrayList<Cookie>(1);
        final String                  expectedToken = "surprise!";
        final TestCloseableHttpClient testClient    = new TestCloseableHttpClient(response);

        // final payload
        final String secretText = String.format(DCOS_AUTH_JSON, testUser, RSAPrivateKeyForJSON, "RS256");
        Whitebox.setInternalState(secret, "value", secretText);

        when(credentials.getSecret()).thenReturn(secret);
        when(secret.getPlainText()).thenReturn(secretText);
        when(builder.build()).thenReturn(testClient);
        when(context.getCookieStore()).thenReturn(store);

        cookies.add(new BasicClientCookie(DcosAuthImpl.DCOS_AUTH_COOKIE, expectedToken));
        when(store.getCookies()).thenReturn(cookies);

        final TokenAuthProvider provider = new DcosAuthImpl(credentials,
                options,
                ContentType.APPLICATION_JSON,
                builder,
                context);
        final String token = provider.getToken();

        assertEquals("Expected token was not received", expectedToken, token);
        assertNotNull("Request was null", testClient.getRequest());
        assert testClient.getRequest() instanceof HttpEntityEnclosingRequestBase;
        final StringWriter writer = new StringWriter();
        IOUtils.copy(((HttpEntityEnclosingRequestBase) testClient.getRequest()).getEntity().getContent(), writer, "UTF-8");
        final String     entityContent = writer.toString();
        final JSONObject json          = JSONObject.fromObject(entityContent);
        assertEquals("User does not match", testUser, json.getString("uid"));
        assertFalse("Token is empty", json.getString("token").isEmpty());
    }

    /**
     * Confirm that HS* keys work as expected.
     *
     * @throws Exception
     */
    @Test
    public void testGetTokenFromCert() throws Exception {
        final String                  testEndPoint  = "https://leader.mesos/acs/api/v1/auth/login";
        final String                  testUser      = "test user";
        final Secret                  secret        = PowerMockito.mock(Secret.class);
        final List<Cookie>            cookies       = new ArrayList<Cookie>(1);
        final String                  expectedToken = "surprise!";
        final TestCloseableHttpClient testClient    = new TestCloseableHttpClient(response);

        // final payload
        final String secretText = String.format(
                "{\"uid\":\"%s\",\"login_endpoint\":\"%s\",\"private_key\":\"%s\",\"scheme\":\"RS256\"}",
                testUser, testEndPoint, RSAPrivateKeyForJSON);

        Whitebox.setInternalState(secret, "value", secretText);

        when(credentials.getSecret()).thenReturn(secret);
        when(secret.getPlainText()).thenReturn(secretText);
        when(builder.build()).thenReturn(testClient);
        when(context.getCookieStore()).thenReturn(store);

        cookies.add(new BasicClientCookie(DcosAuthImpl.DCOS_AUTH_COOKIE, expectedToken));
        when(store.getCookies()).thenReturn(cookies);

        final TokenAuthProvider provider = new DcosAuthImpl(credentials,
                options,
                ContentType.APPLICATION_JSON,
                builder,
                context);
        final String token = provider.getToken();

        assertEquals("Expected token was not received", expectedToken, token);
        assertNotNull("Request was null", testClient.getRequest());
        assertEquals("Wrong login endpoint", testEndPoint, testClient.getRequest().getURI().toString());

        assert testClient.getRequest() instanceof HttpEntityEnclosingRequestBase;
        final StringWriter writer = new StringWriter();
        IOUtils.copy(((HttpEntityEnclosingRequestBase) testClient.getRequest()).getEntity().getContent(), writer, "UTF-8");
        final String     entityContent = writer.toString();
        final JSONObject json          = JSONObject.fromObject(entityContent);
        assertEquals("User does not match", testUser, json.getString("uid"));
        assertFalse("Token is empty", json.getString("token").isEmpty());
    }

    /**
     * A CloseableHttpClient that is able to set the response to be
     * returned when {@link TestCloseableHttpClient#execute(HttpUriRequest, HttpContext) execute}
     * is called.
     */
    class TestCloseableHttpClient extends CloseableHttpClient {
        HttpUriRequest        request;
        CloseableHttpResponse response;

        TestCloseableHttpClient(CloseableHttpResponse response) {
            super();
            this.response = response;
        }

        private HttpUriRequest getRequest() {
            return request;
        }

        @Override
        protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context) throws IOException, ClientProtocolException {
            return null;
        }

        @Override
        public CloseableHttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException, ClientProtocolException {
            this.request = request;
            return response;
        }

        @Override
        public void close() throws IOException {

        }

        @Override
        public HttpParams getParams() {
            return null;
        }

        @Override
        public ClientConnectionManager getConnectionManager() {
            return null;
        }
    }
}