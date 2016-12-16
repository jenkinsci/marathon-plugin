package com.mesosphere.velocity.marathon.fields;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class MarathonUriTest {
    @Test
    public void testHashAndEquals() throws Exception {
        MarathonUri uri1 = new MarathonUri("value1");
        MarathonUri uri2 = new MarathonUri("value2");
        MarathonUri uri3 = new MarathonUri("value1");

        assertNotEquals(uri1, uri2);
        assertNotEquals(uri2, uri3);
        assertEquals(uri1, uri3);

        assertNotEquals(uri1.hashCode(), uri2.hashCode());
        assertNotEquals(uri2.hashCode(), uri3.hashCode());
        assertEquals(uri1.hashCode(), uri3.hashCode());
    }

}