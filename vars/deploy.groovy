import com.freebyTech.BuildInfo
import com.freebyTech.BuildConstants
import com.freebyTech.ContainerLabel

void call(BuildInfo buildInfo, String repository, String imageName, Boolean purgePrevious = false) 
{
    String label = new ContainerLabel("deploy", imageName).label
    
    podTemplate( label: label,
        containers: 
        [
            containerTemplate(name: 'freeby-agent', image: buildInfo.agentTag, ttyEnabled: true, command: 'cat')
        ], 
        volumes: 
        [
            hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')
        ],
        serviceAccount: 'jenkins-builder')
    {
        node(label)
        {
            stage("Overwrite ${imageName}")
            {      
                container('freeby-agent') 
                {
                    withEnv(["APPVERSION=${buildInfo.version}", "VERSION=${buildInfo.semanticVersion}", "REPOSITORY=${repository}", "IMAGE_NAME=${imageName}"])
                    {
                        // Need registry credentials for agent build operation to setup chart museum connection.
                        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: env.REGISTRY_USER_ID,
                        usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_USER_PASSWORD']])
                        {
                            if(purgePrevious)
                            {
                                sh '''
                                helm init --client-only
                                helm plugin install https://github.com/chartmuseum/helm-push
                                helm repo add --username ${REGISTRY_USER} --password ${REGISTRY_USER_PASSWORD} ${REPOSITORY} https://${REGISTRY_URL}/chartrepo/${REPOSITORY}
                                set +e
                                helm delete ${NAMESPACE}-${IMAGE_NAME} --purge
                                set -e
                                helm upgrade --install --namespace ${NAMESPACE} ${NAMESPACE}-${IMAGE_NAME} $REPOSITORY/${IMAGE_NAME} --version ${VERSION} --set image.tag=${APPVERSION}
                                '''
                            } 
                            else 
                            {
                                sh '''
                                helm init --client-only
                                helm plugin install https://github.com/chartmuseum/helm-push
                                helm repo add --username ${REGISTRY_USER} --password ${REGISTRY_USER_PASSWORD} ${REPOSITORY} https://${REGISTRY_URL}/chartrepo/${REPOSITORY}
                                helm upgrade --install --namespace ${NAMESPACE} ${NAMESPACE}-${IMAGE_NAME} $REPOSITORY/${IMAGE_NAME} --version ${VERSION} --set image.tag=${APPVERSION}
                                '''
                            }
                        }
                    }
                }
            }
        }
    }
}