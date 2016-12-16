package com.mesosphere.velocity.marathon.fields;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class MarathonLabelTest {
    @Test
    public void testHashAndEquals() throws Exception {
        MarathonLabel label1 = new MarathonLabel("name1", "value1");
        MarathonLabel label2 = new MarathonLabel("name2", "value2");
        MarathonLabel label3 = new MarathonLabel("name3", "value1");
        MarathonLabel label4 = new MarathonLabel("name1", "value2");
        MarathonLabel label5 = new MarathonLabel("name1", "value1");

        assertNotEquals(label1.hashCode(), label2.hashCode());
        assertNotEquals(label2.hashCode(), label3.hashCode());
        assertNotEquals(label3.hashCode(), label4.hashCode());
        assertNotEquals(label1.hashCode(), label4.hashCode());
        assertEquals(label1.hashCode(), label5.hashCode());

        assertNotEquals(label1, label2);
        assertNotEquals(label2, label3);
        assertNotEquals(label3, label4);
        assertNotEquals(label1, label4);
        assertEquals(label1, label5);
    }
}