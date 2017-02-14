# Marathon Plugin for Jenkins [![Build Status](https://jenkins.mesosphere.com/service/jenkins/buildStatus/icon?job=marathon-plugin-publish-master)](https://jenkins.mesosphere.com/service/jenkins/job/marathon-plugin-publish-master/)

## Background

[Marathon](https://github.com/mesosphere/marathon) is a production-grade container orchestration platform for [Mesosphere's Datacenter Operating System (DC/OS)](https://dcos.io/) and [Apache Mesos](http://mesos.apache.org/). Marathon is a meta framework: you can start other Mesos frameworks such as Chronos or Storm with it to ensure they survive machine failures. It can launch anything that can be launched in a standard shell. In fact, you can even start other Marathon instances via Marathon.

## Summary
This plugin adds a _Marathon Deployment_ post-build item that updates an application within a target Marathon instance. This can also be used within a workflow or pipeline plugin job.

## Requirements
This plugin requires a `marathon.json` file within a Job's working directory. It
is recommended that this file be present within a project's code repository.

```
{
	"id": "/product/service/myApp",
    "cmd": "env && sleep 300",
    "cpus": 0.25,
    "mem": 16.0
}
```

For more information on how to create a `marathon.json` file, see [Application Basics](https://mesosphere.github.io/marathon/docs/application-basics.html).

## Running Locally
This is an Apache Maven project and requires `mvn`.

```
$ git clone git@github.com:mesosphere/jenkins-marathon-plugin.git
$ cd jenkins-marathon-plugin
$ mvn hpi:run
```

To reset the Jenkins instance, remove the local `target` and `work` directories.

```
$ mvn clean && rm -rf ./work/
```

## Package and Deploy
Run `mvn package` to create an `hpi` file within the local `target` directory.
This artifact can be uploaded as a plugin to a running Jenkins instance.

## Pipeline Plugin Support
This plugin can be called as `marathon(...)` within [a pipeline job](https://github.com/jenkinsci/pipeline-plugin/blob/master/TUTORIAL.md).

```
marathon(
    url: 'http://marathon-instance',
    forceUpdate: false,
    id: 'someid',
    docker: 'mesosphere/jenkins-dev',
    dockerForcePull: true)
```

`url` is required and this still depends on a local "marathon.json" file.

## License

The Marathon Plugin for Jenkins is licensed under the Apache License, Version 2.0. For additional information, see the [LICENSE](LICENSE) file included at the root of this repository.

## Reporting Issues

Please file an issue in [the Jenkins issue tracker](https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20component%20%3D%20marathon-plugin), using the "marathon-plugin" component.

## Releasing

For information on releasing new versions of the plugin, see [the jenkins plugin-hosting guide](https://wiki.jenkins-ci.org/display/JENKINS/Hosting+Plugins).

## Contributing

Please submit changes via a GitHub pull request.
