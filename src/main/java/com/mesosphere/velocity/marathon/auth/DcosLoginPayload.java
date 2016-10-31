package com.mesosphere.velocity.marathon.auth;

public class DcosLoginPayload {
    private String uid;
    private String token;
    private String loginURL;

    public DcosLoginPayload(final String loginURL, final String uid, final String token) {
        this.loginURL = loginURL;
        this.uid = uid;
        this.token = token;
    }

    public static DcosLoginPayload create(final String loginURL, final String uid, final String token) {
        return new DcosLoginPayload(loginURL, uid, token);
    }

    public String getLoginURL() {
        return loginURL;
    }

    public String getUid() {
        return uid;
    }

    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return String.format(DcosAuthImpl.DCOS_AUTH_PAYLOAD, uid, token);
    }
}
