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
                                echo ${REGISTRY_USER_PASSWORD} | helm registry login ${REGISTRY_URL} --username ${REGISTRY_USER} --password-stdin
                                helm chart pull ${REGISTRY_URL}/${REPOSITORY}-helm/${IMAGE_NAME}:${APPVERSION}
                                helm chart export ${REGISTRY_URL}/${REPOSITORY}-helm/${IMAGE_NAME}:${APPVERSION} --destination ./deploy
                                cd ./deploy/${IMAGE_NAME}
                                set +e
                                helm delete --namespace ${NAMESPACE}
                                set -e
                                helm install --namespace ${NAMESPACE} ${NAMESPACE}-${IMAGE_NAME} --version ${VERSION} --set image.tag=${APPVERSION} .
                                '''
                            } 
                            else 
                            {
                                sh '''
                                echo ${REGISTRY_USER_PASSWORD} | helm registry login ${REGISTRY_URL} --username ${REGISTRY_USER} --password-stdin
                                helm chart pull ${REGISTRY_URL}/${REPOSITORY}-helm/${IMAGE_NAME}:${APPVERSION}
                                helm chart export ${REGISTRY_URL}/${REPOSITORY}-helm/${IMAGE_NAME}:${APPVERSION} --destination ./deploy
                                cd ./deploy/${IMAGE_NAME}
                                helm upgrade --install --namespace ${NAMESPACE} ${NAMESPACE}-${IMAGE_NAME} --version ${VERSION} --set image.tag=${APPVERSION} .
                                '''
                            }
                        }
                    }
                }
            }
        }
    }
}