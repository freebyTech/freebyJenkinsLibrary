import com.freebyTech.BuildInfo
import com.freebyTech.BuildConstants
import com.freebyTech.NugetPushOptionEnum

BuildInfo call(def script, String versionPrefix, String repository, String imageName, String dockerBuildArguments, Boolean registryPublish, Boolean helmBuildChart, NugetPushOptionEnum nugetPushOption = NugetPushOptionEnum.NoPush, String nugetPackageId = '') 
{
    BuildInfo buildInfo = new BuildInfo(steps, script)

    String label = "worker-${UUID.randomUUID().toString()}"
    
    buildInfo.determineBuildInfo(versionPrefix, repository, imageName)
    
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
                echo "Building version ${buildInfo.version} for branch ${env.BRANCH_NAME}"
                echo '--------------------------------------------------'          
                currentBuild.displayName = "# " + buildInfo.version
            }

            stage('Build Image and Publish') 
            {
                container('freeby-agent') 
                {
                    checkout scm

                    // Use guid of known user for registry security
                    docker.withRegistry(buildInfo.registry, env.REGISTRY_USER_ID) 
                    {
                        def img
                        if(dockerBuildArguments=='') 
                        {
                            img = docker.build(buildInfo.tag, "--build-arg BUILD_VERSION=${buildInfo.version} PACKAGE_ID=${nugetPackageId} ./src")
                        }
                        else 
                        {
                            img = docker.build(buildInfo.tag,"--build-arg BUILD_VERSION=${buildInfo.version} PACKAGE_ID=${nugetPackageId} ${dockerBuildArguments} ./src")
                        }
                        if(registryPublish) {
                            img.push()
                            if("develop".equalsIgnoreCase(env.BRANCH_NAME)) 
                            {
                                img.push('latest')
                            }
                        }
                        //TODO: In the future support -s and -ss options for private nuget server.
                        if(nugetPushOption == NugetPushOptionEnum.PushRelease) {
                            img.inside {
                                sh 'cd /lib/nuget'
                                sh "dotnet nuget push ${nugetPackageId}.${buildInfo.version}"
                            }
                        }
                        else if(nugetPushOption == NugetPushOptionEnum.PushRelease) {
                            img.inside {

                            }
                        }             
                    }

                    if(helmBuildChart) 
                    {
                        withEnv(["APPVERSION=${buildInfo.version}", "VERSION=${buildInfo.semanticVersion}", "REPOSITORY=${repository}", "IMAGE_NAME=${imageName}"])
                        {
                            // Need registry credentials for agent build operation to setup chart museum connection.
                            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: env.REGISTRY_USER_ID,
                            usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_USER_PASSWORD']])
                            {
                                sh '''
                                helm init --client-only
                                helm plugin install https://github.com/chartmuseum/helm-push
                                helm repo add --username ${REGISTRY_USER} --password ${REGISTRY_USER_PASSWORD} $REPOSITORY https://${REGISTRY_URL}/chartrepo/${REPOSITORY}
                                helm package --app-version ${APPVERSION} --version $VERSION ./deploy/${IMAGE_NAME}
                                helm push ${IMAGE_NAME}-${VERSION}.tgz ${REPOSITORY}
                                '''
                            }
                        }
                    }
                }
            }         
        }
    }
    return buildInfo
}