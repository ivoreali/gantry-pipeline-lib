def call(app, tribe) {
  build job: 'kube-deploy', parameters: [
    [$class: 'StringParameterValue', name: 'application', value: app],
    [$class: 'StringParameterValue', name: 'namespace',   value: tribe],
    [$class: 'StringParameterValue', name: 'environment', value: 'stage']
  ], propagate: false, wait: false
}

