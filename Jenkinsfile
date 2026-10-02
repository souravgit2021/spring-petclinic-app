pipeline{
    agent any
    tools {
        jdk 'jdk21'
        maven 'mvn3'
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