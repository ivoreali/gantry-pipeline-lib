package gantry

def withName(name) {
    this.shortName = name
    this
}

def withMakeBuildCommand(command) {
    this.makeBuildCommand = command
    this
}

def withVersionPrefix(version) {
    this.versionPrefix = version
    this
}

def withNodeLabel(nodeLabel) {
    this.nodeLabel = nodeLabel
    this
}

def withTests(makeTestCommand) {
    this.enableTests = true
    this.makeTestCommand = makeTestCommand
    this
}

def withPublishedHtml(reportDir = 'tests/_output/', reportFiles = 'report.html') {
    this.publishHtmlReportDir = reportDir
    this.publishHtmlReportFiles = reportFiles
    this
}

def withTestReport(testResultXmlPath) {
    this.testResultXmlPath = testResultXmlPath
    this
}

def withTestCoverageReport(cloverReportDir = 'tests/_output', cloverReportFileName = 'coverage.xml') {
    this.cloverReportDir = cloverReportDir
    this.cloverReportFileName = cloverReportFileName
    this
}

def withAutoDeployToTestViaKubeCtl(kubeNamesToDeploy) {
    this.enableAutoDeploy = true
    if (!(Collection.isAssignableFrom(kubeNamesToDeploy.getClass()) || kubeNamesToDeploy.getClass().isArray())) {
        this.kubeNamesToDeploy = [kubeNamesToDeploy]
    } else {
        this.kubeNamesToDeploy = kubeNamesToDeploy
    }
    this
}

def withAutoDeployToStageViaKubeCtl(kubeNamesToDeployStage) {
    this.enableAutoDeploy = true
    if (!(Collection.isAssignableFrom(kubeNamesToDeployStage.getClass()) || kubeNamesToDeployStage.getClass().isArray())) {
        this.kubeNamesToDeployStage = [kubeNamesToDeployStage]
    } else {
        this.kubeNamesToDeployStage = kubeNamesToDeployStage
    }
    this
}

def withAutoDeployToProdViaKubeCtl(kubeNamesToDeployProd) {
    this.enableAutoDeploy = true
    if (!(Collection.isAssignableFrom(kubeNamesToDeployProd.getClass()) || kubeNamesToDeployProd.getClass().isArray())) {
        this.kubeNamesToDeployProd = [kubeNamesToDeployProd]
    } else {
        this.kubeNamesToDeployProd = kubeNamesToDeployProd
    }
    this
}

def withDependentJobs(dependentJobs) {
    this.dependentJobs = true
    if (!(Collection.isAssignableFrom(dependentJobs.getClass()) || dependentJobs.getClass().isArray())) {
        this.dependentJobs = [dependentJobs]
    } else {
        this.dependentJobs = dependentJobs
    }
    this
}

def notifySlackChannel(channel) {
    this.slackChannel = channel
    this
}

def execute(dockerFilePath = 'Dockerfile') {
    node(expandValue('minionLabels', '')) {
        slack.channel = expandValue('slackChannel', '#appdev-r')
        checkout scm
        makeBuild()
        if (expandValue('enableTests', false)) {
            runTests()
        }
        if (expandValue('enableAutoDeploy', false)) {
            def version = buildWorkingDirectoryIntoVersionedImage(dockerFilePath)
            deployToKube(version)
        }
        if (expandValue('dependentJobs', false)) {
            runDependentJobs()
        }
        notifyIfStableAgain(slack.channel)
    }
}

private def makeBuild() {
    stage('make build') {
        try {
            sh "mkdir -p ${env.HOME}/.composer"
            if (!expandValue('makeBuildCommand', false)) {
                error "makeBuildCommand not set. use withMakeBuildCommand"
            }
            sha1FromLastSuccesfulBuild = buildMetadata.getLastSuccessfulBuildSha1()
            sh "git rev-parse HEAD > sha1FromHead"
            sha1FromHead = readFile('sha1FromHead').trim()
            sh "rm sha1FromHead || true"
            withEnv([
                    "GIT_PREVIOUS_SUCCESSFUL_COMMIT=${sha1FromLastSuccesfulBuild ?: ''}",
                    "GIT_COMMIT=${sha1FromHead ?: 'HEAD'}"
            ]) {
                sh "${this.makeBuildCommand}"
            }
        } catch (all) {
            slack.error "${env.JOB_NAME} build failed - ${env.BUILD_URL}"
            throw all
        }
    }

}

private def runDependentJobs() {
    stage('run dependent jobs') {
        try {
            for (int i = 0; i < this.dependentJobs.size; i ++) {
                build job: "${this.dependentJobs.get(i)}", propagate: true, wait: true
            }
        } catch (all) {
            slack.error "${env.JOB_NAME} dependent job(s) failed - ${env.BUILD_URL}"
            throw all
        }
    }

}

private def runTests() {
    stage ('run tests') {
        try {
            if (!expandValue('makeTestCommand', false)) {
                error "makeTestCommand not set. use withMakeTestCommand"
            }
            sh("docker-compose down; docker-compose pull")
            sh("${this.makeTestCommand}")
            if (!expandValue('testResultXmlPath', false)) {
                error "testResultXmlPath not set. use withTestReport"
            }
            echo 'Test successful'
        } catch (all) {
            slack.error "${env.JOB_NAME} test failed - ${env.BUILD_URL}"
            throw all
        } finally {
            try {
                step([$class: 'JUnitResultArchiver', testResults: this.testResultXmlPath])
            } catch (Exception e) {
                //ignore exception
            }
            try {
                if (expandValue('publishHtmlReportDir', false) && expandValue('publishHtmlReportFiles', false)) {
                    publishHTML(target: [allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: this.publishHtmlReportDir, reportFiles: this.publishHtmlReportFiles, reportName: 'HTML Report'])
                }
            } catch (Exception e) {
                //ignore exception
            }
            sh "docker-compose down"
        }
        if (expandValue('cloverReportDir', false) && expandValue('cloverReportFileName', false)) {
            try {
                step([$class: 'CloverPublisher', cloverReportDir: this.cloverReportDir, cloverReportFileName: this.cloverReportFileName])
            } catch (all) {
                slack.error "${env.JOB_NAME} test coverage failed - ${env.BUILD_URL}"
                throw all
            }
        }
    }

}

private def buildWorkingDirectoryIntoVersionedImage(dockerfile) {
    stage('build and tag image') {
        try {
            def fullName = getFullName(this.shortName)
            sh "docker build -t ${fullName} -f ${dockerfile} ."
            def version = currentVersion(expandValue('versionPrefix', '1.0'))
            def taggedName = getTaggedName(fullName, version)
            sh "docker tag ${fullName} ${taggedName}"
            version
        } catch (all) {
            slack.error "${env.JOB_NAME} - build working directory into versioned image failed - ${env.BUILD_URL}"
            throw all
        }
    }
}


private def deployToKube(version) {
    stage('upload image and deploy to test') {
        try {
            def fullName = getFullName(this.shortName)
            def taggedName = getTaggedName(fullName, version)
            hoister.betterDockerPush(taggedName)
            hoister.betterDockerPush(fullName)
            for (int i = 0; i < expandValue('kubeNamesToDeploy', []).size; i ++) {
                stagehandPublish(this.kubeNamesToDeploy.get(i), version)
                sh "curl --retry 3 --retry-delay 5 'http://jenkins-savitri.alpha.revinate.net/job/Deploy-to-test-K8S/buildWithParameters?token=DN2cSSyvXukhWYTUwY2F&app=${this.kubeNamesToDeploy.get(i)}'"
            }
            for (int i = 0; i < expandValue('kubeNamesToDeployStage', []).size; i ++) {
                build job: 'kube-deploy', parameters: [
                        [$class: 'StringParameterValue', name: 'application', value: "${this.kubeNamesToDeployStage.get(i)}"],
                        [$class: 'StringParameterValue', name: 'namespace', value: "appdev"],
                        [$class: 'StringParameterValue', name: 'environment', value: 'stage'],
                        [$class: 'StringParameterValue', name: 'version', value: "${version}"]
                ], propagate: true, wait: true
            }
            for (int i = 0; i < expandValue('kubeNamesToDeployProd', []).size; i ++) {
                if (this.kubeNamesToDeployProd.get(i) == 'inguest') {
                    sh "curl 'http://jenkins-mimir.alpha.revinate.net/job/Deploy-to-prod-K8s/buildWithParameters?token=a3ZqR2JlNEI1MnhibkhackQvREEK&app=inguest&version=${version}'"
                } else if (this.kubeNamesToDeployProd.get(i) == 'rns') {
                    sh "curl 'http://jenkins-mimir.alpha.revinate.net/job/Deploy-to-prod-K8s/buildWithParameters?token=a3ZqR2JlNEI1MnhibkhackQvREEK&app=rns&version=${version}'"
                } else {
                    build job: 'kube-deploy', parameters: [
                            [$class: 'StringParameterValue', name: 'application', value: "${this.kubeNamesToDeployProd.get(i)}"],
                            [$class: 'StringParameterValue', name: 'namespace', value: "appdev"],
                            [$class: 'StringParameterValue', name: 'environment', value: "prod"],
                            [$class: 'StringParameterValue', name: 'version', value: "${version}"]
                    ], propagate: true, wait: true
                }
            }
        } catch (all) {
            slack.error "${env.JOB_NAME} - upload image and deploy to test failed - ${env.BUILD_URL}"
            throw all
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

private def getFullName(shortName) {
    "registry-v2.revinate.net/app/${shortName}"
}

private def getTaggedName(fullName, version) {
    "${fullName}:${version}"
}
