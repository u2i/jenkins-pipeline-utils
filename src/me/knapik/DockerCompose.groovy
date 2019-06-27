package me.knapik

import org.jenkinsci.plugins.workflow.cps.CpsScript

class DockerCompose implements Serializable {
    private final String projectName;
    private final String serviceName;
    private final Boolean build;
    private CpsScript script;
    private Integer uid;
    private Integer gid;
    private String group;

    def DockerCompose(String projectName, Boolean build, CpsScript script) {
        this(projectName, "", build, script)
    }

    def DockerCompose(String projectName, CpsScript script) {
        this(projectName, "", true, script)
    }

    def DockerCompose(String projectName, String serviceName, CpsScript script) {
        this(projectName, serviceName, true, script)
    }

    def DockerCompose(String projectName, String serviceName, Boolean build, CpsScript script) {
        this.projectName = projectName
        this.script = script
        this.build = build
        this.serviceName = serviceName
    }

    def createLocalUser(String service) {
        execRoot(service, "addgroup --quiet --gid ${fetchGid()} ${fetchGroup()}")
        execRoot(service, "adduser  --quiet --no-create-home --disabled-password --gecos '' `whoami` --uid ${fetchUid()} --gid ${fetchGid()}")
    }

    def exec(String service, String cmd) {
        exec(service, fetchUid(), cmd)
    }

    def execRoot(String service, String cmd) {
        exec(service, 'root', cmd)
    }

    def exec(String service, Integer uid, String cmd) {
        exec(service, uid.toString(), cmd)
    }

    def exec(String service, String user, String cmd) {
        script.sh """
            docker-compose -p $projectName exec --user $user -T $service sh -c "$cmd"
        """
    }

    def within(Closure cl) {    
        def escapedBranchName = script.env.BRANCH_NAME.replaceAll(/[^a-zA-Z0-9_.-]/, '__')
        def buildDockerTag = "${escapedBranchName}-${script.env.BUILD_ID}"
        script.withEnv(["HOST_UID=${fetchUid()}", "BUILD_DOCKER_TAG=${buildDockerTag}"]) {
            try {
                script.echo 'docker-compose up'
                up()

                cl(this)
            } finally {
                script.echo 'docker-compose down'
                down()
            }
        }
    }

    def up() {
        String build = this.build ? '--build' : ''
        script.sh "docker-compose -p $projectName up ${build} -d $serviceName"
    }

    def down() {
        script.sh "docker-compose -p $projectName down --rmi local"
    }

    private Integer fetchUid() {
        if(uid == null) {
            this.uid = detectUid()
        }
        return uid
    }

    private Integer fetchGid() {
        if(gid == null) {
            this.gid = detectGid()
        }
        return gid
    }

    private String fetchGroup() {
        if(group == null) {
            this.group = detectGroup()
        }
        return group
    }

    private Integer detectUid() {
        String tmpFile = ".jenkins_uid_${uuid()}"
        try {
            script.sh "id -u > $tmpFile"
            return Integer.parseInt(script.readFile(tmpFile).trim())
        } finally {
            script.sh "rm -f $tmpFile"
        }
    }

    private Integer detectGid() {
        String tmpFile = ".jenkins_gid_${uuid()}"
        try {
            script.sh "id -g > $tmpFile"
            return Integer.parseInt(script.readFile(tmpFile).trim())
        } finally {
            script.sh "rm -f $tmpFile"
        }
    }

    private String detectGroup() {
        String tmpFile = ".jenkins_group_${uuid()}"
        try {
            script.sh "getent group ${fetchGid()} | cut -d: -f1 > $tmpFile"
            return script.readFile(tmpFile).trim()
        } finally {
            script.sh "rm -f $tmpFile"
        }
    }

    private java.util.UUID uuid() {
        java.util.UUID.randomUUID()
    }
}
