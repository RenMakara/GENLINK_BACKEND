pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        booleanParam(
            name: 'RUN_SONAR',
            defaultValue: true,
            description: 'Run SonarQube analysis and wait for the Quality Gate.'
        )
    }

    environment {
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
        SONARQUBE_ENV = 'SonarQube'
        SONAR_PROJECT_KEY = 'campuslink-kh-backend'
        SONAR_PROJECT_NAME = 'CampusLink KH Backend'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare Gradle Wrapper') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'chmod +x ./gradlew'
                    }
                }
            }
        }

        stage('Validate Tools') {
            steps {
                script {
                    runCommand('java -version', 'java -version')
                    runCommand('./gradlew --version', '.\\gradlew.bat --version')
                }
            }
        }

        stage('Test and Coverage') {
            steps {
                script {
                    runCommand('./gradlew clean test jacocoTestReport', '.\\gradlew.bat clean test jacocoTestReport')
                }
            }
        }

        stage('SonarQube Analysis') {
            when {
                expression { return params.RUN_SONAR }
            }
            steps {
                withSonarQubeEnv("${env.SONARQUBE_ENV}") {
                    script {
                        runCommand(
                            "./gradlew sonar -Dsonar.projectKey=${env.SONAR_PROJECT_KEY} -Dsonar.projectName='${env.SONAR_PROJECT_NAME}' -Dsonar.host.url=${env.SONAR_HOST_URL} -Dsonar.token=${env.SONAR_AUTH_TOKEN}",
                            ".\\gradlew.bat sonar -Dsonar.projectKey=${env.SONAR_PROJECT_KEY} \"-Dsonar.projectName=${env.SONAR_PROJECT_NAME}\" -Dsonar.host.url=${env.SONAR_HOST_URL} -Dsonar.token=${env.SONAR_AUTH_TOKEN}"
                        )
                    }
                }
            }
        }

        stage('Quality Gate') {
            when {
                expression { return params.RUN_SONAR }
            }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Build Jar') {
            steps {
                script {
                    runCommand('./gradlew bootJar -x test', '.\\gradlew.bat bootJar -x test')
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'build/test-results/test/*.xml'
            archiveArtifacts allowEmptyArchive: true, artifacts: 'build/libs/*.jar, build/reports/jacoco/test/html/**, build/reports/jacoco/test/jacocoTestReport.xml'
        }
    }
}

void runCommand(String unixCommand, String windowsCommand) {
    if (isUnix()) {
        sh unixCommand
    } else {
        bat windowsCommand
    }
}
