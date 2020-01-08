package gantry;

def onMinionsWithLabels(minionLabels) {
  this.minionLabels = minionLabels
  this
}

def withName(name) {
  this.name = name
  this
}

def withVersionPrefix(version) {
  this.versionPrefix = version
  this
}

def inferVersionFromJar() {
  this.inferVersionFromJar = true
  this
}

def withFunctionalTests() {
  this.enableFunctionalTests = true
  this
}

def withJvmFunctionalTests() {
  this.enableFunctionalTests = true
  this.jvmFunctionalTests = true
  this
}

def withSonarAnalysis() {
  this.enableSonar = true
  this
}

def withAutoDeployToTest() {
  this.enableAutoDeploy = true
  this
}

def withPostDeployTests(env) {
  this.postDeployTestsEnv = env
  this
}

def withDockerRegistry(registry) {
  this.dockerRegistry = registry
  this
}

def withK8sNamespace(namespace) {
  this.k8sNamespace = namespace
  this
}

def withAdditionalParallelGradleTasks(name) {
  this.withAdditionalParallelTask(name, gradleTaskFactory(name))
  this
}

def withAdditionalParallelTask(taskName, closure) {
  this.additionalParallelTasks = expandValue('additionalParallelTasks', [:])
  this.additionalParallelTasks[taskName] = {
    stage("Additional task ${taskName}") {
      node(minionLabels()) {
        closure()
      }
    }

  }
  this
}

def notifySlackChannel(channel) {
  this.slackChannel = channel
  this.publishStatusToSlack = true
  this
}

def execute() {
  slack.channel = expandValue('slackChannel', '#eng-r')
  hoister.registry = expandValue('dockerRegistry', 'registry-v2.revinate.net/app')
  hoister.imageName = this.name

  if (expandValue('publishStatusToSlack', false)) {
    slack.info "${this.name} jenkins gantry build started"
  }
  
  javaBuild()

  def longTasks = expandValue("additionalParallelTasks", [:])

  if (expandValue('enableFunctionalTests', false)) {
    longTasks['functionalTests'] = { funcTests() }
  }

  // disable sonar until a new server is set in AWS
  //if (expandValue('enableSonar', false)) {
    //longTasks['sonarCheck'] = { sonarCheck() }
  //}

  parallel longTasks

  dockerBuild(expandValue('versionPrefix', '1.0'), expandValue('inferVersionFromJar', false))

  if (expandValue('enableAutoDeploy', false)) {
    autoDeploy()
  }

  if (expandValue('postDeployTestsEnv', false)) {
    postDeployTests()
  }

  if (expandValue('publishStatusToSlack', false)) {
    slack.info "${this.name} jenkins gantry build finished"
  }
  
  notifyIfStableAgain(slack.channel)
}

private def minionLabels() {
  expandValue('minionLabels', '')
}

private def javaBuild() {
  stage('Java build') {
    node(minionLabels()) {
      checkout scm

      try {
        withColors {
          sh './gradlew clean build'
        }
      } catch (all) {
        slack.error "${this.name} java build failed"
        throw all
      } finally {
        step([$class: 'JUnitResultArchiver', testResults: 'build/test-results/**/TEST*.xml'])
      }

      step([$class: 'ArtifactArchiver', artifacts: 'build/libs/*.jar', fingerprint: true])

      stash name: 'jar', includes: 'build/libs/*jar'
    }
  }
}

private def funcTests() {
  stage ('Functional tests') {
    node(minionLabels()) {
      checkout scm
      unstash 'jar'

      usingDocker {
        try {
          withColors {
            sh './gradlew functionalTest'
          }
        } catch (all) {
          slack.error "${this.name} functional test failed"

          echo 'Output from app container'
          sh "docker logs ${this.name} || true"

          throw all
        } finally {
          if (expandValue('jvmFunctionalTests', false)) {
            try {
              step([$class: 'JUnitResultArchiver', testResults: 'build/test-results/**/TEST*.xml'])
            } catch (ignored) {
              // likely no tests to archive
            }
          }
        }
      }
      echo 'Test successful'
    }
  }

}

private def sonarCheck() {
  stage('Static code analysis') {
    node(minionLabels()) {
      checkout scm

      sh './gradlew cobertura sonarqube > /dev/null 2>&1'
    }
  }
}

private def dockerBuild(versionPrefix, inferVersionFromJar) {
  stage('Docker build and push') {
    node(minionLabels()) {
      checkout scm

      unstash 'jar'

      def version = inferVersionFromJar ? getVersionFromJar() : currentVersion(versionPrefix)

      usingDocker { hoister.buildAndPush version }
      stagehandPublish this.name, version
    }
  }
}

private def autoDeploy() {
  stage ('Trigger deployment to test') {
    node {
      sh "curl 'http://jenkins-savitri.alpha.revinate.net/job/Deploy-to-test-K8S/buildWithParameters?token=DN2cSSyvXukhWYTUwY2F&app=${this.name}'"
    }
  }
}

private def postDeployTests() {
  // The triggering here could instead be handled by the remote-job-trigger plugin for jenkins, requires jenkins-gantry to be upgraded to version >2.7.1
  stage('Trigger smoke tests') {
    echo 'Waiting 5 minutes for test deploy to complete.'
    sleep 300 // wait 5 minutes for test deploy job to complate
    node {
      def env = expandValue('postDeployTestsEnv', '')
      if (env == "dint") {
        sh "curl 'http://jenkins-savitri.alpha.revinate.net/job/dint_postDeployTests_1_sanityTest/buildWithParameters?token=KyJWqTGVYtC6aUPMpy&DEBUG=false&TESTS_BRANCH=master&CURRENT_TEST=*'"

        sh "curl 'http://jenkins-savitri.alpha.revinate.net/job/dint_postDeployTests_2_smokeTest/buildWithParameters?token=KyJWqTGVYtC6aUPMpy&DEBUG=false&TESTS_BRANCH=master&CURRENT_TEST=*'"

        sh "curl 'http://jenkins-savitri.alpha.revinate.net/job/dint_postDeployTests_3_regressionTest/buildWithParameters?token=KyJWqTGVYtC6aUPMpy&DEBUG=false&TESTS_BRANCH=master&CURRENT_TEST=*'"
      }
    }
  }
}

private def gradleTaskFactory(taskName) {
  return {
    checkout scm

    usingDocker {
      try {
        withColors {
          sh "./gradlew ${taskName}"
        }
      } catch (all) {
        slack.error "${this.name} additional task ${taskName} failed"
        throw all
      } finally {
        try {
          step([$class: 'JUnitResultArchiver', testResults: 'build/test-results/**/TEST-*.xml'])
        } catch (ignored) {
          // likely no tests to archive
        }
      }
    }
  }
}

private def expandValue(prop, defaultValue) {
  try {
    return this."$prop"
  } catch (MissingPropertyException e) {
  }
  return defaultValue
}
