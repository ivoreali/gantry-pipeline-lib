def call() {
  sh 'git rev-parse --short HEAD > git-revision'
  return readFile('git-revision').trim()
}
