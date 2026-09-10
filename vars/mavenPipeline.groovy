def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def jdkVersion = config.jdkVersion ?: 'jdk21'

    echo "Using JDK version: ${jdkVersion}"

    pipeline {
        options {
            timestamps()
            disableConcurrentBuilds()
        }
        
        agent none

        stages {
            stage('Build') {
                agent { label 'build' }
                options {
                    skipDefaultCheckout()
                    timeout(time: 30, unit: 'MINUTES')
                }
                when {
                    expression { return !config.skipBuild }
                }
                steps {
                    script {
                        echo "Building with Maven..."
                        sh 'mvn clean install'
                    }
                }
            }
        }   

    }

}