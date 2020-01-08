def call() {
  if (!fileExists('charts')) {
    echo 'No charts folder, skipping helm step'
    return
  }

  dir('charts') {
    echo 'Removing previous charts'
    sh 'rm *tgz || true' // rm returns -1 if nothing is deleted

    echo 'Packaging charts'
    sh '''
      echo Packaging charts
      find . -type d -exec test -e '{}'/Chart.yaml \\; -print | while read chart; do
        helm lint $chart && helm package --save=false $chart
      done
    '''

    echo 'Publishing charts'
    sh '''
      for i in $(ls *tgz); do
        echo Publishing $i
        curl -n -T $i -H "X-Checksum-Sha1: $(sha1sum $i | cut -d' ' -f1)" https://revinate.jfrog.io/revinate/helm/
      done

    '''
  }
}
