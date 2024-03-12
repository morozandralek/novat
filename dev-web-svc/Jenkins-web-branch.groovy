
def rsync_web_root(project_name, dest_path, host){
    withCredentials([sshUserPrivateKey(credentialsId: 'ssh-jenkins-ci-user', keyFileVariable: 'SSH_KEY')]) {
      sh """rsync -r -e "ssh -i $SSH_KEY -o StrictHostKeyChecking=no" ./$project_name jenkins@$host:$dest_path"""
    }
}

def replace_docker_compose(project_name, host){
  String compose_file = readFile "$project_name/docker-compose.tpl"
  env_num = host
  compose_file = compose_file.replaceAll("ENV_REP","oz${env_num}")
  writeFile(file: "$project_name/docker-compose.yaml", text: compose_file)
}

def run_docker_compose(project_name, host){
  stage("Docker compose up ${project_name}"){
    withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
        def remote = [:]
        remote.user = "jenkins"
        remote.identityFile = sshKey
        remote.port = 22
        remote.name = "${host}"
        remote.host = "${host}"
        remote.allowAnyHosts = true
        //sshCommand remote: remote, command: "chmod -R 775 /opt/web/${project_name}/", sudo: true
        sshCommand remote: remote, command: "chown -R www-data:www-data /opt/web/${project_name}/", sudo: true
        sshCommand remote: remote, command: "sudo docker-compose -f /opt/web/${project_name}/docker-compose.yaml restart", sudo: true
    }
  }
}

def run_www_data(host){
  stage("Change owner www-data"){
    withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
        def remote = [:]
        remote.user = "jenkins"
        remote.identityFile = sshKey
        remote.port = 22
        remote.name = "${host}"
        remote.host = "${host}"
        remote.allowAnyHosts = true
        sshCommand remote: remote, command: "chown -R www-data:www-data /opt/web/", sudo: true
        sshCommand remote: remote, command: '''sudo ps -ef | grep npm | grep -v grep | awk '{print $2}' | xargs sudo kill | exit 0''', sudo: true
        sshCommand remote: remote, command: '''sudo ps -ef | grep node_modules | grep -v grep | awk '{print $2}' | xargs sudo kill | exit 0''', sudo: true
    }
  }
}

def clear_www_data(host){
  stage("Clear www-data"){
    withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
        def remote = [:]
        remote.user = "jenkins"
        remote.identityFile = sshKey
        remote.port = 22
        remote.name = "${host}"
        remote.host = "${host}"
        remote.allowAnyHosts = true
        sshCommand remote: remote, command: "sudo docker-compose -f /opt/web/ozby/docker-compose.yaml stop", sudo: true
        sshCommand remote: remote, command: "rm -rf /opt/web/*", sudo: true
    }
  }
}



//def clear_container(host){
//  def container_name = [ 'php-fpm-ozby', 'nginx-adm', 'nginx-ozby', 'php-cron-ozby', 'php-fpm-admin', 'php-fpm-admapi', 'nginx-admapi', 'php-fpm-storage', 'nginx-storage', 'nginx-ozapi', 'php-fpm-ozapi', 'nginx-auth', 'php-fpm-auth' ]
//  def image_name = [ 'ozby_php-cron-ozby', 'ozby_nginx-ozby', 'ozby_nginx', 'ozby_php-fpm-ozby', 'ozadmapi_nginx-admapi', 'ozadmapi_php-fpm-admapi', 'ozstorage_nginx-storage', 'ozstorage_php-fpm-storage', 'ozapi_nginx-ozapi', 'ozapi_php-fpm-ozapi', 'auth_nginx-auth', 'auth_php-fpm-auth' ]
//  withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
//      def remote = [:]
//      remote.user = "jenkins"
//      remote.identityFile = sshKey
//      remote.port = 22
//      remote.name = "${host}"
//      remote.host = "${host}"
//      remote.allowAnyHosts = true
//
//
//      for (i = 0; i < container_name.size(); i++) {
//       println("Stop container " + container_name[i])
//       rm_container_name = container_name[i]
//       rm_image_name = image_name[i]
//       sshCommand remote: remote, command: "docker rm -f ${rm_container_name}", sudo: true
//       sshCommand remote: remote, command: "docker rmi -f ${rm_image_name}", sudo: true
//      }
//      sshCommand remote: remote, command: "docker image prune -a -f", sudo: true
//      sshCommand remote: remote, command: "rm -rf /opt/web/*", sudo: true
//  }
//}
def run_ansible_web(host){
  git credentialsId: 'ldap_jenkins_ci', branch: "master", url: 'ssh://git@git.oz.by:7999/oz/devops.git'

  stage("ansible config web"){
    dir("ansible"){
      ansiColor('xterm') {
      ansiblePlaybook(
          playbook: '03-critical.yaml',
          inventory: "inventories/dev/hosts",
          limit: "${host}",
          tags: 'web_dev',
          colorized: true)
      }
    }
  }
}

def run_uploadconf(host){
  withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
      def remote = [:]
      remote.user = "jenkins"
      remote.identityFile = sshKey
      remote.port = 22
      remote.name = "${host}"
      remote.host = "${host}"
      remote.allowAnyHosts = true
      sshCommand remote: remote, command: "/opt/web/uploadconf.sh", sudo: true
      sshCommand remote: remote, command: "chown -R www-data:www-data /mnt/", sudo: true
  }
}

def сhange_own_mnt(host){
  stage("Change owner /mnt"){
    withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
        def remote = [:]
        remote.user = "jenkins"
        remote.identityFile = sshKey
        remote.port = 22
        remote.name = "${host}"
        remote.host = "${host}"
        remote.allowAnyHosts = true
        sshCommand remote: remote, command: "chown -R www-data:www-data /mnt", sudo: true
    }
  }
}

def prepare_web_root_dir(project_name, git_url, branch, ozcode, prefix){
  dir("$project_name"){
    git credentialsId: 'ssh-jenkins-ci-user', url: "$git_url", branch: "$branch"
    sh("""git branch --set-upstream-to=origin/${branch} ${branch}""")


    withCredentials([sshUserPrivateKey(credentialsId: 'ssh-jenkins-ci-user', keyFileVariable: 'SSH_KEY')]) {
      sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --init'

      if ("$project_name" == "auth"){
        sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        sh("""cd includes/classes/client; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZAUTH_submodule_Client}""")
      } else if ("$project_name" == "ozby"){
        sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        // sh("""cd includes/classes/Shared; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZBY_submodule_Shared}""")
        // sh("""cd includes/classes/Zend; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZBY_submodule_Zend}""")
        sh("""cd includes/classes/client; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZBY_submodule_Client}""")
      } else if ("$project_name" == "ozapi"){
        sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        sh("""cd ozcode; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZAPI_submodule_ozby}""")
      } else if ("$project_name" == "ozstorage"){
        sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        sh("""cd ozcode; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZSTORAGE_submodule_ozby}""")
      } else if ("$project_name" == "ozadmapi"){
        sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        sh("""cd ozcode; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZADMAPI_submodule_ozby}""")
      }

      if ("$ozcode" == "ozcode"){
        dir("ozcode"){
          sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --init'
          if ("$project_name" == "ozapi"){
              //sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
              // sh("""cd includes/classes/Zend; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZAPI_submodule_ozby_submodule_Zend}""")
              //sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
              sh("""cd includes/classes/client; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZAPI_submodule_ozby_submodule_Client}""")
              //sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
              // sh("""cd includes/classes/Shared; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZAPI_submodule_ozby_submodule_Shared}""")
          } else if ("$project_name" == "ozstorage"){
              //sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
              // sh("""cd includes/classes/Zend; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZSTORAGE_submodule_ozby_submodule_Zend}""")
              //sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
              sh("""cd includes/classes/client; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZSTORAGE_submodule_ozby_submodule_Client}""")
              //sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
              // sh("""cd includes/classes/Shared; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZSTORAGE_submodule_ozby_submodule_Shared}""")
          } else if ("$project_name" == "ozadmapi"){
              //sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
              // sh("""cd includes/classes/Zend; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZADMAPI_submodule_ozby_submodule_Zend}""")
              //sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
              sh("""cd includes/classes/client; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZADMAPI_submodule_ozby_submodule_Client}""")
              //sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
              // sh("""cd includes/classes/Shared; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${OZADMAPI_submodule_ozby_submodule_Shared}""")
          }
          //sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        }
      }
      sh("""GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git config --global --add safe.directory '*' """)
    }
  }
}

//************** MAIN
//
try{
node("ansible"){
  stage("Prepare"){
    selectServer=sh(script: "dig +short oz${dev_list}.loc", returnStdout: true).trim()
    check_job.run_job_file("${dev_list}", selectServer, "job_check")
    echo "${selectServer}"
  }
}

node("srv-jenkins-agent-1") {
  run_www_data("${selectServer}")
  clear_www_data("${selectServer}")
}

node("ansible"){
  run_ansible_web("${selectServer}")
}

node("srv-jenkins-agent-1") {
  stage("Run upload uploadconf.sh"){
    run_uploadconf("${selectServer}")
    сhange_own_mnt("${selectServer}")
  }
  stage("Clear source job"){
    sh("pwd; rm -rf *")
  }

  stage("Prepare web auth"){
    prepare_web_root_dir("auth", "ssh://git@git.oz.by:7999/oz/auth.git", "${OZAUTH}", "empty", "empty")
    replace_docker_compose("auth", "${dev_list}")
    rsync_web_root("auth","/opt/web", "${selectServer}")
    run_docker_compose("auth", "${selectServer}")
  }

  stage("Prepare web ozby"){
    if ("${OZBY}" == "master") {
      stage("Check nginx"){
        withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
            def remote = [:]
            remote.user = "jenkins"
            remote.identityFile = sshKey
            remote.port = 22
            remote.name = "${selectServer}"
            remote.host = "${selectServer}"
            remote.allowAnyHosts = true
            sshCommand remote: remote, command: "systemctl stop nginx | exit 0; systemctl disable nginx | exit 0", sudo: true
        }
      }
    }
    prepare_web_root_dir("ozby", "ssh://git@git.oz.by:7999/oz/ozby.git", "${OZBY}", "empty", "empty")
    replace_docker_compose("ozby", "${dev_list}")
    rsync_web_root("ozby","/opt/web", "${selectServer}")
    run_docker_compose("ozby", "${selectServer}")
  }

  stage("Prepare web ozapi"){
    prepare_web_root_dir("ozapi", "ssh://git@git.oz.by:7999/oz/ozapi.git", "${OZAPI}", "ozcode", "OZAPI")
    replace_docker_compose("ozapi", "${dev_list}")
    rsync_web_root("ozapi","/opt/web", "${selectServer}")
    run_docker_compose("ozapi", "${selectServer}")
  }

  stage("Prepare web ozadmapi"){
    prepare_web_root_dir("ozadmapi", "ssh://git@git.oz.by:7999/oz/ozadmapi.git", "${OZADMAPI}", "ozcode", "OZADMAPI")
    replace_docker_compose("ozadmapi", "${dev_list}")
    rsync_web_root("ozadmapi","/opt/web", "${selectServer}")
    run_docker_compose("ozadmapi", "${selectServer}")
  }

  stage("Prepare web ozstorage"){
    prepare_web_root_dir("ozstorage", "ssh://git@git.oz.by:7999/oz/ozstorage.git", "${OZSTORAGE}", "ozcode", "OZSTORAGE")
    replace_docker_compose("ozstorage", "${dev_list}")
    rsync_web_root("ozstorage","/opt/web", "${selectServer}")
    run_docker_compose("ozstorage", "${selectServer}")
  }

  run_www_data("${selectServer}")

  stage("Migrate DB"){
    if(migrate_db == "true") {
      withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
          def remote = [:]
          remote.user = "jenkins"
          remote.identityFile = sshKey
          remote.port = 22
          remote.name = "${selectServer}"
          remote.host = "${selectServer}"
          remote.allowAnyHosts = true
          sshCommand remote: remote, command: "sudo docker exec php-fpm-admin php bin/console migrate -n --allow-no-migration --all-or-nothing", sudo: true
      }
    }
  }

  stage("ACL run"){
    if(acl_run == 'true') {
      withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
          def remote = [:]
          remote.user = "jenkins"
          remote.identityFile = sshKey
          remote.port = 22
          remote.name = "${selectServer}"
          remote.host = "${selectServer}"
          remote.allowAnyHosts = true
          sshCommand remote: remote, command: "sudo docker exec php-fpm-admin php bin/console acl:run ${tasknum}", sudo: true
      }
    }
  }

  stage("Custom script run"){
    if(script_run == 'true') {
      withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
          def remote = [:]
          remote.user = "jenkins"
          remote.identityFile = sshKey
          remote.port = 22
          remote.name = "${selectServer}"
          remote.host = "${selectServer}"
          remote.allowAnyHosts = true
          sshCommand remote: remote, command: "sudo docker exec php-fpm-admin php bin/console script:run ${tasknum}", sudo: true
      }
    }
  }

  stage("Clear log"){
    if(clearlog == 'true') {
      withCredentials([sshUserPrivateKey(credentialsId: "ssh-jenkins-ci-user", keyFileVariable: 'sshKey')]) {
          def remote = [:]
          remote.user = "jenkins"
          remote.identityFile = sshKey
          remote.port = 22
          remote.name = "${selectServer}"
          remote.host = "${selectServer}"
          remote.allowAnyHosts = true
          sshCommand remote: remote, command: "sudo docker exec php-fpm-admin php /home/www/oz.by/public_html/convert/clearlog.php", sudo: true
      }
    }
  }
}
}finally {
  echo "Finally run selectServer=$selectServer dev_list=$dev_list"
  node("ansible"){
    check_job.stop_job_file("${dev_list}", selectServer, "job_check")
  }
}
