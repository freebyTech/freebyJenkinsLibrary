import com.freebyTech.BuildInfo
import com.freebyTech.BuildConstants
import com.freebyTech.NugetPushOptionEnum
import com.freebyTech.ContainerLabel

BuildInfo call(def script, String versionPrefix, String repository, String imageName, String extraDockerBuildArgument, Boolean registryPublish, Boolean helmBuildChart = false, NugetPushOptionEnum nugetPushOption = NugetPushOptionEnum.NoPush, String nugetPackageId = '') 
{
    BuildInfo buildInfo = new BuildInfo(steps, script)

    String label = new ContainerLabel("build", imageName).label
    
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
                        if(extraDockerBuildArgument=='') 
                        {
                            img = docker.build(buildInfo.tag, "--build-arg BUILD_VERSION=${buildInfo.version} --build-arg PACKAGE_ID=${nugetPackageId} ./src")
                        }
                        else 
                        {
                            img = docker.build(buildInfo.tag,"--build-arg BUILD_VERSION=${buildInfo.version}--build-arg PACKAGE_ID=${nugetPackageId} --build-arg ${extraDockerBuildArgument} ./src")
                        }
                        if(registryPublish) {
                            img.push()
                            if("develop".equalsIgnoreCase(env.BRANCH_NAME)) 
                            {
                                img.push('latest')
                            }
                        }
                        withEnv(["NUGET_API=${env.NUGET_API_KEY}", "PACKAGE_ID=${nugetPackageId}", "VERSION=${buildInfo.version}"])
                        {
                            //TODO: In the future support -s options for private nuget server?
                            if(nugetPushOption == NugetPushOptionEnum.PushRelease) {
                                img.inside {
                                    sh '''
                                    set +x
                                    dotnet nuget push /lib/nuget/$PACKAGE_ID.$VERSION.nupkg -k $NUGET_API -s https://api.nuget.org/v3/index.json
                                    set -x
                                    '''
                                }
                            }
                            else if(nugetPushOption == NugetPushOptionEnum.PushDebug) {
                                img.inside {
                                    sh '''
                                    set +x
                                    dotnet nuget push /lib/nuget_d/$PACKAGE_ID.$VERSION.nupkg -k $NUGET_API -s https://api.nuget.org/v3/index.json
                                    set -x
                                    '''
                                }
                            }
                        }             
                    }

                    if(helmBuildChart) 
                    {
                        withEnv(["APPVERSION=${buildInfo.version}", "VERSION=${buildInfo.semanticVersion}", "REPOSITORY=${repository}", "IMAGE_NAME=${imageName}", "HELM_EXPERIMENTAL_OCI=1"])
                        {
                            // Need registry credentials for agent build operation to setup chart museum connection.
                            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: env.REGISTRY_USER_ID,
                                    usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_USER_PASSWORD']])
                            {
                                sh '''
                                cd ./deploy/${IMAGE_NAME}
                                helm chart save . ${REGISTRY_URL}/${REPOSITORY}-helm/${IMAGE_NAME}:${APPVERSION}
                                echo ${REGISTRY_USER_PASSWORD} | helm registry login ${REGISTRY_URL} --username ${REGISTRY_USER} --password-stdin
                                helm chart push ${REGISTRY_URL}/${REPOSITORY}-helm/${IMAGE_NAME}:${APPVERSION}
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
