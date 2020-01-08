def call(Closure body) {
  echo 'Cleaning docker daemon before the build'
  removeAllContainers()
  cleanDockerDangling()
  //removeDockerNetworks()

  try {
    body()
  } finally {
    echo 'Cleaning docker deamon after the build'
    removeAllContainers()
    //removeDockerNetworks()
  }
}
