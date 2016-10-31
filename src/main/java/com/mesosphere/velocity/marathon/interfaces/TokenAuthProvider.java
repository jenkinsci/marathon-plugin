package com.mesosphere.velocity.marathon.interfaces;

import com.auth0.jwt.JWTAlgorithmException;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;

public interface TokenAuthProvider {
    String getToken() throws IOException, JWTAlgorithmException, InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException;

    void setPayload(HttpEntity entity);

    void setContentType(ContentType contentType);

    void setContentType(String contentType);

}
