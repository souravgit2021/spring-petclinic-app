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

        // Nexus username/password credential; Jenkins exposes it as NEXUS_USR / NEXUS_PSW (masked in logs)
        NEXUS          = credentials('nexus-creds')
        // Routes all Maven downloads through Nexus and holds the deploy server ids
        MAVEN_SETTINGS = ".mvn/nexus-settings.xml"
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

        stage("Test Application") {
            steps {
                // Compiles and runs the tests; jacoco:report is bound to prepare-package in the pom,
                // so invoke it explicitly to produce target/site/jacoco/jacoco.xml for SonarQube
                sh 'mvn -s "$MAVEN_SETTINGS" clean test jacoco:report'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/TEST-*.xml'
                }
            }
        }

        stage("OWASP Dependency-Check (SCA)") {
            steps {
                // Scans the Maven dependency tree against the NVD and fails the build on any
                // vulnerability with CVSS >= 7. The NVD database is cached in the agent's ~/.m2,
                // so only the first run does the full (slow) download.
                withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                    sh '''
                        mvn -s "$MAVEN_SETTINGS" org.owasp:dependency-check-maven:13.0.0:check \
                            -DnvdApiKeyEnvironmentVariable=NVD_API_KEY \
                            -DfailBuildOnCVSS=7 \
                            -DsuppressionFile=dependency-check-suppressions.xml \
                            -Dformats=HTML,JSON,XML \
                            -DossIndexAnalyzerEnabled=false \
                            -DassemblyAnalyzerEnabled=false \
                            -DnodeAuditAnalyzerEnabled=false
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
                }
            }
        }

        stage("SonarQube Analysis") {
            steps {
                script {
                    withSonarQubeEnv('sonar') {
                        sh '''
                            mvn -s "$MAVEN_SETTINGS" org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                                -Dsonar.java.binaries=target/classes \
                                -Dsonar.junit.reportPaths=target/surefire-reports \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                        '''
                    }
                }
            }
        }


        stage("Build Application") {
            steps {
                // Tests already passed in the previous stage. No 'clean', so the test and
                // coverage reports in target/ are kept.
                sh 'mvn -s "$MAVEN_SETTINGS" package -DskipTests'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/spring-petclinic-*.jar', fingerprint: true
                }
            }
        }

        stage("Publish to Nexus") {
            steps {
                // Uploads only the built jar to maven-snapshots.
                // Requires the "Nexus Artifact Uploader" Jenkins plugin. maven-snapshots only accepts
                // -SNAPSHOT versions, so the version is read from pom.xml (e.g. 4.0.0-SNAPSHOT); each
                // build overwrites the previous upload of that version.
                // script {
                //     def projectVersion = sh(
                //         script: 'mvn -s "$MAVEN_SETTINGS" -q help:evaluate -Dexpression=project.version -DforceStdout',
                //         returnStdout: true
                //     ).trim()

                    nexusArtifactUploader(
                        nexusVersion: 'nexus3',
                        protocol: 'http',
                        nexusUrl: '192.168.1.11:8081',
                        repository: 'maven-snapshots',
                        credentialsId: 'nexus-creds',
                        groupId: 'org.springframework.samples',
                        version: '4.0.0-SNAPSHOT',
                        artifacts: [
                            [artifactId: 'spring-petclinic', classifier: '', file: "target/spring-petclinic-4.0.0-SNAPSHOT.jar", type: 'jar']
                        ]
                    )
                // }
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
                        -v trivy-cache:/root/.cache \
                        aquasec/trivy:latest image \
                        --timeout 15m \
                        --scanners vuln \
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

        stage("Update GitOps Manifest") {
            environment {
                GITOPS_REPO   = "github.com/souravgit2021/gitops-springpetclinic.git"
                GITOPS_BRANCH = "main"
                MANIFEST_FILE = "deployment.yaml"
            }
            steps {
                // Checked out into a sub-folder so it does not mix with the application sources
                dir('gitops') {
                    git branch: "${GITOPS_BRANCH}", url: "https://${GITOPS_REPO}",
                        credentialsId: 'github-token', changelog: false, poll: false

                    withCredentials([usernamePassword(
                        credentialsId: 'github-token',
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_TOKEN'
                    )]) {
                        sh '''
                            # Point the container image at the image pushed by this build. Only the
                            # image reference is replaced, so the rest of the line is left untouched.
                            sed -i -E "s#^([[:space:]]*image:[[:space:]]*)[^[:space:]]+#\\1${IMAGE_NAME}:${IMAGE_TAG}#" "$MANIFEST_FILE"
                            grep -n "image:" "$MANIFEST_FILE"

                            if git diff --quiet -- "$MANIFEST_FILE"; then
                                echo "$MANIFEST_FILE already uses ${IMAGE_NAME}:${IMAGE_TAG}, nothing to push"
                                exit 0
                            fi

                            git config user.name  "Jenkins"
                            git config user.email "jenkins@localhost"
                            git add "$MANIFEST_FILE"
                            git commit -m "Update image to ${IMAGE_NAME}:${IMAGE_TAG} (build #${BUILD_NUMBER}) [skip ci]"
                            git push "https://${GIT_USER}:${GIT_TOKEN}@${GITOPS_REPO}" "HEAD:${GITOPS_BRANCH}"
                        '''
                    }
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