slack.channel = '#eng-r'

stage "Update Jenkins Gantry"
node('jenkins-in-docker') {
  checkout scm
  sh 'git remote rm jenkins || true'
  sh 'git remote add jenkins http://jenkins-gantry.alpha.revinate.net/workflowLibs.git'
  sh 'git push -f jenkins HEAD:refs/heads/master'

  slack.info "Jenkins Gantry level up to ${env.BUILD_NUMBER}"
}
