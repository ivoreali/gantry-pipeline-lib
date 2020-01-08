def call() {
  echo 'Removing all existing containers'
  try {
    sh 'docker rm -f -v `docker ps -aq` > /dev/null 2>&1'
  } catch(all) {
    echo 'No containers to remove'
  }
}
