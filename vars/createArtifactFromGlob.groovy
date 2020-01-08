def call(app, glob) {
        version = currentVersion()
        zipFileName = "${app}.${version}.zip"
        zip(zipFile: zipFileName, glob: glob)
        fileSha = sha1(zipFileName)
        writeFile(file: "${zipFileName}.sha1", text: "${fileSha}  ${zipFileName}")

        archiveArtifacts artifacts: zipFileName + "*", fingerprint: true
}
