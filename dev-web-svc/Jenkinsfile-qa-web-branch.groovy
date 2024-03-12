def INPUT_JIRA_TASK = "${INPUT_JIRA_TASK}"

def oz_by      = ""
def oz_auth    = ""
def oz_api     = ""
def oz_admapi  = ""
def oz_storage = ""

def shared = ""
def zend   = ""
def client = ""


def search_project_branch(project_name, repo_url, inputJiraTask){
  lower_inputJiraTask = inputJiraTask.toLowerCase()
  returnBranchName = ""
  withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'ldap_jenkins_ci', usernameVariable: 'CI_USER', passwordVariable: 'CI_PASSWORD']]) {
    def gettags = ("git ls-remote -t -h http://${CI_USER}:${CI_PASSWORD}@${repo_url}").execute()
    branch_list = gettags.text.readLines().collect { it.split()[1].replaceAll('refs/heads/', '').replaceAll('refs/tags/', '').replaceAll("\\^\\{\\}", '')}
    List list_return = branch_list

    countJiraTask = 0
    for (branch in list_return) {
      lower_branch = branch.toLowerCase()
      if (lower_branch.contains(lower_inputJiraTask)){
        print(branch)
        countJiraTask = countJiraTask + 1
        returnBranchName = branch
      }
    }

    if (lower_inputJiraTask == "master"){
      print("Return ${project_name} branch name master")
      return "master"
    } else if (countJiraTask > 1){
      print("${inputJiraTask} found branch > 1 counter_checl=${countJiraTask} Return ${project_name} master")
      currentBuild.description = "ABORTED Stopping found branch > 1"
      currentBuild.result = 'ABORTED'
      error('Stopping found branch > 1')
    } else if (countJiraTask == 1){
      print("Return ${project_name} branch name = ${returnBranchName}")
      return returnBranchName
    } else {
      print("Return ${project_name} branch name master")
      return "master"
    }

  }
}


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
        sshCommand remote: remote, command: "sudo docker-compose -f /opt/web/ozby/docker-compose.yaml stop| exit 0", sudo: true
        sshCommand remote: remote, command: "rm -rf /opt/web/*", sudo: true
    }
  }
}

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

def prepare_web_root_dir(project_name, git_url, branch, ozcode, shared_branch, zend_branch, client_branch, ozby_branch ){
  dir("$project_name"){
    git credentialsId: 'ssh-jenkins-ci-user', url: "$git_url", branch: "$branch"
    sh("""git branch --set-upstream-to=origin/${branch} ${branch}""")
    withCredentials([sshUserPrivateKey(credentialsId: 'ssh-jenkins-ci-user', keyFileVariable: 'SSH_KEY')]) {
      sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --init'

      if ("$project_name" == "auth"){
        sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        sh("""cd includes/classes/client; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${client_branch}""")
      } else if ("$project_name" == "ozby"){
        sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        // sh("""cd includes/classes/Shared; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${shared_branch}""")
        // sh("""cd includes/classes/Zend; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${zend_branch}""")
        sh("""cd includes/classes/client; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${client_branch}""")
      } else if ("$project_name" == "ozapi"){
        sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        sh("""cd ozcode; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${ozby_branch}""")
      } else if ("$project_name" == "ozstorage"){
        sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        sh("""cd ozcode; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${ozby_branch}""")
      } else if ("$project_name" == "ozadmapi"){
        sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --remote'
        sh("""cd ozcode; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${ozby_branch}""")
      }

      if ("$ozcode" == "ozcode"){
        dir("ozcode"){
          sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git submodule update --init'
          if ("$project_name" == "ozapi"){
              // sh("""cd includes/classes/Zend; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${zend_branch}""")
              sh("""cd includes/classes/client; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${client_branch}""")
              // sh("""cd includes/classes/Shared; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${shared_branch}""")
          } else if ("$project_name" == "ozstorage"){
              // sh("""cd includes/classes/Zend; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${zend_branch}""")
              sh("""cd includes/classes/client; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${client_branch}""")
              // sh("""cd includes/classes/Shared; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${shared_branch}""")
          } else if ("$project_name" == "ozadmapi"){
              // sh("""cd includes/classes/Zend; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${zend_branch}""")
              sh("""cd includes/classes/client; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${client_branch}""")
              // sh("""cd includes/classes/Shared; GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout ${shared_branch}""")
          }
        }
      }
      sh("""GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git config --global --add safe.directory '*' """)
    }
  }
}
//-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// MAIN
//-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

try{
node("ansible"){
  stage("Prepare"){
    selectServer=sh(script: "dig +short oz${dev_list}.loc", returnStdout: true).trim()
    echo "${selectServer}"
    currentBuild.displayName = "${BUILD_NUMBER}-dev${dev_list}"
    check_job.run_job_file("${dev_list}", selectServer, "job_check")
  }
}

node("srv-jenkins-agent-1") {
  stage("Fill list branch All project"){
    oz_by = search_project_branch("oz_by","bitbucket.oz.loc/scm/oz/ozby.git", INPUT_JIRA_TASK)
    oz_auth = search_project_branch("oz_auth","bitbucket.oz.loc/scm/oz/auth.git", INPUT_JIRA_TASK)
    oz_api = search_project_branch("oz_api","bitbucket.oz.loc/scm/oz/ozapi.git", INPUT_JIRA_TASK)
    oz_admapi = search_project_branch("oz_admapi","bitbucket.oz.loc/scm/oz/ozadmapi.git", INPUT_JIRA_TASK)
    oz_storage = search_project_branch("oz_storage","bitbucket.oz.loc/scm/oz/ozstorage.git", INPUT_JIRA_TASK)

    shared = search_project_branch("shared","bitbucket.oz.loc/scm/oz/sh_shared.git", INPUT_JIRA_TASK)
    zend   = search_project_branch("zend","bitbucket.oz.loc/scm/oz/sh_zend.git", INPUT_JIRA_TASK)
    client = search_project_branch("client","bitbucket.oz.loc/scm/oz/sh_client.git", INPUT_JIRA_TASK)
    print ("""
    Show list branch setup
    oz_by      = ${oz_by}
    oz_auth    = ${oz_auth}
    oz_api     = ${oz_api}
    oz_admapi  = ${oz_admapi}
    oz_storage = ${oz_storage}

    shared = ${shared}
    zend   = ${zend}
    client = ${client}
    """)
    currentBuild.description = """Show list branch setup
    oz_by      = ${oz_by}
    oz_auth    = ${oz_auth}
    oz_api     = ${oz_api}
    oz_admapi  = ${oz_admapi}
    oz_storage = ${oz_storage}

    shared = ${shared}
    zend   = ${zend}
    client = ${client}
    """
  }
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
    prepare_web_root_dir("auth", "ssh://git@git.oz.by:7999/oz/auth.git", "${oz_auth}", "empty", shared, zend, client, oz_by)
    replace_docker_compose("auth", "${dev_list}")
    rsync_web_root("auth","/opt/web", "${selectServer}")
    run_docker_compose("auth", "${selectServer}")
  }

  stage("Prepare web ozby"){
    prepare_web_root_dir("ozby", "ssh://git@git.oz.by:7999/oz/ozby.git", "${oz_by}", "empty", shared, zend, client, oz_by)
    replace_docker_compose("ozby", "${dev_list}")
    rsync_web_root("ozby","/opt/web", "${selectServer}")
    run_docker_compose("ozby", "${selectServer}")
  }

  stage("Prepare web ozapi"){
    prepare_web_root_dir("ozapi", "ssh://git@git.oz.by:7999/oz/ozapi.git", "${oz_api}", "ozcode", shared, zend, client, oz_by)
    replace_docker_compose("ozapi", "${dev_list}")
    rsync_web_root("ozapi","/opt/web", "${selectServer}")
    run_docker_compose("ozapi", "${selectServer}")
  }

  stage("Prepare web ozadmapi"){
    prepare_web_root_dir("ozadmapi", "ssh://git@git.oz.by:7999/oz/ozadmapi.git", "${oz_admapi}", "ozcode", shared, zend, client, oz_by)
    replace_docker_compose("ozadmapi", "${dev_list}")
    rsync_web_root("ozadmapi","/opt/web", "${selectServer}")
    run_docker_compose("ozadmapi", "${selectServer}")
  }

  stage("Prepare web ozstorage"){
    prepare_web_root_dir("ozstorage", "ssh://git@git.oz.by:7999/oz/ozstorage.git", "${oz_storage}", "ozcode", shared, zend, client, oz_by)
    replace_docker_compose("ozstorage", "${dev_list}")
    rsync_web_root("ozstorage","/opt/web", "${selectServer}")
    run_docker_compose("ozstorage", "${selectServer}")
  }

  stage("DB rsync"){
    script{
      if(db_rsync == "true"){
        build job: 'dev-db-rsync', parameters: [string(name: 'dev_list', value: "${dev_list}")]
      }
    }
  }

  stage("Sphinx index update"){
    script{
      if(sphinx_index_update == "true"){
        build job: 'dev-sphinx-index-update', parameters: [string(name: 'dev_list', value: "${dev_list}"), string(name: 'index_name', value: ""), string(name: 'update', value: "true"), string(name: 'index', value: "true"), string(name: 'flush_redis', value: "true"), string(name: 'restart_sphinx', value: "true")]
      }
    }
  }

  stage("Rabbit clear"){
    script{
       build job: 'dev-rabbit-clear', parameters: [string(name: 'dev_list', value: "${dev_list}")]
    }
  }

  stage("Operations on environment"){
    script{
       build job: 'dev-web-operations-on-environment', parameters: [string(name: 'dev_list', value: "${dev_list}"), string(name: 'vuejs_compilation', value: "true"), string(name: 'flush_redis', value: "true"), string(name: 'upload_config', value: "true"), string(name: 'version_increment', value: "true"), string(name: 'kill_php_processes', value: "true"), string(name: 'reset_cron_config', value: "true")]
    }
  }
}
}finally {
  echo "Finally run selectServer=$selectServer dev_list=$dev_list"
  node("ansible"){
    check_job.stop_job_file("${dev_list}", selectServer, "job_check")
  }
}
