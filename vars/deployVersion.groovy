import com.freebyTech.BuildInfo
import com.freebyTech.BuildConstants
import com.freebyTech.ContainerLabel

void call(def script, String version, String repository, String imageName, String ns, Boolean purgePrevious = false) 
{
    BuildInfo buildInfo = new BuildInfo(steps, script)

    String label = new ContainerLabel("deployv", imageName).label
    
    buildInfo.determineBuildInfoFromPassedVersion(version, repository, imageName)

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
            stage("Deploy ${imageName}")
            {      
                container('freeby-agent') 
                {
                    withEnv(["APPVERSION=${buildInfo.version}", "VERSION=${buildInfo.semanticVersion}", "REPOSITORY=${repository}", "IMAGE_NAME=${imageName}", "NS=${ns}"])
                    {
                        // Need registry credentials for agent build operation to setup chart museum connection.
                        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: env.REGISTRY_USER_ID,
                        usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_USER_PASSWORD']])
                        {
                            // Can be necessary if previous label selectors had version information in them.
                            if(purgePrevious)
                            {
                                sh '''
                                helm init --client-only
                                helm plugin install https://github.com/chartmuseum/helm-push
                                helm repo add --username ${REGISTRY_USER} --password ${REGISTRY_USER_PASSWORD} ${REPOSITORY} https://${REGISTRY_URL}/chartrepo/${REPOSITORY}
                                helm delete ${NS}-${IMAGE_NAME} --purge
                                helm upgrade --install --namespace ${NS} ${NS}-${IMAGE_NAME} $REPOSITORY/${IMAGE_NAME} --version ${VERSION} --set image.tag=${APPVERSION}
                                '''
                            }
                            else
                            {
                                sh '''
                                helm init --client-only
                                helm plugin install https://github.com/chartmuseum/helm-push
                                helm repo add --username ${REGISTRY_USER} --password ${REGISTRY_USER_PASSWORD} ${REPOSITORY} https://${REGISTRY_URL}/chartrepo/${REPOSITORY}
                                helm upgrade --install --namespace ${NS} ${NS}-${IMAGE_NAME} $REPOSITORY/${IMAGE_NAME} --version ${VERSION} --set image.tag=${APPVERSION}
                                '''
                            }
                        }
                    }
                }
            }
        }
    }
}