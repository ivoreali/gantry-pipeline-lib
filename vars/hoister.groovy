import groovy.transform.Field

@Field
def registry
@Field
def imageName
@Field
def dockerfile


def getDeclaredDockerFilename() {
  return expandValue('dockerfile', null)
}

def getFullName() {
  def registry = expandValue('registry', 'registry-v2.revinate.net/app')
  "${registry}/${this.imageName}"
}

def build(path = '.') {
  def filename = getDeclaredDockerFilename()
  def extraFlags = filename == null ? '' : "-f ${filename}"
  sh "docker build -t ${this.imageName} ${extraFlags} ${path}"
}

def pushImage(version = 'latest', includeLatest = true) {
  if (version != 'latest') {
    def taggedName = "${getFullName()}:${version}"
    sh "docker tag ${this.imageName} ${taggedName}"
    betterDockerPush(taggedName)
  }

  if (includeLatest) {
    sh "docker tag ${this.imageName} ${getFullName()}"
    betterDockerPush("${getFullName()}:latest")
  }
}

def betterDockerPush(tag) {
  def attempts = 3
  for (i = 1; i <= attempts; i++) {
    try {
      sh "docker push $tag"
      echo "Image pushed successfully on attempt $i"
      return
    } catch (all) {
      if (attempts == i) {
        echo "All $attempt push attempts failed. Cancelling the build..."
        throw all
      } else {
        echo "Failed to push on attempt $i. Waiting 5 seconds to retry"
        sleep 5
      }
    }
  }
}

def buildAndPush(version = 'latest', buildPath = '.', includeLatest = true) {
  def filename = getDeclaredDockerFilename()
  def extraFlags = filename == null ? '' : "-f ${filename}"

  def remoteName = getFullName()
  def localImageName = "${this.imageName}:${version}"
  def remoteImageName = "${remoteName}:${version}"

  sh "docker build -t ${localImageName} ${extraFlags} ${buildPath}"

  sh "docker tag ${localImageName} ${remoteImageName}"
  betterDockerPush(remoteImageName)

  // latest is just a tag that points to the same version
  if (includeLatest && version != 'latest') {
    def latestRemoteImageName = "${remoteName}:latest"
    sh "docker tag ${localImageName} ${latestRemoteImageName}"
    betterDockerPush(latestRemoteImageName)
  }
}

private def expandValue(prop, defaultValue) {
  try {
    return this."$prop"
  } catch (MissingPropertyException e) {}
  return defaultValue
}

