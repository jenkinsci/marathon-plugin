# Marathon Plugin for Jenkins
This is a `maven` project.

## Local Testing

```
$ git clone git@github.com:mesosphere/jenkins-marathon-plugin.git
$ cd jenkins-marathon-plugin
$ mvn hpi:run
```

To reset the Jenkins instace, remove the local `work` directory.

```
$ mvn clean && rm -rf ./work/
```

