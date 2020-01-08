def call() {
  sh "ls build/libs | grep \"^.*\\.jar\$\" | head -1 > jarname"
  def jarname = readFile('jarname').trim()
  if (jarname) {
    echo "Found Jar with name ${jarname}"
    def matcher = jarname =~ /^.+-([a-zA-Z0-9\.]+)\.jar$/
    if (matcher) {
      def version = matcher[0][1]
      echo "Version of Jar is ${version}"
      return version
    }
  }
  return 'unknown'
}
