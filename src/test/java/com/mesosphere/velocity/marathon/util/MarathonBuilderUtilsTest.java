package com.mesosphere.velocity.marathon.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MarathonBuilderUtilsTest {

    @Test
    public void testRmTrailingSlash() throws Exception {
        final String withSlash = "sometext/";
        final String noSlash   = "sometext";

        assertEquals(noSlash, MarathonBuilderUtils.rmSlashFromUrl(withSlash));
        assertEquals(noSlash, MarathonBuilderUtils.rmSlashFromUrl(noSlash));
    }
}
