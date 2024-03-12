/*
process = [ 'bash', '-c', "curl -X GET -H \"Content-Type: application/json\"  https://nexus.ozalliance.by/service/rest/repository/browse/skdo-mobile/ | grep -o -E '>mobile-.*tar'" ].execute()
process.waitFor()
artifact_list = process.text.readLines().collect { it.split()[0].replaceAll('>', '')}
return artifact_list.reverse()

*/

/*
import jenkins.model.*
credentialsId = 'ldap_jenkins_ci'
def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
  com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class, Jenkins.instance, null, null ).find{it.id == credentialsId}
username = creds.username
password = creds.password

def gettags = ("git ls-remote -t -h http://${username}:${password}@bitbucket.oz.loc/scm/oz/oz_html.git").execute()
branch_list = gettags.text.readLines().collect { it.split()[1].replaceAll('refs/heads/', '').replaceAll('refs/tags/', '').replaceAll("\\^\\{\\}", '')}
List list_return = branch_list
list_return.add(0, "master")
return list_return
*/


def nexus_repo = "https://docker.ozalliance.by"
def now = new Date()
def currentDate = now.format("yyyyMMdd")
def skdo_mobile_afact = "${SKDO_MOBILE}".replaceAll("\"","")
def skdo_desktop_afact = "${SKDO_DESKTOP}".replaceAll("\"","")
def var_replace_mobile = "${replace_mobile}"
def var_replace_desktop = "${replace_desktop}"
def var_oz_html = "${oz_html}"
def var_oz_html_branch = "${oz_html_branch}"
def home_mobile = "/opt/web/ozby"
def home_desktop = "/opt/web/ozby"
def list_folder_mobile = ["css/mobile-assets", "js/mobile-assets"]
def list_folder_desktop = ["css/desktop-assets", "js/desktop-assets", "img/desktop-assets"]
def nexus_repo_mobile = "https://nexus.ozalliance.by/repository/skdo-mobile/"
def nexus_repo_desktop = "https://nexus.ozalliance.by/repository/skdo-desktop/"
def selectServer = ""



def clean_folder(type, home, list_folder, selectServer){
  stage ("Clean folder ${type} on loc ")
  dir("${type}") {
    sh("rm -rf *")

    for(int folder in list_folder) {
      rm_folder = "${home}/${folder}"
      withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
          def remote = [:]
          remote.user = "jenkins"
          remote.identityFile = sshKey
          remote.port = 22
          remote.name = "${selectServer}"
          remote.host = "${selectServer}"
          remote.allowAnyHosts = true
          sshCommand remote: remote, command: "rm -rf ${rm_folder}/*", sudo: true
      }
     println(rm_folder);
    }
  }
}

def download_artifact(type, nexus_repo, artifact_name){
  stage ("Download artifact ${type} and unarchive"){
    dir("${type}"){
      sh(script: "curl ${nexus_repo}/${artifact_name} --output ${artifact_name}", returnStdout: true).trim()
      sh(script: "tar -xvf ${artifact_name}", returnStdout: true).trim()
    }
  }
}

def replace_artifact(type, home, list_folder, selectServer){
  stage ("Replace artifact ${type}"){
    dir("${type}"){
      for(int folder in list_folder) {
        replace_folder = "${home}/${folder}"
        withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
          def remote = [:]
          remote.user = "jenkins"
          remote.identityFile = sshKey
          remote.port = 22
          remote.name = "${selectServer}"
          remote.host = "${selectServer}"
          remote.allowAnyHosts = true
          sshCommand remote: remote, command: "chown -R jenkins:jenkins ${replace_folder}", sudo: true

          withCredentials([sshUserPrivateKey(credentialsId: 'ssh-jenkins-ci-user', keyFileVariable: 'SSH_KEY')]) {
            sh """rsync -r -e "ssh -i $SSH_KEY -o StrictHostKeyChecking=no" ./dist/${folder}/* jenkins@$selectServer:$replace_folder/"""
          }
          sshCommand remote: remote, command: "chown -R www-data:www-data ${replace_folder}", sudo: true
       }
       println("Replace folder=>${replace_folder}");
      }
    }
  }
}

def run_docker_cmd(selectServer){
  stage("Docker cmd"){
    withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
      def remote = [:]
      remote.user = "jenkins"
      remote.identityFile = sshKey
      remote.port = 22
      remote.name = "${selectServer}"
      remote.host = "${selectServer}"
      remote.allowAnyHosts = true
      sshCommand remote: remote, command: """sudo docker exec php-fpm-ozby php /home/www/oz.by/public_html/_/cron_temp.phtml -cron minimizeCssJs""", sudo: true
    }
  }
}

def oz_html(var_oz_html_branch, selectServer){
  stage("OZ HTML sinc"){
    dir('oz_html'){
      git credentialsId: 'ssh-jenkins-ci-user', branch: "${var_oz_html_branch}", url: 'ssh://git@git.oz.by:7999/oz/oz_html.git'

      withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
        def remote = [:]
        remote.user = "jenkins"
        remote.identityFile = sshKey
        remote.port = 22
        remote.name = "${selectServer}"
        remote.host = "${selectServer}"
        remote.allowAnyHosts = true
        sshCommand remote: remote, command: "chown -R jenkins:jenkins /opt/web/ozby/css /opt/web/ozby/js", sudo: true

        withCredentials([sshUserPrivateKey(credentialsId: 'ssh-jenkins-ci-user', keyFileVariable: 'SSH_KEY')]) {
          sh """rsync -r --exclude 'mobile-assets' --exclude 'desktop-assets'  -e "ssh -i $SSH_KEY -o StrictHostKeyChecking=no" ./css/* jenkins@$selectServer:/opt/web/ozby/css/"""
          sh """rsync -r --exclude 'mobile-assets' --exclude 'desktop-assets'  -e "ssh -i $SSH_KEY -o StrictHostKeyChecking=no" ./js/* jenkins@$selectServer:/opt/web/ozby/js/"""
        }
        sshCommand remote: remote, command: "chown -R www-data:www-data /opt/web/ozby", sudo: true
     }

    }
  }
}

node("srv-jenkins-agent-1"){

  stage("Prepare"){
    selectServer=sh(script: "dig +short oz${dev_list}.loc", returnStdout: true).trim()
    echo "${selectServer}"
    print("${skdo_mobile_afact}")
    print("${skdo_desktop_afact}")
    print("${var_replace_mobile}")
    print("${var_replace_desktop}")
  }


  // MAIN
  if (var_replace_mobile == "true"){

    clean_folder("mobile","${home_mobile}", list_folder_mobile, selectServer)
    download_artifact("mobile", nexus_repo_mobile, skdo_mobile_afact)
    replace_artifact("mobile","${home_mobile}", list_folder_mobile, selectServer)
    run_docker_cmd(selectServer)

  } else if (var_replace_mobile == "false"){
    print("Mobile artifact not selected")
  }

  if (var_replace_desktop == "true"){
    clean_folder("desktop","${home_desktop}", list_folder_desktop, selectServer)
    download_artifact("desktop", nexus_repo_desktop, skdo_desktop_afact)
    replace_artifact("desktop","${home_desktop}", list_folder_desktop, selectServer)
    run_docker_cmd(selectServer)
  } else if (var_replace_desktop == "false"){
    print("Desktop artifact not selected")
  }

  if (var_oz_html == "true"){
    oz_html(var_oz_html_branch, selectServer)
  } else if (var_oz_html == "false"){
    print("OZ html artifact not selected")
  }

}
