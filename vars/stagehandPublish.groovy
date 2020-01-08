def call(app, version) {
  def gitCommit = currentGitCommit()
  sh "curl -v --retry 3 --retry-delay 5 -XPOST http://stagehand.alpha.revinate.net/build/${app} -d 'version=${version}' -d 'git-hash=${gitCommit}'"
}

