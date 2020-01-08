def call(prefix = '1.0') {
  def revision = currentGitCommit()
  return "${prefix}.${env.BUILD_NUMBER}.${revision ?: 1}"
}
