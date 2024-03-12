Example ActiveChoiseParametr

import jenkins.model.*
credentialsId = 'ldap_jenkins_ci'
def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
  com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class, Jenkins.instance, null, null ).find{it.id == credentialsId}
username = creds.username
password = creds.password

def gettags = ("git ls-remote -t -h http://${username}:${password}@bitbucket.oz.loc/scm/oz/ozby.git").execute()
branch_list = gettags.text.readLines().collect { it.split()[1].replaceAll('refs/heads/', '').replaceAll('refs/tags/', '').replaceAll("\\^\\{\\}", '')}
List list_return = branch_list
list_return.add(0, "master")
return list_return


OZBY
master
OZBY_submodule_Zend
master
OZBY_submodule_Client
master
OZBY_submodule_Shared

OZAUTH
OZAUTH_submodule_Client

OZAPI
OZAPI_submodule_ozby
OZAPI_submodule_ozby_submodule_Zend
OZAPI_submodule_ozby_submodule_Client
OZAPI_submodule_ozby_submodule_Shared

master
OZSTORAGE
OZSTORAGE_submodule_ozby
OZSTORAGE_submodule_ozby_submodule_Zend
OZSTORAGE_submodule_ozby_submodule_Client
OZSTORAGE_submodule_ozby_submodule_Shared

master
OZADMAPI
OZADMAPI_submodule_ozby
OZADMAPI_submodule_ozby_submodule_Zend
OZADMAPI_submodule_ozby_submodule_Client
OZADMAPI_submodule_ozby_submodule_Shared
