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
        //if(!script.env.BRANCH_NAME?.trim() && (script.env.BRANCH_NAME.equalsIgnoreCase("master") || script.env.BRANCH_NAME.equalsIgnoreCase("develop")) {
            this.version = "${versionPrefix}.${script.env.BUILD_NUMBER}.${date.format('MMdd')}"
            this.semanticVersion = "${versionPrefix}.${script.env.BUILD_NUMBER}"
        //}  

        // Standard Docker Registry or custom docker registry?
        if(BuildConstants.DEFAULT_DOCKER_REGISTRY.equalsIgnoreCase(script.env.REGISTRY_URL)) 
        {
            steps.echo 'Publishing to standard docker registry.'
            this.tag = "${repository}/${image}:${this.version}"
            this.agentTag = "${repository}/${script.env.AGENT_IMAGE}"
            this.regsitry = ''
        }
        else 
        {
            steps.echo "Publishing to registry ${script.env.REGISTRY_URL}"
            this.tag = "${script.env.REGISTRY_URL}/${repository}/${image}:${this.version}"
            this.agentTag = "${script.env.REGISTRY_URL}/${repository}/${script.env.AGENT_IMAGE}"
            this.registry = "https://${script.env.REGISTRY_URL}"
        }        
    }
}