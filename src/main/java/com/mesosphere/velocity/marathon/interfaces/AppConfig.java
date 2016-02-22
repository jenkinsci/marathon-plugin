package com.mesosphere.velocity.marathon.interfaces;

import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;

import java.util.List;

public interface AppConfig {
    String getAppId();
    String getUrl();
    String getDocker();
    List<MarathonUri> getUris();
    List<MarathonLabel> getLabels();
}
