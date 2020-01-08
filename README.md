# gantry-pipeline-lib

Jenkins Pipeline supports shared scripts to be reused by multiple builds.
This repo is a mirror to the scripts currently installed in Jenkins.

The Jenkins workflow-cps-global-lib plugin turns Jenkins into a git host.
In order to publish your changes into Jenkins you must add an extra remote
origin to your git

```
git remote add jenkins http://jenkins-gantry.alpha.revinate.net/workflowLibs.git
git push -u jenkins master
```

For more details check the [Jenkins Pipeline documentation]
(https://github.com/jenkinsci/workflow-plugin/tree/master/cps-global-lib).

## Branching Strategy
* `master` branch is intended for use on Jenkins >= 2.X installations (jenkins-gantry.alpha.revinate.net)
* `jenkins.1.x` branch is intended for use Jenkins ~> 1.X installations (app-jenkins-gantry.revinate.net)

## Pipeline code deployment
Currently, a webhook is setup on the `master` branch of this repo, and pushes respective branches to jenkins instances, as described in the Jenkinsfile.

## General purpose functions

* `removeAllContainers()` Removes all running and stopped containers.
* `removeRunningContainers()` *deprecated* Initially it only removed the
  running containers but stopped containers are a problem because those
  could expose ports that are necessary for newer containers. Use
  `removeAllContainers()` instead.
* `cleanDockerDangling()` Deletes all dangling images and volumes. It is
  useful to call it before building and publishing Docker images.
* `stagehandPublish(app, version)` Publishes the build version. This
  build version becomes the last-stable version and it will be picked up
  by the deployment script to deploy to the test environment.
* `usingDocker(Closure body)` Wrapper function that runs `body` closure
  in a clean docker environment.
* `currentVersion(prefix)` Determines the app version (usually the
  Docker image tag) from the current build. The `prefix` defaults to
  `1.0`. The format generated format is
  `<prefix>.<build_number>.<git_commit_hash>`. The git commit hash is
truncated to 3 characters.
* `deployToTest(app, team)` Trigger the deployment of the application
  `app` in the namespace `${team}-test`
* `createArtifactFromGlob(app, glob)` Creates a zip file, a corresponding
  sha1 file of the zip, and jenkins artifacts of the resulting zip/shafile
  (This supports a general "zip"/"sha" workflow for any files found by glob)

## Slack

This wraps the `slackSend` API to a more convenient syntax

```groovy
slack.channel = '#eng'

// red
slack.error 'Build failed'

// yellow
slack.warn 'Build completed with ignored tests'

// green
slack.info 'Build successful'

```

## Docker

### `usingDocker()`
Build steps can create a destroy a lot of containers. To reduce the risk
of failing a build due to some previous state in the docker daemon we
created a function called `usingDocker(body)`

```groovy
usingDocker {
  sh 'docker build -t myapp .'
  sh 'docker run -d myapp'
}

```

In the example above an unamed container with the image `myapp` will be
left running. If this container exposes a port then a later build that
needs the same port could fail. The `usingDocker()` wrapper stops all
the previous containers before and after the build to keep the Jenkins
slave as clean as possible.

### `hoister`
> An apparatus for hoisting, as a block and tackle, a derrick, or a crane.

A small utility to build and push Docker images

```groovy
hoister.registry = 'registry.revinate.net/yourteam'
hoister.imageName = 'app'
hoister.buildAndPush '1.0.0'
```

`buildAndPush(version, buildContextPath, includeLatest)` has the following defaults
* version = `latest`
* buildContextPath = `.`
* includeLatest = `true` (if the version is not `latest`, then add an
  extra tag to mark as `latest` too)

#### Optional Properties
* `hoister.setDockerfile(dockerfileFullPath)`: This can be used when Dockerfile location is different from build context path. Defaults to `Dockerfile`.

## PxBuild
Platform Spring Boot applications have very similar Gradle build steps. This
is an example with all the steps:

```groovy
new gantry.PxBuild()
  .withName(<app name ending with 'r'>)
  .withVersionPrefix(<app version prefix>)
  .inferVersionFromJar()
  .withFunctionalTests()
  .withSonarAnalysis()
  .withAutoDeployToTest()
  .withPostDeployTests('dint')
  .execute()
```

The only required property to be set is the name, everything else is optional.

### Options

* `withName()` name of the application
* `withVersionPrefix(prefix)` sets a custom version prefix, defaults to `1.0`
* `inferVersionFromJar()` sets the version automatically based on the name of
  the Jar produced by the build: e.g. if the build produced `app-1.0.0.abcdefg.jar`
  the version is inferred to be `1.0.0.abcdefg`. If set, the version prefix
  set by `withVersionPrefix` is ignored.
* `withFunctionalTests()` calls the `functionalTest` Gradle task
* `withSonarAnalysis()` calls both `cobertura` and `sonarqube` tasks
* `withAutoDeployToTest()` deploys the application to the test
  environment on a successful build
* `onMinionsWithLabels(labels)` forces this build on minions with labels `labels`.  "jenkins-in-docker" is a label applied to minions running in K8S via https://github.com/revinate-docker/kube-namespaces/blob/master/kube/techops/prod/jenkins-build-agent/deployment.yaml.template and  https://github.com/revinate-docker/docker-socket-proxy
* `withPostDeployTests(team)` runs the smoke tests for a particular team in [jenkins-savitri](http://jenkins-savitri.alpha.revinate.net/) in the test kubernetes environment, currently the only possible value is 'dint'. This is typically combined with _withAutoDeployToTest()_ to run a suite of tests against a new build. 

## SymfonyBuild
Symfony PHP applications have very similar pipeline steps. This
is an example with all the steps:

```groovy
new gantry.SymfonyBuild()
  .withName(<appName>)
  .withVersionPrefix(<app version prefix>)
  .withMakeBuildCommand('docker run --rm -v /var/lib/jenkins/.composer:/var/www/.composer -v `pwd`:/var/www/app registry.revinate.net/appdev/symfony-base:php7-builder make build')
  .withTests('docker-compose run --rm app make test-coverage')
  .withTestReport('tests/_output/report.xml')
  .withPublishedHtml('tests/_output/', 'report.html')
  .withTestCoverageReport('tests/_output', 'coverage.xml')
  .withAutoDeployToTest(<kube-namespace-name>)
  .notifySlackChannel('#appdev-r')
  .execute()
```

The only required property to be set is the name and makeBuildCommand, everything else is optional.
That said, we do not build and upload images unless you deploy to test.

### Options

* `withName(<appName>)` name of the application
* `withVersionPrefix(prefix)` sets a custom version prefix, defaults to `1.0`
* `withMakeBuildCommand('docker run ... make build')` the docker command to build the app (in the working directory -- no image is created at this point)
* `withTests('docker-compose run ... make test-coverage')` the docker command to run tests (which preferably includes code coverage as part of the test run)
* `withTestReport('tests/_output/report.xml')` the relative path to junit report xml
* `withPublishedHtml('tests/_output/', 'report.html')` the relative directory path and filename of the html report (or anything else you want to persist as part of the test run)
* `withTestCoverageReport('tests/_output', 'coverage.xml')` the relative directory path and filename of the clover code coverage report
* `withAutoDeployToTestViaKubeCtl(<kube-namespace-name>)` builds and tags the image, then deploys to the test environment via kube
* `notifySlackChannel(<slackChannel>)` the slack channel to notify wrt info, exceptions, etc
