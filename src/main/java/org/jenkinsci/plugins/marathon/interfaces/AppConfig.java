package org.jenkinsci.plugins.marathon.interfaces;

import org.jenkinsci.plugins.marathon.fields.MarathonLabel;
import org.jenkinsci.plugins.marathon.fields.MarathonUri;

import java.util.List;

public interface AppConfig {
    String getAppId();
    String getUrl();
    String getDocker();
    List<MarathonUri> getUris();
    List<MarathonLabel> getLabels();
}
