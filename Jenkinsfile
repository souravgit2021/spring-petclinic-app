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

        stage("Build Application"){
            steps {
                sh "mvn clean package"
            }

        }

        stage("Test Application"){
            steps {
                sh "mvn test"
            }

        }
       
       stage('SonarQube Analysis') {
        steps {
         script {
            withSonarQubeEnv(credentialsId: 'sonar-token') {
                sh '''
                    mvn -B clean verify \
                    org.sonarsource.scanner.maven:sonar-maven-plugin:sonar
                '''
            }
        }
    }
}
       
       
        }
}
 