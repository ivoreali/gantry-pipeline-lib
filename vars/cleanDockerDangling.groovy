def call() {
  echo 'Removing dangling Docker images'
  try {
    sh 'docker rmi $(docker images -qf dangling=true) > /dev/null 2>&1'
  } catch(all) {
    echo 'No image to remove'
  }

  echo 'Removing dangling Docker volumes'
  try {
    sh 'docker volume rm $(docker volume ls -qf dangling=true) > /dev/null 2>&1'
  } catch(all) {
    echo 'No volume to remove'
  }
}
