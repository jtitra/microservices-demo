// Jenkinsfile for a full-featured CI/CD pipeline
// Handles npm build, containerization, security scans, multi-environment deployments (rolling, blue/green, canary),
// metric validation, automatic rollback, notifications, and issue tracking
// Does not use Helm, uses raw Kubernetes manifests with kubectl

pipeline {
    // Define agent to run on any available node with Docker and kubectl installed
    agent {
        label 'docker-kubectl-node'
    }

    // Environment variables for the pipeline
    environment {
        // Application metadata
        APP_NAME = 'my-node-app'
        APP_VERSION = "${env.BUILD_NUMBER}"
        GIT_REPO = 'https://github.com/example/my-node-app.git'
        GIT_BRANCH = 'main'

        // Docker and registry settings
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_IMAGE = "${DOCKER_REGISTRY}/example/${APP_NAME}:${APP_VERSION}"
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-creds')

        // Kubernetes settings
        KUBE_CONFIG = credentials('kubeconfig')
        KUBE_NAMESPACE_DEV = 'dev'
        KUBE_NAMESPACE_STAGING = 'staging'
        KUBE_NAMESPACE_PROD = 'prod'
        K8S_MANIFEST_DIR = 'k8s'

        // Stable image versions for rollback
        DEV_STABLE_IMAGE = 'docker.io/example/my-node-app:latest-dev'
        STAGING_STABLE_IMAGE = 'docker.io/example/my-node-app:latest-staging'
        PROD_STABLE_IMAGE = 'docker.io/example/my-node-app:latest-prod'

        // Nexus for artifact storage
        NEXUS_URL = 'https://nexus.example.com'
        NEXUS_REPO = 'my-node-app-artifacts'
        NEXUS_CREDENTIALS = credentials('nexus-creds')

        // Slack for notifications
        SLACK_CHANNEL = '#deployments'
        SLACK_TOKEN = credentials('slack-token')

        // Jira for issue tracking
        JIRA_URL = 'https://jira.example.com'
        JIRA_PROJECT = 'DEVOPS'
        JIRA_CREDENTIALS = credentials('jira-creds')

        // ServiceNow for change management
        SNOW_URL = 'https://servicenow.example.com'
        SNOW_CREDENTIALS = credentials('snow-creds')
        SNOW_CHANGE_TABLE = 'change_request'

        // Prometheus for metric validation
        PROMETHEUS_URL = 'http://prometheus.example.com:9090'

        // Audit log file
        AUDIT_LOG = "${WORKSPACE}/audit.log"
    }

    // Pipeline options
    options {
        // Keep only the last 10 builds
        buildDiscarder(logRotator(numToKeepStr: '10'))
        // Timeout after 2 hours
        timeout(time: 2, unit: 'HOURS')
        // Disable concurrent builds
        disableConcurrentBuilds()
        // Skip default checkout (we'll do it explicitly)
        skipDefaultCheckout()
    }

    // Triggers (e.g., poll SCM every 5 minutes)
    triggers {
        pollSCM('H/5 * * * *')
    }

    // Pipeline stages
    stages {
        // Stage 1: Checkout code from Git
        stage('Checkout') {
            steps {
                script {
                    echo "Checking out code from ${GIT_REPO} branch ${GIT_BRANCH}"
                    git branch: "${GIT_BRANCH}", url: "${GIT_REPO}"
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Checked out code from ${GIT_REPO}' >> ${AUDIT_LOG}"
                }
            }
        }

        // Stage 2: Install dependencies and build the application
        stage('NPM Build') {
            steps {
                script {
                    echo "Running npm install and build"
                    sh '''
                        npm install
                        npm run build
                    '''
                    // Archive build artifacts
                    archiveArtifacts artifacts: 'dist/**', allowEmptyArchive: true
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Completed npm build' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "NPM Build failed for ${APP_NAME} build #${BUILD_NUMBER}! Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 3: Run unit tests
        stage('Unit Tests') {
            steps {
                script {
                    echo "Running unit tests"
                    sh 'npm test'
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Completed unit tests' >> ${AUDIT_LOG}"
                }
            }
            post {
                always {
                    // Archive test results
                    junit 'test-results/*.xml'
                }
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Unit Tests failed for ${APP_NAME} build #${BUILD_NUMBER}! Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 4: Build Docker image
        stage('Build Docker Image') {
            steps {
                script {
                    echo "Building Docker image ${DOCKER_IMAGE}"
                    sh '''
                        docker build -t ${DOCKER_IMAGE} .
                    '''
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Built Docker image ${DOCKER_IMAGE}' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Docker Build failed for ${APP_NAME} build #${BUILD_NUMBER}! Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 5: Security scan with Trivy
        stage('Security Scan') {
            steps {
                script {
                    echo "Running Trivy security scan on ${DOCKER_IMAGE}"
                    sh '''
                        trivy image --severity HIGH,CRITICAL --exit-code 1 ${DOCKER_IMAGE}
                    '''
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Completed security scan' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Security Scan failed for ${APP_NAME} build #${BUILD_NUMBER}! Critical vulnerabilities found. Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 6: Push Docker image to registry
        stage('Push Docker Image') {
            steps {
                script {
                    echo "Pushing Docker image to ${DOCKER_REGISTRY}"
                    sh '''
                        echo "${DOCKERHUB_CREDENTIALS_PSW}" | docker login -u "${DOCKERHUB_CREDENTIALS_USR}" --password-stdin ${DOCKER_REGISTRY}
                        docker push ${DOCKER_IMAGE}
                        # Tag as latest for the environment
                        docker tag ${DOCKER_IMAGE} ${DOCKER_REGISTRY}/example/${APP_NAME}:latest
                        docker push ${DOCKER_REGISTRY}/example/${APP_NAME}:latest
                    '''
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Pushed Docker image ${DOCKER_IMAGE}' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Docker Push failed for ${APP_NAME} build #${BUILD_NUMBER}! Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 7: Store artifact in Nexus
        stage('Store Artifact in Nexus') {
            steps {
                script {
                    echo "Uploading artifact to Nexus"
                    sh '''
                        tar -czf ${APP_NAME}-${APP_VERSION}.tar.gz dist/
                        curl -u "${NEXUS_CREDENTIALS_USR}:${NEXUS_CREDENTIALS_PSW}" \
                             --upload-file ${APP_NAME}-${APP_VERSION}.tar.gz \
                             ${NEXUS_URL}/repository/${NEXUS_REPO}/${APP_NAME}/${APP_VERSION}/${APP_NAME}-${APP_VERSION}.tar.gz
                    '''
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Uploaded artifact to Nexus' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Nexus artifact upload failed for ${APP_NAME} build #${BUILD_NUMBER}! Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 8: Create Jira ticket
        stage('Create Jira Ticket') {
            steps {
                script {
                    echo "Creating Jira ticket for deployment tracking"
                    def jiraResponse = httpRequest(
                        authentication: 'jira-creds',
                        httpMode: 'POST',
                        url: "${JIRA_URL}/rest/api/2/issue",
                        requestBody: """
                            {
                                "fields": {
                                    "project": {"key": "${JIRA_PROJECT}"},
                                    "summary": "Deployment of ${APP_NAME} v${APP_VERSION} to Dev",
                                    "description": "Tracking deployment of build #${BUILD_NUMBER} to dev environment.",
                                    "issuetype": {"name": "Task"}
                                }
                            }
                        """,
                        contentType: 'APPLICATION_JSON'
                    )
                    def jiraIssue = readJSON text: jiraResponse.content
                    env.JIRA_ISSUE_KEY = jiraIssue.key
                    echo "Created Jira ticket: ${JIRA_ISSUE_KEY}"
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Created Jira ticket ${JIRA_ISSUE_KEY}' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Jira ticket creation failed for ${APP_NAME} build #${BUILD_NUMBER}! Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 9: Create ServiceNow change request
        stage('Create ServiceNow Change Request') {
            steps {
                script {
                    echo "Creating ServiceNow change request"
                    def snowResponse = httpRequest(
                        authentication: 'snow-creds',
                        httpMode: 'POST',
                        url: "${SNOW_URL}/api/now/table/${SNOW_CHANGE_TABLE}",
                        requestBody: """
                            {
                                "short_description": "Change request for ${APP_NAME} v${APP_VERSION} deployment",
                                "description": "Deploying build #${BUILD_NUMBER} to dev environment.",
                                "state": "new",
                                "priority": "3"
                            }
                        """,
                        contentType: 'APPLICATION_JSON'
                    )
                    def snowChange = readJSON text: snowResponse.content
                    env.SNOW_CHANGE_ID = snowChange.result.sys_id
                    echo "Created ServiceNow change request: ${SNOW_CHANGE_ID}"
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Created ServiceNow change request ${SNOW_CHANGE_ID}' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "ServiceNow change request creation failed for ${APP_NAME} build #${BUILD_NUMBER}! Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 10: Deploy to Dev (Rolling Update)
        stage('Deploy to Dev (Rolling)') {
            steps {
                script {
                    echo "Deploying to dev environment with rolling update"
                    // Update manifest with new image
                    sh """
                        sed -i 's|image:.*|image: ${DOCKER_IMAGE}|' ${K8S_MANIFEST_DIR}/dev/deployment.yaml
                        kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/dev/ -n ${KUBE_NAMESPACE_DEV}
                    """
                    // Perform health check
                    echo "Checking application health in dev"
                    sh """
                        kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_DEV} wait --for=condition=ready pod -l app=${APP_NAME} --timeout=300s
                        curl -f http://${APP_NAME}.${KUBE_NAMESPACE_DEV}.svc.cluster.local/health
                    """
                    // Update stable image
                    env.DEV_STABLE_IMAGE = "${DOCKER_IMAGE}"
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Deployed to dev environment' >> ${AUDIT_LOG}"
                }
            }
            post {
                success {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: ' dobry',
                            message: "Successfully deployed ${APP_NAME} v${APP_VERSION} to dev environment! Build #${BUILD_NUMBER}"
                        )
                    }
                }
                failure {
                    script {
                        echo "Rolling back dev deployment to ${DEV_STABLE_IMAGE}"
                        sh """
                            sed -i 's|image:.*|image: ${DEV_STABLE_IMAGE}|' ${K8S_MANIFEST_DIR}/dev/deployment.yaml
                            kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/dev/ -n ${KUBE_NAMESPACE_DEV}
                        """
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Dev deployment failed for ${APP_NAME} build #${BUILD_NUMBER}! Rolled back to ${DEV_STABLE_IMAGE}. Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 11: Validate Metrics in Dev
        stage('Validate Metrics in Dev') {
            steps {
                script {
                    echo "Validating metrics using Prometheus"
                    def errorRate = sh(script: """
                        curl -s '${PROMETHEUS_URL}/api/v1/query?query=rate(http_requests_total{app="${APP_NAME}", namespace="${KUBE_NAMESPACE_DEV}", status=~"5.."}[5m])' | jq '.data.result[0].value[1]' || echo "0"
                    """, returnStdout: true).trim()
                    def latency = sh(script: """
                        curl -s '${PROMETHEUS_URL}/api/v1/query?query=histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{app="${APP_NAME}", namespace="${KUBE_NAMESPACE_DEV}"}[5m]))' | jq '.data.result[0].value[1]' || echo "0"
                    """, returnStdout: true).trim()
                    echo "Error Rate: ${errorRate}, Latency (p95): ${latency}"
                    if (errorRate.toFloat() > 0.01 || latency.toFloat() > 0.5) {
                        echo "Metrics validation failed, initiating rollback"
                        sh """
                            sed -i 's|image:.*|image: ${DEV_STABLE_IMAGE}|' ${K8S_MANIFEST_DIR}/dev/deployment.yaml
                            kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/dev/ -n ${KUBE_NAMESPACE_DEV}
                        """
                        error "Metrics validation failed: Error Rate=${errorRate}, Latency=${latency}"
                    }
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Validated metrics in dev' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Metrics validation failed in dev for ${APP_NAME} build #${BUILD_NUMBER}! Rolled back to ${DEV_STABLE_IMAGE}. Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 12: Deploy to Staging (Blue/Green)
        stage('Deploy to Staging (Blue/Green)') {
            steps {
                script {
                    echo "Deploying to staging with blue/green strategy"
                    // Deploy to green environment
                    sh """
                        sed -i 's|image:.*|image: ${DOCKER_IMAGE}|' ${K8S_MANIFEST_DIR}/staging/deployment-green.yaml
                        kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/staging/deployment-green.yaml -n ${KUBE_NAMESPACE_STAGING}
                        kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/staging/service.yaml -n ${KUBE_NAMESPACE_STAGING}
                    """
                    // Perform health check
                    echo "Checking green environment health in staging"
                    sh """
                        kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_STAGING} wait --for=condition=ready pod -l app=${APP_NAME},env=green --timeout=300s
                        curl -f http://${APP_NAME}-green.${KUBE_NAMESPACE_STAGING}.svc.cluster.local/health
                    """
                    // Switch traffic to green
                    echo "Switching traffic to green environment"
                    sh """
                        kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_STAGING} patch service ${APP_NAME} -p '{"spec":{"selector":{"env":"green"}}}'
                    """
                    // Update stable image
                    env.STAGING_STABLE_IMAGE = "${DOCKER_IMAGE}"
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Deployed to staging (green) and switched traffic' >> ${AUDIT_LOG}"
                }
            }
            post {
                success {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'good',
                            message: "Successfully deployed ${APP_NAME} v${APP_VERSION} to staging (green)! Build #${BUILD_NUMBER}"
                        )
                    }
                }
                failure {
                    script {
                        echo "Rolling back staging deployment to ${STAGING_STABLE_IMAGE}"
                        sh """
                            sed -i 's|image:.*|image: ${STAGING_STABLE_IMAGE}|' ${K8S_MANIFEST_DIR}/staging/deployment-blue.yaml
                            kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/staging/deployment-blue.yaml -n ${KUBE_NAMESPACE_STAGING}
                            kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_STAGING} patch service ${APP_NAME} -p '{"spec":{"selector":{"env":"blue"}}}'
                        """
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Staging deployment failed for ${APP_NAME} build #${BUILD_NUMBER}! Rolled back to ${STAGING_STABLE_IMAGE}. Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 13: Validate Metrics in Staging
        stage('Validate Metrics in Staging') {
            steps {
                script {
                    echo "Validating metrics in staging"
                    def errorRate = sh(script: """
                        curl -s '${PROMETHEUS_URL}/api/v1/query?query=rate(http_requests_total{app="${APP_NAME}", namespace="${KUBE_NAMESPACE_STAGING}", env="green", status=~"5.."}[5m])' | jq '.data.result[0].value[1]' || echo "0"
                    """, returnStdout: true).trim()
                    def latency = sh(script: """
                        curl -s '${PROMETHEUS_URL}/api/v1/query?query=histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{app="${APP_NAME}", namespace="${KUBE_NAMESPACE_STAGING}", env="green"}[5m]))' | jq '.data.result[0].value[1]' || echo "0"
                    """, returnStdout: true).trim()
                    echo "Staging Error Rate: ${errorRate}, Latency (p95): ${latency}"
                    if (errorRate.toFloat() > 0.01 || latency.toFloat() > 0.5) {
                        echo "Metrics validation failed, initiating rollback"
                        sh """
                            sed -i 's|image:.*|image: ${STAGING_STABLE_IMAGE}|' ${K8S_MANIFEST_DIR}/staging/deployment-blue.yaml
                            kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/staging/deployment-blue.yaml -n ${KUBE_NAMESPACE_STAGING}
                            kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_STAGING} patch service ${APP_NAME} -p '{"spec":{"selector":{"env":"blue"}}}'
                        """
                        error "Metrics validation failed: Error Rate=${errorRate}, Latency=${latency}"
                    }
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Validated metrics in staging' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Metrics validation failed in staging for ${APP_NAME} build #${BUILD_NUMBER}! Rolled back to ${STAGING_STABLE_IMAGE}. Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 14: Check ServiceNow Approval
        stage('Check ServiceNow Approval') {
            steps {
                script {
                    echo "Checking ServiceNow approval for change request ${SNOW_CHANGE_ID}"
                    def maxRetries = 10
                    def retryCount = 0
                    def approved = false
                    while (retryCount < maxRetries && !approved) {
                        def snowResponse = httpRequest(
                            authentication: 'snow-creds',
                            httpMode: 'GET',
                            url: "${SNOW_URL}/api/now/table/${SNOW_CHANGE_TABLE}/${SNOW_CHANGE_ID}"
                        )
                        def snowChange = readJSON text: snowResponse.content
                        if (snowChange.result.state == 'approved') {
                            approved = true
                            echo "ServiceNow change request ${SNOW_CHANGE_ID} approved"
                        } else {
                            echo "Waiting for approval... Current state: ${snowChange.result.state}"
                            sleep 30
                            retryCount++
                        }
                    }
                    if (!approved) {
                        error "ServiceNow change request ${SNOW_CHANGE_ID} was not approved within timeout"
                    }
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] ServiceNow change request ${SNOW_CHANGE_ID} approved' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "ServiceNow approval failed for ${APP_NAME} build #${BUILD_NUMBER}! Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 15: Manual Approval for Prod
        stage('Manual Approval for Prod') {
            steps {
                script {
                    echo "Waiting for manual approval to deploy to production"
                    slackSend(
                        channel: "${SLACK_CHANNEL}",
                        token: "${SLACK_TOKEN}",
                        color: 'warning',
                        message: "Manual approval required for ${APP_NAME} v${APP_VERSION} deployment to production. Build #${BUILD_NUMBER}. Approve in Jenkins: ${env.BUILD_URL}"
                    )
                    input message: "Approve deployment to production?", ok: "Deploy"
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Manual approval received for prod deployment' >> ${AUDIT_LOG}"
                }
            }
        }

        // Stage 16: Deploy to Prod (Canary)
        stage('Deploy to Prod (Canary)') {
            steps {
                script {
                    echo "Deploying to production with canary strategy"
                    // Deploy canary with limited replicas
                    sh """
                        sed -i 's|image:.*|image: ${DOCKER_IMAGE}|' ${K8S_MANIFEST_DIR}/prod/deployment-canary.yaml
                        sed -i 's|replicas:.*|replicas: 1|' ${K8S_MANIFEST_DIR}/prod/deployment-canary.yaml
                        kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/prod/deployment-canary.yaml -n ${KUBE_NAMESPACE_PROD}
                        kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/prod/service-canary.yaml -n ${KUBE_NAMESPACE_PROD}
                    """
                    // Perform health check
                    echo "Checking canary health in production"
                    sh """
                        kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_PROD} wait --for=condition=ready pod -l app=${APP_NAME},canary=true --timeout=300s
                        curl -f http://${APP_NAME}-canary.${KUBE_NAMESPACE_PROD}.svc.cluster.local/health
                    """
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Deployed canary to prod' >> ${AUDIT_LOG}"
                }
            }
            post {
                success {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'good',
                            message: "Canary deployment successful for ${APP_NAME} v${APP_VERSION} in production! Build #${BUILD_NUMBER}"
                        )
                    }
                }
                failure {
                    script {
                        echo "Rolling back prod canary deployment"
                        sh """
                            kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_PROD} delete -f ${K8S_MANIFEST_DIR}/prod/deployment-canary.yaml || true
                            kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_PROD} delete -f ${K8S_MANIFEST_DIR}/prod/service-canary.yaml || true
                        """
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Prod canary deployment failed for ${APP_NAME} build #${BUILD_NUMBER}! Rolled back. Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 17: Validate Prod Canary Metrics
        stage('Validate Prod Canary Metrics') {
            steps {
                script {
                    echo "Validating canary metrics in production"
                    def errorRate = sh(script: """
                        curl -s '${PROMETHEUS_URL}/api/v1/query?query=rate(http_requests_total{app="${APP_NAME}", namespace="${KUBE_NAMESPACE_PROD}", canary="true", status=~"5.."}[5m])' | jq '.data.result[0].value[1]' || echo "0"
                    """, returnStdout: true).trim()
                    def latency = sh(script: """
                        curl -s '${PROMETHEUS_URL}/api/v1/query?query=histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{app="${APP_NAME}", namespace="${KUBE_NAMESPACE_PROD}", canary="true"}[5m]))' | jq '.data.result[0].value[1]' || echo "0"
                    """, returnStdout: true).trim()
                    echo "Prod Canary Error Rate: ${errorRate}, Latency (p95): ${latency}"
                    if (errorRate.toFloat() > 0.01 || latency.toFloat() > 0.5) {
                        echo "Canary metrics validation failed, initiating rollback"
                        sh """
                            kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_PROD} delete -f ${K8S_MANIFEST_DIR}/prod/deployment-canary.yaml || true
                            kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_PROD} delete -f ${K8S_MANIFEST_DIR}/prod/service-canary.yaml || true
                        """
                        error "Canary metrics validation failed: Error Rate=${errorRate}, Latency=${latency}"
                    }
                    // Promote canary to full deployment
                    echo "Promoting canary to full production deployment"
                    sh """
                        sed -i 's|image:.*|image: ${DOCKER_IMAGE}|' ${K8S_MANIFEST_DIR}/prod/deployment.yaml
                        sed -i 's|replicas:.*|replicas: 3|' ${K8S_MANIFEST_DIR}/prod/deployment.yaml
                        kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/prod/deployment.yaml -n ${KUBE_NAMESPACE_PROD}
                        kubectl --kubeconfig ${KUBE_CONFIG} apply -f ${K8S_MANIFEST_DIR}/prod/service.yaml -n ${KUBE_NAMESPACE_PROD}
                    """
                    // Update stable image
                    env.PROD_STABLE_IMAGE = "${DOCKER_IMAGE}"
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Validated canary metrics and promoted to prod' >> ${AUDIT_LOG}"
                }
            }
            post {
                success {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'good',
                            message: "Successfully promoted canary to full production deployment for ${APP_NAME} v${APP_VERSION}! Build #${BUILD_NUMBER}"
                        )
                    }
                }
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'danger',
                            message: "Prod canary metrics validation failed for ${APP_NAME} build #${BUILD_NUMBER}! Rolled back. Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 18: Cleanup Old Resources
        stage('Cleanup') {
            steps {
                script {
                    echo "Cleaning up old resources"
                    sh """
                        # Remove old canary deployments if necessary
                        kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_PROD} delete deployment ${APP_NAME}-canary --ignore-not-found=true
                        kubectl --kubeconfig ${KUBE_CONFIG} -n ${KUBE_NAMESPACE_STAGING} delete deployment ${APP_NAME}-blue --ignore-not-found=true
                        # Delete old images from registry (optional, based on retention policy)
                        # docker rmi ${DOCKER_IMAGE} || true
                    """
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Cleaned up old resources' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'warning',
                            message: "Cleanup stage failed for ${APP_NAME} build #${BUILD_NUMBER}! Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 19: Tag Git Repository
        stage('Tag Git Repository') {
            steps {
                script {
                    echo "Tagging Git repository with release version"
                    sshagent(['git-credentials']) {
                        sh '''
                            git config user.email "jenkins@example.com"
                            git config user.name "Jenkins"
                            git tag -a v${APP_VERSION} -m "Release v${APP_VERSION} for build #${BUILD_NUMBER}"
                            git push origin v${APP_VERSION}
                        '''
                    }
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Tagged Git repository with v${APP_VERSION}' >> ${AUDIT_LOG}"
                }
            }
            post {
                failure {
                    script {
                        slackSend(
                            channel: "${SLACK_CHANNEL}",
                            token: "${SLACK_TOKEN}",
                            color: 'warning',
                            message: "Git tagging failed for ${APP_NAME} build #${BUILD_NUMBER}! Check Jenkins: ${env.BUILD_URL}"
                        )
                    }
                }
            }
        }

        // Stage 20: Archive Audit Log
        stage('Archive Audit Log') {
            steps {
                script {
                    echo "Archiving audit log"
                    archiveArtifacts artifacts: 'audit.log', allowEmptyArchive: true
                    // Log to audit file
                    sh "echo '[${new Date().toString()}] Archived audit log' >> ${AUDIT_LOG}"
                }
            }
        }
    }

    // Post-build actions
    post {
        always {
            script {
                // Send final notification
                def status = currentBuild.currentResult == 'SUCCESS' ? 'good' : 'danger'
                def message = currentBuild.currentResult == 'SUCCESS' ?
                    "Pipeline completed successfully for ${APP_NAME} v${APP_VERSION}! Build #${BUILD_NUMBER}" :
                    "Pipeline failed for ${APP_NAME} v${APP_VERSION}! Build #${BUILD_NUMBER}. Check Jenkins: ${env.BUILD_URL}"
                slackSend(
                    channel: "${SLACK_CHANNEL}",
                    token: "${SLACK_TOKEN}",
                    color: status,
                    message: message
                )
                // Update Jira ticket
                if (env.JIRA_ISSUE_KEY) {
                    httpRequest(
                        authentication: 'jira-creds',
                        httpMode: 'POST',
                        url: "${JIRA_URL}/rest/api/2/issue/${JIRA_ISSUE_KEY}/comment",
                        requestBody: """
                            {
                                "body": "Pipeline ${currentBuild.currentResult} for build #${BUILD_NUMBER}. Jenkins URL: ${env.BUILD_URL}"
                            }
                        """,
                        contentType: 'APPLICATION_JSON'
                    )
                }
                // Update ServiceNow ticket
                if (env.SNOW_CHANGE_ID) {
                    httpRequest(
                        authentication: 'snow-creds',
                        httpMode: 'PUT',
                        url: "${SNOW_URL}/api/now/table/${SNOW_CHANGE_TABLE}/${SNOW_CHANGE_ID}",
                        requestBody: """
                            {
                                "state": "${currentBuild.currentResult == 'SUCCESS' ? 'closed' : 'canceled'}",
                                "close_notes": "Pipeline ${currentBuild.currentResult} for build #${BUILD_NUMBER}. Jenkins URL: ${env.BUILD_URL}"
                            }
                        """,
                        contentType: 'APPLICATION_JSON'
                    )
                }
            }
        }
        cleanup {
            script {
                // Clean workspace
                cleanWs()
                sh "echo '[${new Date().toString()}] Cleaned workspace' >> ${AUDIT_LOG}"
            }
        }
    }
}
