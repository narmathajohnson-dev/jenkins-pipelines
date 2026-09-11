def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    all_options = [
        "autoDeploy",
        "attachLogToEmail", 
        "clearWorkspace",
        "containers",
        "dockerArtifactMap",
        "dockerProjectRepo",
        "dockerProjectRepoBranch",
        "emailRecipients",
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
    def sonarProjectVersion = config.sonarProjectVersion
    
    String addXmlBindmodule = (
        jdkVersion.startsWith('openjdk-21') ||
        jdkVersion.startsWith('openjdk-17') ||
        jdkVersion.startsWith('openjdk-11') ||
        jdkVersion.startsWith('jdk1.8') ||
        jdkVersion.startsWith('jdk1.7') ||
        jdkVersion.startsWith('jdk1.6')
    ) ? '' : '--add-modules java.xml.bind '
    
    String useConcMarkSweepGC = (
        jdkVersion.startsWith('openjdk-21') || 
        jdkVersion.startsWith('openjdk-17')
    )? '' : '-XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled'
    String mavenCodeMetricsOpts = addXmlBindmodule + ' -Xmx3072m ' + useConcMarkSweepGC + ' -Djava.io.tmpdir=$WORKSPACE/tmp-build '
    String mavenSonarOpts = addXmlBindmodule + ' -Xmx3072m ' + useConcMarkSweepGC + ' -Djava.io.tmpdir=$WORKSPACE/tmp-build '
    boolean useVerboseVersion = config.useVerbaseVersion == true
    String autoDeploy = config.autoDeploy ?: null
    boolean waitForDeploy = config.waitForDeploy == true
    boolean runIntegrationTests = config.runIntegrationTests == true
    boolean clearWorkspace = config.clearWorkspace == true
    boolean skipInitialBuild = config.skipInitialBuild == true
    String[] branchesToDeployRegExpressions = ['main', 'master', 'release.*', 'truck', 'hotfix.*', 'feature.*', 'bugfix.*', 'support.*']
    String artifactVersion = ""

    def dockerArtifactMap = config.dockerArtifactMap ?: []
    def containers = config.containers ?: []
    def dockerProjectRepo = config.dockerProjectRepo
    def dockerProjectRepoBranch = config.dockerProjectRepoBranch ?: 'main'
    def branchNameForDocker = env.GIT_BRANCH
    def branchIsReleaseable = branchNameForDocker.equals("trunk") || 
                        branchNameForDocker.equals("rel_") || 
                        branchNameForDocker.equals("branches/rel_") ||
                        branchNameForDocker.equals("main") || 
                        branchNameForDocker.equals("master") ||
                        branchNameForDocker.matches("release/")
    def branchIsRelease = branchNameForDocker.equals("rel_") || 
                        branchNameForDocker.equals("branches/rel_") || 
                        branchNameForDocker.equals("release/") ||
                        branchNameForDocker.equals("release-") 
    boolean mainBranchFlag = (env.GIT_BRANCH == 'main' || env.GIT_BRANCH == 'master' || env.GIT_BRANCH == 'truck')
    boolean performReleaseBuild = config.performReleaseBuild == null ? (mainBranchFlag || branchIsRelease) : config.performReleaseBuild
    def devhubhost = "https://devhub.kumaran.com"
    def latestTag = branchIsRelease ? "rc" : (branchNameForDocker == "main" || branchNameForDocker == "master" || branchNameForDocker == "trunk") ? "latest" : null

    def dockerArtifactPattern = ""
    for (dockerArtifact in dockerArtifactMap) {
        dockerArtifactPattern += ",**/${dockerArtifact['pattern']}"
    }
    dockerArtifactPattern = dockerArtifactPattern.length() > 0 ? dockerArtifactPattern.substring(1) : " no files to stash"
    def disableMavenDownloadMessages = "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
    def enableMavenDownloadMessages = config.enableMavenDownloadMessages == true ? "" : disableMavenDownloadMessages
    def mavenGoal = ""

    echo "listOfProfiles: ${listOfProfiles}"
    echo "Maven version: ${mavenVersion}"
    echo "Node.js version: ${nodeJsVersion}"
    echo "JDK version: ${jdkVersion}"
    echo "Email recipients: ${emailRecipients}"
    echo "Attach log to email: ${attachLogToEmail}"
    echo "Sonar: ${sonar}"
    echo "Nexus: ${nexus}"
    echo "Env GIT_BRANCH: ${env.GIT_BRANCH}"
    echo "Workspace: ${env.WORKSPACE}"

    pipeline {
        options {
            timestamps()
            buildDiscarder(logRotator(
                daysToKeepStr: '30', 
                numToKeepStr: '40', 
                artifactDaysToKeepStr: '10', 
                artifactNumToKeepStr: '5'
            ))
        }        
        
        tools {
            maven 'maven-3.9.9' // Requires configuring 'maven3' in Manage Jenkins -> Tools
        }

        environment {
            sonarProjectKey = "${env.JOB_NAME}"
        }

        agent none        

        stages {
            stage('BUILD') {
                agent any


                options {
                    skipDefaultCheckout()
                    timeout(time: 30, unit: 'MINUTES')
                }
                when {
                    expression { skipInitialBuild != true }
                }
                steps {
                    script {
                        if (clearWorkspace) {
                            echo "Clearing workspace..."
                            deleteDir()
                        }
                        // checkout scm                        
                    }

                    echo "${autoDeploy != null ? "Auto-deploying to ${autoDeploy}..." : "No auto-deploy specified."}"
                    echo "containers size: ${containers.size()}"
                    echo "-branchNameForDocker: ${branchNameForDocker}-"
                    
                    sh 'mkdir -p $WORKSPACE/tmp-build'
                    echo 'Created temporary build directory: ${env.WORKSPACE}/tmp-build'

                    script {
                        mvnGoal = 'mvn -B clean install -DskipTests '

                        if (mainBranchFlag || branchIsRelease) {
                            mvnGoal += 'deploy:deploy ' +
                            '-DaltDeploymentRepository=snapshot-repo::default::http://172.24.2.167:8081/repository/maven-snapshots/ ' +
                            '-DdeployAtEnd=true '
                        }
                        if(listOfProfiles != null) {
                            for (profile in listOfProfiles) {
                                mvnGoal += '-P' + profile + ' '
                            }
                        }

                        mvnGoal += enableMavenDownloadMessages
                    }

                    echo "Maven command: ${mvnGoal}"

                    withMaven(
                        maven: mavenVersion, 
                        mavenLocalRepo: '.repository', 
                        publisherStrategy: 'EXPLICIT',
                        mavenOpts: '-Xmx3072m ' + useConcMarkSweepGC + ' -Djava.io.tmpdir=$WORKSPACE/tmp-build ') {
                        sh "${mvnGoal}"
                    }

                    echo "Building with Maven..."                    
                    // sh 'mvn clean install'

                    echo "Cleaning .repository..."

                    sh 'mkdir -p .repository'

                    dir('.repository') {
                        deleteDir()
                    }
                }
            }
        }   

    }

}