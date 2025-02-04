pipeline {
    agent any
    parameters {
        choice(
            name: 'ENVIRONMENT',
            choices: ['DEV', 'QA', 'PROD'],
            description: 'Select the environment for the build'
        )
    }
    environment {
        DEPLOY_PATH_DEV_QA = '/home/youruser/newbuild/'
        DEPLOY_PATH_PROD = '/home/youruser/prodbuild/'
        ARTIFACT_NAME = 'app-name'
    }
    stages {
        stage('Setup Environment Variables') {
            steps {
                script {
                    echo "Setting up the environment for ${params.ENVIRONMENT}..."
                    
                    // Example: Set environment variables for different environments
                    if (params.ENVIRONMENT == 'DEV') {
                        env.API_URL = 'https://dev-api.yourdomain.com/api'
                    } else if (params.ENVIRONMENT == 'QA') {
                        env.API_URL = 'https://qa-api.yourdomain.com/api'
                    } else if (params.ENVIRONMENT == 'PROD') {
                        env.API_URL = 'https://api.yourdomain.com/api'
                    }

                    // Write environment variables to a temporary .env file for the build
                    sh """
                    echo API_URL=${env.API_URL} > .env
                    """
                }
            }
        }

        stage('Clone Repository') {
            steps {
                echo 'Cloning repository...'
                git branch: 'release', url: 'git@yourrepo.git'
            }
        }

        stage('Install Dependencies') {
            steps {
                echo 'Installing dependencies...'
                sh 'npm install' // Modify this according to your app's dependency manager
            }
        }

        stage('Build App') {
            steps {
                echo 'Building the application...'
                sh 'npm run build' // Modify this according to your app's build process
            }
        }

        stage('Create Tarball') {
            steps {
                echo 'Creating tarball of the build...'
                script {
                    // Assuming your app has a version in package.json
                    def packageJson = readJSON file: 'package.json'
                    def version = packageJson.version
                    def envName = params.ENVIRONMENT.toLowerCase()

                    // Create tarball with environment name and version
                    sh """
                    cd ./build
                    tar cvfz ${ARTIFACT_NAME}-${envName}.${version}.tgz *
                    """
                }
            }
        }

        stage('Deploy to Server') {
            steps {
                script {
                    def deployPath
                    def serverIP
                    def credentialsID
                    def deployScriptPath
                    def serverUser

                    // Define server details based on the environment
                    if (params.ENVIRONMENT == 'PROD') {
                        serverIP = 'your.prod.server.ip'
                        credentialsID = 'prod-credentials-id' // Replace with your actual prod credentials ID
                        deployPath = DEPLOY_PATH_PROD
                        deployScriptPath = '/home/youruser/scripts/deploy.sh'
                        serverUser = 'prod-user'  // Change to appropriate user for prod
                    } else {
                        serverIP = params.ENVIRONMENT == 'QA' ? 'qa-server-ip' : 'dev-server-ip'
                        credentialsID = 'dev-qa-credentials-id' // Replace with your dev/qa credentials ID
                        deployPath = DEPLOY_PATH_DEV_QA
                        deployScriptPath = '/home/youruser/scripts/deploy.sh'
                        serverUser = 'dev-user'  // Change to appropriate user for dev/qa
                    }

                    def artifactName = "${ARTIFACT_NAME}-${params.ENVIRONMENT.toLowerCase()}.${version}.tgz"

                    echo "Deploying artifact to ${serverIP}..."

                    // Deploy artifact to server
                    sshagent([credentialsID]) {
                        sh """
                            scp -o StrictHostKeyChecking=no build/${artifactName} ${serverUser}@${serverIP}:${deployPath}
                            ssh ${serverUser}@${serverIP} 'bash ${deployScriptPath}'
                        """
                    }
                }
            }
        }

        stage('Archive Artifact') {
            steps {
                // Archive the tarball with the environment name
                archiveArtifacts artifacts: 'build/*.tgz', fingerprint: true
            }
        }
    }
}
