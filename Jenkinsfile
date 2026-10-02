pipeline{
    agent any
    tools {
        jdk 'jdk21'
        maven 'mvn3'
    }
    
    environment {
        APP_NAME = "spring-app"
        RELEASE = "1.0.0"
        DOCKER_USER = "docsourav1992"
        DOCKER_PASS = 'dockerhub'
        IMAGE_NAME = "${DOCKER_USER}" + "/" + "${APP_NAME}"
        IMAGE_TAG = "${RELEASE}-${BUILD_NUMBER}"

    }
    
    
    
    stages{
        stage("Cleanup Workspace"){
            steps {
                cleanWs()
            }

        }
    
        stage("Checkout from SCM"){
            steps {
                git branch: 'main', url: 'https://github.com/souravgit2021/spring-petclinic-app.git'
            }

        }

        stage("Test Application"){
            steps {
                // jacoco:report is bound to prepare-package in the pom, so invoke it explicitly
                // to produce target/site/jacoco/jacoco.xml for SonarQube coverage
                sh "mvn clean test jacoco:report"
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/TEST-*.xml'
                }
            }

        }


        stage("Sonarqube Analysis") {
            steps {
                script {
                    withSonarQubeEnv(credentialsId: 'sonar-token') {
                        sh '''
                            mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                                -Dsonar.java.binaries=target/classes \
                                -Dsonar.junit.reportPaths=target/surefire-reports \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                        '''
                    }
                }
            }

        }


        stage("Build Application"){
            steps {
                sh "mvn clean package"
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/spring-petclinic-*.jar', fingerprint: true
                }
            }

        }


        stage("Build & Push Docker Image") {
            steps {
                script {
                    docker.withRegistry('',DOCKER_PASS) {
                        docker_image = docker.build "${IMAGE_NAME}"
                    }

                    docker.withRegistry('',DOCKER_PASS) {
                        docker_image.push("${IMAGE_TAG}")
                        docker_image.push('latest')
                    }
                }
            }
        }


        // stage("Quality Gate") {
        //     steps {
        //         script {
        //             waitForQualityGate abortPipeline: false, credentialsId: 'jenkins-sonarqube-token'
        //         }
        //     }

        // }

    
    


    }
}