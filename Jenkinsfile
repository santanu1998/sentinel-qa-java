/*
 * SentinelQA — declarative Jenkins pipeline.
 *
 * Mirrors the GitHub Actions workflow so a team can migrate either way without rewriting tests:
 * fast API gate, then the browser matrix in parallel, then a published Allure report.
 */
pipeline {

    agent any

    tools {
        jdk 'jdk-25'
        maven 'maven-3.9'  // any Maven 3.9+ tool configured on the agent
    }

    parameters {
        choice(name: 'SUITE',
               choices: ['testng-smoke.xml', 'testng-api.xml', 'testng-regression.xml'],
               description: 'TestNG suite to execute')
        choice(name: 'ENVIRONMENT', choices: ['qa', 'staging'], description: 'Target environment')
        booleanParam(name: 'HEADLESS', defaultValue: true, description: 'Run browsers headless')
        booleanParam(name: 'RAISE_DEFECTS', defaultValue: false,
                     description: 'Create a Jira issue for each non-environmental failure')
    }

    environment {
        MAVEN_OPTS = '-Xmx2g'
        JIRA = credentials('jira-automation-user')
    }

    options {
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timestamps()
        disableConcurrentBuilds()
    }

    triggers {
        // Nightly full regression
        cron(env.BRANCH_NAME == 'main' ? 'H 2 * * *' : '')
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    currentBuild.description = "${params.SUITE} on ${params.ENVIRONMENT}"
                }
            }
        }

        stage('Compile') {
            steps {
                sh 'mvn -B clean test-compile'
            }
        }

        stage('Selenium Grid up') {
            when { expression { params.SUITE != 'testng-api.xml' } }
            steps {
                sh 'docker compose -f docker-compose.grid.yml up -d --wait'
            }
        }

        stage('API gate') {
            steps {
                sh """
                   mvn -B test \
                     -Dsuite=testng-api.xml \
                     -Denv=${params.ENVIRONMENT} \
                     -Ddefect.autocreate.enabled=${params.RAISE_DEFECTS} \
                     -Ddefect.user=${JIRA_USR} \
                     -Ddefect.api.token=${JIRA_PSW}
                """
            }
        }

        stage('UI matrix') {
            when { expression { params.SUITE != 'testng-api.xml' } }
            parallel {
                stage('Chrome') {
                    steps {
                        sh """
                           mvn -B test \
                             -Dsuite=${params.SUITE} \
                             -Dbrowser=chrome \
                             -Dheadless=${params.HEADLESS} \
                             -Denv=${params.ENVIRONMENT} \
                             -Dgrid.enabled=true
                        """
                    }
                }
                stage('Firefox') {
                    steps {
                        sh """
                           mvn -B test \
                             -Dsuite=${params.SUITE} \
                             -Dbrowser=firefox \
                             -Dheadless=${params.HEADLESS} \
                             -Denv=${params.ENVIRONMENT} \
                             -Dgrid.enabled=true
                        """
                    }
                }
            }
        }

        stage('Postman collection') {
            steps {
                sh '''
                   npm install --no-save newman newman-reporter-htmlextra
                   npx newman run postman/SentinelQA_Reservations.postman_collection.json \
                       -e postman/qa.postman_environment.json \
                       --reporters cli,junit --reporter-junit-export target/newman/junit.xml
                '''
            }
        }
    }

    post {
        always {
            sh 'docker compose -f docker-compose.grid.yml down --remove-orphans || true'
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml, target/newman/*.xml'
            allure includeProperties: false, results: [[path: 'target/allure-results']]
            archiveArtifacts artifacts: 'target/logs/**', allowEmptyArchive: true
        }
        failure {
            emailext subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER} (${params.ENVIRONMENT})",
                     body: '''The suite failed. Triage verdicts and screenshots are attached to the
Allure report: ${BUILD_URL}allure/''',
                     recipientProviders: [developers(), requestor()]
        }
        unstable {
            echo 'Suite finished with flaky retries — review the flakiness report in Allure.'
        }
    }
}
