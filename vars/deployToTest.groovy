def call(app, tribe) {
  sh "curl 'http://jenkins-savitri.alpha.revinate.net/job/Deploy-to-test-K8S/buildWithParameters?token=DN2cSSyvXukhWYTUwY2F&app=${app}'"
}
