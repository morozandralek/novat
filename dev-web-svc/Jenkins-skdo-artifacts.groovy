def nexus_repo = "https://docker.ozalliance.by"
def now = new Date()
def currentDate = now.format("yyyyMMdd")
def var_replace_mobile = "${replace_mobile}"
def var_replace_desktop = "${replace_desktop}"




def docker_build_artifact(type, currentDate, build_id){
  stage("Build and upload artifact ${type}") {
    dir(type){
      artifact_name="${type}-${currentDate}-${build_id}"
      dockerImage = docker.build("${artifact_name}", "-f Dockerfile-artifacts --no-cache .")
      sh """id=\$(docker create ${artifact_name}); docker cp \$id:/app/dist  - > ${artifact_name}.tar; docker rm -v \$id; docker rmi -f ${artifact_name}"""
      withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'ldap_jenkins_ci', usernameVariable: 'CI_USER', passwordVariable: 'CI_PASSWORD']]) {
        sh "curl --upload-file ${artifact_name}.tar -u ${CI_USER}:${CI_PASSWORD} https://nexus.ozalliance.by/repository/skdo-${type}/${artifact_name}.tar"
      }
    }
  }
}

def git_clone(type){
  stage("Git clone ${type}"){
      sh("rm -rf *")
      withCredentials([sshUserPrivateKey(credentialsId: 'ssh-jenkins-ci-user', keyFileVariable: 'SSH_KEY')]) {
        //git branch: "changes-from-ozalliance", credentialsId: 'ssh-jenkins-ci-user', url: "git@git.skdo.space:ozby/${type}.git"
        sh """GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git clone git@git.skdo.space:ozby/${type}.git"""
        dir(type){
          //sh """GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git branch --set-upstream-to=origin/changes-from-ozalliance changes-from-ozalliance"""
          sh """GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git config --global user.email "devops@oz.by"; git config --global user.name "Andrey Moroz" """
          sh """GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git checkout changes-from-ozalliance"""
          sh 'GIT_SSH_COMMAND="ssh -i $SSH_KEY -o StrictHostKeyChecking=no" git rebase main'
        }
      }
  }
}


node("srv-jenkins-agent-1"){

  if (var_replace_mobile == "true"){
    git_clone('mobile')
    docker_build_artifact('mobile',currentDate,"${BUILD_ID}")
  } else if (var_replace_mobile == "false"){
    print("Mobile artifact not selected")
  }

  if (var_replace_desktop == "true"){
    git_clone('desktop')
    docker_build_artifact('desktop',currentDate,"${BUILD_ID}")
  } else if (var_replace_desktop == "false"){
    print("Desktop artifact not selected")
  }

}
