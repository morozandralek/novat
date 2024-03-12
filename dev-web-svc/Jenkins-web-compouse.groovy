
def prepare_web_root_dir(project_name, git_url){
  dir("$project_name"){
    git credentialsId: 'ssh-jenkins-ci-user', url: "$git_url", branch: "master"
    sh("""git branch --set-upstream-to=origin/master master""")
  }
}

def replace_docker_compose(project_name, host){
  String compose_file = readFile "$project_name/docker-compose.tpl"
  env_num = host
  compose_file = compose_file.replaceAll("ENV_REP","oz${env_num}")
  writeFile(file: "$project_name/docker-compose.yaml", text: compose_file)
}

def run_and_copy_docker_compose(project_name, host, dest_path){
  stage("Copy docker file"){
    withCredentials([sshUserPrivateKey(credentialsId: 'ssh-jenkins-ci-user', keyFileVariable: 'SSH_KEY')]) {
      sh """rsync -r -e "ssh -i $SSH_KEY -o StrictHostKeyChecking=no" --rsync-path="sudo -A rsync" ./$project_name/docker-compose.yaml jenkins@$host:$dest_path/$project_name/docker-compose.yaml"""
      sh """rsync -r -e "ssh -i $SSH_KEY -o StrictHostKeyChecking=no" --rsync-path="sudo -A rsync" ./$project_name/ci jenkins@$host:$dest_path/$project_name"""
    }
  }
  stage("Docker compose up ${project_name}"){
    withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
        def remote = [:]
        remote.user = "jenkins"
        remote.identityFile = sshKey
        remote.port = 22
        remote.name = "${host}"
        remote.host = "${host}"
        remote.allowAnyHosts = true
        sshCommand remote: remote, command: "chown -R www-data:www-data /opt/web/${project_name}/", sudo: true
        sshCommand remote: remote, command: "sudo docker-compose -f /opt/web/${project_name}/docker-compose.yaml up --force-recreate --build -d", sudo: true
    }
  }
}

try{
node("ansible"){
  stage("Prepare"){
    selectServer=sh(script: "dig +short oz${dev_list}.loc", returnStdout: true).trim()
    check_job.run_job_file("${dev_list}", selectServer, "job_check")
    echo "${selectServer}"
  }
}

node("srv-jenkins-agent-1") {
  stage("Prepare web auth"){
    prepare_web_root_dir("auth", "ssh://git@git.oz.by:7999/oz/auth.git")
    replace_docker_compose("auth", "${dev_list}")
    run_and_copy_docker_compose("auth", "${selectServer}", "/opt/web")
  }

  stage("Prepare web ozby"){
    prepare_web_root_dir("ozby", "ssh://git@git.oz.by:7999/oz/ozby.git")
    replace_docker_compose("ozby", "${dev_list}")
    run_and_copy_docker_compose("ozby", "${selectServer}", "/opt/web")
  }

  stage("Prepare web ozapi"){
    prepare_web_root_dir("ozapi", "ssh://git@git.oz.by:7999/oz/ozapi.git")
    replace_docker_compose("ozapi", "${dev_list}")
    run_and_copy_docker_compose("ozapi", "${selectServer}", "/opt/web")
  }

  stage("Prepare web ozadmapi"){
    prepare_web_root_dir("ozadmapi", "ssh://git@git.oz.by:7999/oz/ozadmapi.git")
    replace_docker_compose("ozadmapi", "${dev_list}")
    run_and_copy_docker_compose("ozadmapi", "${selectServer}", "/opt/web")
  }

  stage("Prepare web ozstorage"){
    prepare_web_root_dir("ozstorage", "ssh://git@git.oz.by:7999/oz/ozstorage.git")
    replace_docker_compose("ozstorage", "${dev_list}")
    run_and_copy_docker_compose("ozstorage", "${selectServer}", "/opt/web")
  }
}
}finally {
  echo "Finally run selectServer=$selectServer dev_list=$dev_list"
  node("ansible"){
    check_job.stop_job_file("${dev_list}", selectServer, "job_check")
  }
}
