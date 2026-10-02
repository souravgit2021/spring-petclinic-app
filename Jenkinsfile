pipeline {
    agent any

    tools {
        jdk 'jdk21'
        maven 'mvn3'
    }

    stages {
        stage('Cleanup Workspace') {
            steps {
                cleanWs()
            }
        }

        stage('Checkout SCM') {
            steps {
                git branch: 'main', url: 'https://github.com/souravgit2021/spring-petclinic-app.git'
            }
        }

        stage('Compile Application') {
            steps {
                // Compiles source and validates dependencies without running tests
                sh 'mvn clean compile -DskipTests'
            }
        }

        stage('Run Tests') {
            steps {
                // Runs the tests and generates surefire/failsafe reports
                sh 'mvn test'
            }
            post {
                always {
                    // Archives test results in Jenkins UI
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                script {
                    // Use the exact server installation name configured in Manage Jenkins -> System
                    withSonarQubeEnv('SonarQube') {
                        sh '''
                            mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                                -Dsonar.projectKey=spring-petclinic \
                                -Dsonar.projectName="Spring PetClinic"
                        '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    // Pauses pipeline until SonarQube webhook returns pass/fail status
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Package Application') {
            steps {
                // Packages the verified artifact without re-running the test suite
                sh 'mvn package -DskipTests'
            }
        }
    }

    post {
        failure {
            echo "Pipeline failed. Check test reports and SonarQube analysis."
        }
    }
}