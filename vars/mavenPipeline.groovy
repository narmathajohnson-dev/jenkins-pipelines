def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def listOfProfiles = config.profiles ? config.profiles.tokenize(',') : null
    def nodeJsVersion = config.nodeJsVersion ?: 'nodejs-8.9.4'
    def jdkVersion = config.jdkVersion ?: 'jdk-21.0.6'
    def mavenVersion = config.mavenVersion ?: 'maven-3.9.9'
    def emailRecipients = config.emailRecipients ?: 'johnson.arockisamy@kumaran.com'
    def attachLogToEmail = config.attachLogToEmail == true
    def sonar = config.sonar == true
    def nexus = config.nexus == true
    def clearWorkspace = config.clearWorkspace == true
    if (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'truck' || env.BRANCH_NAME == 'release.*') {
        echo "Sonar and Nexus will be executed for main | master | truck | release.*: ${env.BRANCH_NAME}"
        sonar = true
        nexus = true
    }

    echo "listOfProfiles: ${listOfProfiles}"
    echo "Maven version: ${mavenVersion}"
    echo "Node.js version: ${nodeJsVersion}"
    echo "JDK version: ${jdkVersion}"
    echo "Email recipients: ${emailRecipients}"
    echo "Attach log to email: ${attachLogToEmail}"
    echo "Sonar: ${sonar}"
    echo "Nexus: ${nexus}"
    echo "Env BRANCH_NAME: ${env.BRANCH_NAME}"

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
                    echo "Env BRANCH_NAME: ${env.GIT_BRANCH}"
                    // sh 'mvn clean install'
                }
            }
        }   

    }

}