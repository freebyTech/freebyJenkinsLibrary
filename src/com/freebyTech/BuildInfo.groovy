package com.freebyTech

import com.freebyTech.BuildConstants

class BuildInfo implements Serializable {
    String version
    String semVersion
    String tag
    String agentTag
    String registry

    private def steps

    BuildInfo(steps) { this.steps = steps }

    def determineBuildInfo(String versionPrefix, String repository, String image) {
        def date = new Date()
        //if(!steps.env.BRANCH_NAME?.trim() && (steps.env.BRANCH_NAME.equalsIgnoreCase("master") || steps.env.BRANCH_NAME.equalsIgnoreCase("develop")) {
            this.version = "${versionPrefix}.${steps.env.BUILD_NUMBER}.${date.format('MMdd')}"
            this.semVersion = "${versionPrefix}.${steps.env.BUILD_NUMBER}"
        //}  

        // Standard Docker Registry or custom docker registry?
        if('index.docker.io'.equalsIgnoreCase(env.REGISTRY_URL)) 
        {
            steps.echo 'Publishing to standard docker registry.'
            this.tag = "${repository}/${image}:${this.version}"
            this.agentTag = "${repository}/${BuildConstants.DEFAULT_JENKINS_AGENT}"
            this.regsitry = ''
        }
        else 
        {
            steps.echo "Publishing to registry ${steps.env.REGISTRY_URL}"
            this.tag = "${steps.env.REGISTRY_URL}/${repository}/${image}:${this.version}"
            this.agentTag = "${steps.env.REGISTRY_URL}/${repository}/${BuildConstants.DEFAULT_JENKINS_AGENT}"
            this.registry = "https://${steps.env.REGISTRY_URL}"
        }        
    }
}