def call() {
  echo 'Removing extra docker networks'
  try {
    sh "docker network ls | grep 'network' | awk '{print \\$1}' | xargs echo docker network rm"
  } catch(all) {
    echo 'Existing network has containers attached'
  }
}

