def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    all_options = [
        "autoDeploy",
        "attachLogToEmail", 
        "clearWorkspace"
        "containers" 
        "dockerArtifactMap",
        "dockerProjectRepo",
        "dockerProjectRepoBranch",
        "emailRecipients"
        "enableMavenDownloadMessages",
        "jdkVersion", 
        "mavenVersion", 
        "nexusIq",
        "nodeJsVersion",
        "performReleaseBuild",
        "profiles", 
        "runIntegrationTests",
        "skipInitialBuild",
        "sonar",
        "sonarProjectVersion",
        "useNewSonor",        
        "useVerbaseVersion",
        "waitForDeploy"]

    config.each { key, value ->
        if (!all_options.contains(key)) {
            all_options_string = "\n            "
            all_options.each { option ->
                all_options_string += "  - ${option}\n          "
            }
            echo "BAD OPTION: ${key}. Valid options are: ${all_options_string}"
            error "Invalid option: ${key}. Valid options are: ${all_options.join(', ')}"
        }
    }

    def listOfProfiles = config.profiles ? config.profiles.tokenize(',') : null
    def nodeJsVersion = config.nodeJsVersion ?: 'nodejs-8.9.4'
    def jdkVersion = config.jdkVersion ?: 'jdk-21.0.6'
    def mavenVersion = config.mavenVersion ?: 'maven-3.9.9'
    def emailRecipients = config.emailRecipients ?: 'johnson.arockisamy@kumaran.com'
    def attachLogToEmail = config.attachLogToEmail == true
    
    def sonar = config.sonar == true
    def nexus = config.nexus == true
    if (env.GIT_BRANCH == 'main' || env.GIT_BRANCH == 'master' || env.GIT_BRANCH == 'truck' || env.GIT_BRANCH == 'release.*') {
        echo "Sonar and Nexus will be executed for main | master | truck | release.*: ${env.BRANCH_NAME}"
        sonar = true
        nexus = true
    }

    def sonarInstance = config.useNewSonar == false? 'Sonar old' : 'Sonar'

    String addXmlBindmodule = (
        jdkVersion.startsWith('openjdk-21') || 
        jdkVersion.startsWith('openjdk-17')
    )? '' : '--add-modules java.xml.bind '

    echo "listOfProfiles: ${listOfProfiles}"
    echo "Maven version: ${mavenVersion}"
    echo "Node.js version: ${nodeJsVersion}"
    echo "JDK version: ${jdkVersion}"
    echo "Email recipients: ${emailRecipients}"
    echo "Attach log to email: ${attachLogToEmail}"
    echo "Sonar: ${sonar}"
    echo "Nexus: ${nexus}"
    echo "Env GIT_BRANCH: ${env.GIT_BRANCH}"

    pipeline {
        options {
            timestamps()
            disableConcurrentBuilds()
        }
        
        agent any

        tools {
            maven 'maven-3.9.9' // Requires configuring 'maven3' in Manage Jenkins -> Tools
        }

        stages {
            stage('BUILD') {
                


                options {
                    skipDefaultCheckout()
                    timeout(time: 30, unit: 'MINUTES')
                }
                when {
                    expression { return !config.skipBuild }
                }
                steps {
                    // script {
                    //     if (clearWorkspace) {
                    //         echo "Clearing workspace..."
                    //         deleteDir()
                    //     }
                    //     checkout scm                        
                    // }

                    echo "Building with Maven..."                    
                    // sh 'mvn clean install'
                }
            }
        }   

    }

}