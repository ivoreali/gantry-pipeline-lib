def getLastSuccessfulBuildSha1() {
    def job = Jenkins.instance.getItem(env.JOB_NAME)
    return job?.getLastSuccessfulBuild()?.getAction(hudson.plugins.git.util.BuildData.class)?.lastBuiltRevision?.sha1String
}