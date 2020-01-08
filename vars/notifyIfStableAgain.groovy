
def call(channel) {
  try {
    def item = Jenkins.instance.getItem(env.JOB_NAME)
    def previousBuild = item.getLastBuild().getPreviousBuild()
    previousResult = previousBuild?.getResult() ?: Result.SUCCESS
    if (previousResult == Result.FAILURE) {
      slack.channel = channel
      slack.info "${env.JOB_NAME} is back to normal - ${env.BUILD_URL}"
    }
  } catch (all) {
    //swallow exception
  }
}