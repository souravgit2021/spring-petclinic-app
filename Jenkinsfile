pipeline {
    agent any

    tools {
        jdk 'jdk21'
        maven 'mvn3'
    }

    environment {
        APP_NAME    = "spring-app"
        RELEASE     = "1.0.0"
        DOCKER_USER = "docsourav1992"
        IMAGE_NAME  = "${DOCKER_USER}/${APP_NAME}"
        IMAGE_TAG   = "${RELEASE}-${BUILD_NUMBER}"
    }

    stages {
        stage("Cleanup Workspace") {
            steps {
                cleanWs()
            }
        }

        stage("Checkout from SCM") {
            steps {
                git branch: 'main', url: 'https://github.com/souravgit2021/spring-petclinic-app.git'
            }
        }

        stage("Build & Test Application") {
            steps {
                // Compiles, runs tests, packages the JAR, and produces the JaCoCo XML report in a single pass
                sh "mvn clean package jacoco:report"
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/TEST-*.xml'
                }
                success {
                    archiveArtifacts artifacts: 'target/spring-petclinic-*.jar', fingerprint: true
                }
            }
        }

        stage("SonarQube Analysis") {
            steps {
                script {
                    withSonarQubeEnv('sonar') {
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

        // stage("Quality Gate") {
        //     steps {
        //         timeout(time: 5, unit: 'MINUTES') {
        //             script {
        //                 waitForQualityGate abortPipeline: true
        //             }
        //         }
        //     }
        // }

        stage("Build Docker Image") {
            steps {
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest ."
            }
        }

        stage("Trivy Security Scan") {
            steps {
                sh """
                    docker run --rm \
                        -u root \
                        -v /var/run/docker.sock:/var/run/docker.sock \
                        -v \${WORKSPACE}:/workspace \
                        aquasec/trivy:latest image \
                        --format table \
                        --output /workspace/trivy-report.txt \
                        --severity HIGH,CRITICAL \
                        --exit-code 0 \
                        ${IMAGE_NAME}:${IMAGE_TAG}
                """
                archiveArtifacts artifacts: 'trivy-report.txt', allowEmptyArchive: true
            }
        }

        stage("Push to Docker Hub") {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub',
                    usernameVariable: 'DOCKER_USER_ID',
                    passwordVariable: 'DOCKER_USER_PWD'
                )]) {
                    sh '''
                        echo "$DOCKER_USER_PWD" | docker login -u "$DOCKER_USER_ID" --password-stdin
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:latest
                        docker logout
                    '''
                }
            }
        }
    }

    post {
        always {
            // Remove local images created in this build to prevent agent disk exhaustion
            sh """
                docker rmi ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest || true
            """
        }
    }
}