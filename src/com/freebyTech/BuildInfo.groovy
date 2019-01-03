package com.freebyTech

import com.freebyTech.BuildConstants

class BuildVersionInfo implements Serializable {
    String version
    String semVersion
    String tag
    String agentTag
    String registry

    private def steps

    BuildVersionInfo(steps) { this.steps = steps }

    def determineVersionNumber(String versionPrefix, String repository, String image) {
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
            steps.echo "Publishing to registry ${env.REGISTRY_URL}"
            this.tag = "${env.REGISTRY_URL}/${repository}/${image}:${this.version}"
            this.agentTag = "${env.REGISTRY_URL}/${repository}/${BuildConstants.DEFAULT_JENKINS_AGENT}"
            this.registry = "https://${env.REGISTRY_URL}"
        }        
    }
}