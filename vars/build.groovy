import com.freebyTech.BuildInfo
import com.freebyTech.BuildConstants
import com.freebyTech.NugetPushOptionEnum
import com.freebyTech.ContainerLabel

BuildInfo call(def script, String versionPrefix, String repository, String imageName, String extraDockerBuildArguments, Boolean registryPublish, Boolean helmBuildChart = false, NugetPushOptionEnum nugetPushOption = NugetPushOptionEnum.NoPush, String nugetPackageId = '', String dockerFileLocation = './src', String overrideHelmDirectory = '', String extraSHCommands = '') 
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
                container('freeby-agent')
                {
                    checkout scm
                    buildInfo.checkForVersionOverrideTags(versionPrefix, repository, imageName)
                    echo '--------------------------------------------------'
                    echo "Building version ${buildInfo.version} for branch ${env.BRANCH_NAME}"
                    echo '--------------------------------------------------'          
                    currentBuild.displayName = "# " + buildInfo.version
                    buildInfo.pushTag()
                }
            }

            stage("Build Image and Publish - ${env.BRANCH_NAME}") 
            {
                container('freeby-agent') 
                {
                    checkout scm

                    if(extraSHCommands != '')
                    {
                        dir('.') {
                            sh extraSHCommands.replace('${BUILD_VERSION}', "${buildInfo.version}")
                        }                                   
                    }

                    // Use guid of known user for registry security
                    docker.withRegistry(buildInfo.registry, env.REGISTRY_USER_ID) 
                    {
                        def img
                        if(extraDockerBuildArguments=='') 
                        {
                            img = docker.build(buildInfo.tag, "--build-arg BUILD_VERSION=${buildInfo.version} --build-arg PACKAGE_ID=${nugetPackageId} ${dockerFileLocation}")
                        }
                        else 
                        {
                            img = docker.build(buildInfo.tag,"--build-arg BUILD_VERSION=${buildInfo.version} --build-arg PACKAGE_ID=${nugetPackageId} ${extraDockerBuildArguments} ${dockerFileLocation}")
                        }
                        if(registryPublish) {
                            img.push()
                            if("develop".equalsIgnoreCase(env.BRANCH_NAME)) 
                            {
                                img.push('latest')
                            }
                        }    
                    }

                    if(helmBuildChart) 
                    {
                        def helmDir = imageName
                        if(overrideHelmDirectory != '') 
                        {
                            helmDir = overrideHelmDirectory
                        }

                        withEnv(["APPVERSION=${buildInfo.version}", "VERSION=${buildInfo.semanticVersion}", "REPOSITORY=${repository}", "IMAGE_NAME=${imageName}", "HELM_EXPERIMENTAL_OCI=1", "HELM_DIR=${helmDir}"])
                        {
                            // Need registry credentials for agent build operation to setup chart museum connection.
                            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: env.REGISTRY_USER_ID,
                                    usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_USER_PASSWORD']])
                            {
                                sh '''
                                cd ./deploy/${HELM_DIR}
                                sed -i \"s/1.16.0/${APPVERSION}/g\" ./Chart.yaml
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
    // Since we can't get docker in docker to function in new GKE cluster with newer Jenkins, spinning up another pod seems the appropriate response.
    if(nugetPushOption == NugetPushOptionEnum.PushRelease || nugetPushOption == NugetPushOptionEnum.PushDebug) {
        label = new ContainerLabel("publish", imageName).label
        podTemplate( label: label,
            containers: 
            [
                containerTemplate(name: 'nuget-agent', image: buildInfo.tag, ttyEnabled: true, command: 'cat')
            ])
        {
            node(label) 
            {
                stage("Publish Nuget Package - ${env.BRANCH_NAME}") 
                {
                    container('nuget-agent') 
                    {
                        withEnv(["NUGET_API=${env.NUGET_API_KEY}", "PACKAGE_ID=${nugetPackageId}", "VERSION=${buildInfo.version}"])
                        {
                            //TODO: In the future support -s options for private nuget server?
                            if(nugetPushOption == NugetPushOptionEnum.PushRelease) {
                                sh '''
                                    set +x
                                    dotnet nuget push /lib/nuget/$PACKAGE_ID.$VERSION.nupkg -k $NUGET_API -s https://api.nuget.org/v3/index.json
                                    set -x
                                '''
                            }
                            else if(nugetPushOption == NugetPushOptionEnum.PushDebug) {
                                sh '''
                                    set +x
                                    dotnet nuget push /lib/nuget_d/$PACKAGE_ID.$VERSION.nupkg -k $NUGET_API -s https://api.nuget.org/v3/index.json
                                    set -x
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
