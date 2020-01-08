def channel

def error(message) {
  slackSend color: 'danger', channel: this.channel, message: message
}

def warn(message) {
  slackSend color: 'warning', channel: this.channel, message: message
}

def info(message) {
  slackSend color: 'good', channel: this.channel, message: message
}
