import com.freebyTech.BuildInfo
import com.freebyTech.BuildConstants

BuildInfo call(def script, String versionPrefix, String repository, String image, String dockerBuildArguments, Boolean helmChartBuild) 
{
    BuildInfo buildInfo = new BuildInfo(steps, script)

    String label = "worker-${UUID.randomUUID().toString()}"
    
    buildInfo.determineBuildInfo(versionPrefix, repository, image)
    
    podTemplate( label: label,
        containers: 
        [
            containerTemplate(name: 'freeby-agent', image: buildInfo.agentTag, ttyEnabled: true, command: 'cat')
        ], 
        volumes: 
        [
            hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')
        ])
    {
        node(label) 
        {
            stage('Setup Build Settings') 
            {
                echo '--------------------------------------------------'
                echo "Building version ${buildInfo.version} for branch ${script.env.BRANCH_NAME}"
                echo '--------------------------------------------------'          
                currentBuild.displayName = "# " + buildInfo.version
            }

            stage('Build Image and Publish') 
            {
                container('freeby-agent') 
                {
                    checkout scm

                    // Use guid of known user for registry security
                    docker.withRegistry(buildInfo.registry, BuildConstants.REGISTRY_USER_GUID) 
                    {
                        def app
                        if(dockerBuildArguments=='') 
                        {
                            app = docker.build(buildInfo.tag, "./src")
                        }
                        else 
                        {
                            app = docker.build(buildInfo.tag,"--build-arg ${dockerBuildArguments} ./src")
                        }
                        app.push()
                        if("develop".equalsIgnoreCase(script.env.BRANCH_NAME)) 
                        {
                            app.push('latest')
                        }          
                    }

                    if(helmChartBuild) 
                    {
                        withEnv(["APPVERSION=${buildInfo.version}", "VERSION=${buildInfo.semanticVersion}", "REPOSITORY=${repository}", "IMAGE=${image}"])
                        {
                            // Need registry credentials for agent build operation to setup chart museum connection.
                            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: BuildConstants.REGISTRY_USER_GUID,
                            usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_USER_PASSWORD']])
                            {
                                sh '''
                                helm init --client-only
                                helm plugin install https://github.com/chartmuseum/helm-push
                                helm repo add --username ${REGISTRY_USER} --password ${REGISTRY_USER_PASSWORD} $REPOSITORY https://${REGISTRY_URL}/chartrepo/${REPOSITORY}
                                helm package --app-version ${APPVERSION} --version $VERSION ./deploy/${IMAGE}
                                helm push ${IMAGE}-${VERSION}.tgz ${REPOSITORY}
                                '''
                            }
                        }
                    }
                }
            }         
        }
    }
}