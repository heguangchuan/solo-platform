def dockerRegistry = "solo-platform"

// 部署目录：优先用 Jenkins 工作区（仓库根，docker-compose.yml 在此），
// 回退到历史硬编码路径，保证兼容。
def getDeployPath(env) {
    return env.WORKSPACE ?: "/root/${dockerRegistry}"
}

def getBranchName(env) {
    return env.BRANCH_NAME
}

// 解析 Spring profile：
//  - 多分支流水线才有 BRANCH_NAME（local/mock/prod 等分支名即 profile）
//  - 普通流水线 BRANCH_NAME 为 null，退化为 prod（生产默认）
// 注意：若使用多分支且分支名不是 local/mock/prod，需保证存在对应的
//       application-<分支>.yaml，否则请直接把这里写成固定 'prod'。
def resolveProfile(env) {
    def branch = getBranchName(env)
    if (branch && branch != 'null' && branch.trim()) {
        return branch.trim()
    }
    return 'prod'
}

def getPath(serviceName) {
    def pathSplit = serviceName.split("/")
    return pathSplit[pathSplit.length - 1].trim()
}

def buildServiceList = []

pipeline {
    agent any
    tools {
        maven "maven"
        jdk "jdk21"
    }
    parameters {
        extendedChoice(
            name: "buildServices",
            type: "PT_CHECKBOX",
            description: "选择需要部署的服务模块",
            quoteValue: false,
            saveJSONParameterToFile: false,
            value: "solo",
            visibleItemCount: 20,
            multiSelectDelimiter: ",",
            defaultValue: "solo"
        )
        string(
            description: '镜像版本',
            name: 'version',
            defaultValue: '1.0.0'
        )
    }
    stages {
        stage('prepare') {
            steps {
                script {
                    echo "部署的服务为: ${buildServices}"
                    buildServiceList = Arrays.asList(buildServices.split(",")).collect { it.trim() }
                    def profile = resolveProfile(env)
                    echo "执行构建任务： ${getBranchName(env)}_${env.BUILD_ID}，profile=${profile}"

                    // 仅 solo 是 Maven 模块，必须在模块目录内构建（仓库根无 pom.xml）。
                    // 该步骤主要用于提前暴露编译错误；Dockerfile 构建阶段也会在容器内重新编译。
                    // 若 Jenkins 代理未安装 Maven/JDK，可整体删除本 stage。
                    dir("${env.WORKSPACE}/solo") {
                        sh label: 'mvn package', script: "mvn clean package -DskipTests=true"
                    }
                }
            }
        }

        stage('build') {
            options {
                timeout(time: 30, unit: 'MINUTES')
            }
            steps {
                script {
                    def profile = resolveProfile(env)
                    for (String serviceName in buildServiceList) {
                        dir("${env.WORKSPACE}/${serviceName}") {
                            def imageName = getPath(serviceName)
                            def lastName = "${dockerRegistry}/${imageName}:${version}"
                            sh label: 'docker build', script: "docker build --build-arg profile=${profile} -t ${lastName} ."
                        }
                    }
                }
            }
        }

        stage('deploy') {
            options {
                timeout(time: 30, unit: 'MINUTES')
            }
            steps {
                script {
                    def deployPath = getDeployPath(env)
                    dir(deployPath) {
                        for (String serviceName in buildServiceList) {
                            def imageName = getPath(serviceName)
                            def lastName = "${dockerRegistry}/${imageName}:${version}"
                            sh label: 'sed file', script: "sed -i 's/${imageName}:[0-9.]\\+/${imageName}:${version}/g' docker-compose.yml"
                        }
                        sh label: 'docker-compose-up', script: "docker compose up -d"
                    }
                }
            }
        }
    }
}
