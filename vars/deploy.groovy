import com.freebyTech.BuildInfo
import com.freebyTech.BuildConstants
import com.freebyTech.ContainerLabel

void call(BuildInfo buildInfo, String repository, String imageName, Boolean purgePrevious = false, String hostUrl) 
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
                    withEnv(["APPVERSION=${buildInfo.version}", "VERSION=${buildInfo.semanticVersion}", "REPOSITORY=${repository}", "IMAGE_NAME=${imageName}", "HELM_EXPERIMENTAL_OCI=1", "HOST_URL=${hostUrl}"])
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
                                helm install --namespace ${NAMESPACE} ${NAMESPACE}-${IMAGE_NAME} --version ${VERSION} --set image.tag=${APPVERSION} \
                                    --set image.repository=${REGISTRY_URL}/${REPOSITORY}/${IMAGE_NAME} --set ingress.hosts={ '${HOST_URL}' } \
                                    --set ingress.tls.hosts={ '${HOST_URL}' } --set virtualService.hosts { '${HOST_URL}' } --debug .
                                set -e
                                '''
                            } 
                            else 
                            {
                                sh '''
                                echo ${REGISTRY_USER_PASSWORD} | helm registry login ${REGISTRY_URL} --username ${REGISTRY_USER} --password-stdin
                                helm chart pull ${REGISTRY_URL}/${REPOSITORY}-helm/${IMAGE_NAME}:${APPVERSION}
                                helm chart export ${REGISTRY_URL}/${REPOSITORY}-helm/${IMAGE_NAME}:${APPVERSION} --destination ./deploy
                                cd ./deploy/${IMAGE_NAME}
                                set +e
                                helm upgrade --install --namespace ${NAMESPACE} ${NAMESPACE}-${IMAGE_NAME} --version ${VERSION} --set image.tag=${APPVERSION} \
                                    --set image.repository=${REGISTRY_URL}/${REPOSITORY}/${IMAGE_NAME} --set ingress.hosts={ '${HOST_URL}' } \
                                    --set ingress.tls.hosts={ '${HOST_URL}' } --set virtualService.hosts { '${HOST_URL}' } --debug .
                                set -e
                                '''
                            }
                        }
                    }
                }
            }
        }
    }
}