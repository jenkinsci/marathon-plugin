# Marathon Plugin for Jenkins

##Background
Marathon is a production-grade container orchestration platform for Mesosphere's Datacenter Operating System (DCOS) and Apache Mesos. Marathon is a meta framework: you can start other Mesos frameworks such as Chronos or Storm with it to ensure they survive machine failures. It can launch anything that can be launched in a standard shell. In fact, you can even start other Marathon instances via Marathon.

##Summary
This plugin adds a _Marathon Deployment_ post-build item that updates an application within a target Marathon instance. This can also be used within a workflow or pipeline
plugin job.

## Requirements
This plugin requires a "marathon.json" file within a Job's working directory. It
is recommended that this file be present within a project's code repository.

```
{
	"id": "/product/service/myApp",
    "cmd": "env && sleep 300",
    "cpus": 0.25,
    "mem": 16.0
}
```

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

## workflow/Pipeline Plugin Support
This plugin can be called as `marathon(...)` within a workflow job.

```
marathon(
    url: 'http://marathon-instance',
    appid: 'someid',
    docker: 'mesosphere/jenkins-dev')
```

`url` is required and this still depends on a local "marathon.json" file.
