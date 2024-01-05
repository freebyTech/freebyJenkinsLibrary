package com.freebyTech

import com.freebyTech.BuildConstants

class BuildInfo implements Serializable {
    String version
    String semanticVersion
    String tag
    String agentTag
    String registry

    private def steps
    private def script

    BuildInfo(steps, script) 
    { 
        this.steps = steps
        this.script = script
    }

    def determineBuildInfo(String versionPrefix, String repository, String image) {
        def date = new Date()
        def verDate = date.format('MMdd')
        if(verDate.getAt(0) == '0') {
            verDate = verDate.substring(1)
        }
        //if(!script.env.BRANCH_NAME?.trim() && (script.env.BRANCH_NAME.equalsIgnoreCase("master") || script.env.BRANCH_NAME.equalsIgnoreCase("develop")) {
        this.version = "${versionPrefix}.${script.env.BUILD_NUMBER}.${verDate}"
        this.semanticVersion = "${versionPrefix}.${script.env.BUILD_NUMBER}"
        //}  

        // Standard Docker Registry or custom docker registry?
        if(BuildConstants.DEFAULT_DOCKER_REGISTRY.equalsIgnoreCase(script.env.REGISTRY_URL)) 
        {
            steps.echo 'Publishing to standard docker registry.'
            this.tag = "${repository}/${image}:${this.version}"
            this.agentTag = "freebytech-pub/${script.env.AGENT_IMAGE}"
            this.registry = ''
        }
        else 
        {
            steps.echo "Publishing to registry ${script.env.REGISTRY_URL}"
            this.tag = "${script.env.REGISTRY_URL}/${repository}/${image}:${this.version}"
            this.agentTag = "${script.env.REGISTRY_URL}/freebytech-pub/${script.env.AGENT_IMAGE}"
            this.registry = "https://${script.env.REGISTRY_URL}"
        }        
    }

    def determineBuildInfoFromPassedVersion(String version, String repository, String image) {
        this.version = version;
        this.semanticVersion = version.substring(0, version.lastIndexOf("."));
        //}  

        // Standard Docker Registry or custom docker registry?
        if(BuildConstants.DEFAULT_DOCKER_REGISTRY.equalsIgnoreCase(script.env.REGISTRY_URL)) 
        {
            steps.echo 'Publishing to standard docker registry.'
            this.tag = "${repository}/${image}:${this.version}"
            this.agentTag = "freebytech-pub/${script.env.AGENT_IMAGE}"
            this.registry = ''
        }
        else 
        {
            steps.echo "Publishing to registry ${script.env.REGISTRY_URL}"
            this.tag = "${script.env.REGISTRY_URL}/${repository}/${image}:${this.version}"
            this.agentTag = "${script.env.REGISTRY_URL}/freebytech-pub/${script.env.AGENT_IMAGE}"
            this.registry = "https://${script.env.REGISTRY_URL}"
        }        
    }

    def checkForVersionOverrideTags(String versionPrefix) {
        script.sh(script:"git config --global --add safe.directory ${script.pwd()}")
        script.sh(script:"git config --global user.email \"${script.env.GIT_USER_EMAIL}\"")
        script.sh(script:"git config --global user.name \"${script.env.GIT_USER_NAME}\"")
        def lastVersion = script.sh(returnStdout: true, script: "echo \$(git tag --sort=-creatordate -l 'v${versionPrefix}.*' | head -1)").trim()
        if (lastVersion.length() > 0) {
            steps.echo "Existing version tag ${lastVersion} found"
            lastVersion = lastVersion.substring(1);
            def lastVersionSplit = lastVersion.tokenize('.')
            def lastPatchVersion = lastVersionSplit[2]
            def date = new Date()
            def verDate = date.format('MMdd')
            if(verDate.getAt(0) == '0') {
                verDate = verDate.substring(1)
            }
            this.version = "${versionPrefix}.${lastPatchVersion}.${verDate}"
            this.semanticVersion = "${versionPrefix}.${lastPatchVersion}"
        }        
    }

    def pushTag() {
        script.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: script.env.PRIVATE_GIT_REPO_USER_ID, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
            steps.echo "Pushing new version tag ${this.version}"
            def originUrl = script.scm.getUserRemoteConfigs()[0].getUrl()
            def fixedOriginUrl = originUrl.replaceAll("https://", "https://${env.GIT_USERNAME}:${env.GIT_PASSWORD}")
            script.sh(script:"git tag -a v${this.version} -m \"Version ${this.version}\"")
            script.sh(script:"git push ${fixedOriginUrl} v${this.version}")
        }
    }
                    
}