#!groovy

import hudson.model.*
import jenkins.model.*


import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

import hudson.model.FreeStyleProject
import hudson.tasks.ArtifactArchiver

import hudson.plugins.parameterizedtrigger.*

// groovy script to run 3 jobs:
// flaskBuild
// njinxImageBuild
// executeEnvironments


// Declare variables for the job
def gitUrl = "https://github.com/user/repo.git"
def dockerhub_PWD = null
def dockerhub_USR = null
def verificationUrl = 'http://localhost/containers'
def stringClass = '$class'
def stringUsername = '$USERNAME'


def createJob(name, script) {
  def instance = Jenkins.getInstance()
  def job = instance.getItem(name)
  

  if ( job == null) {
    job = instance.createProject(WorkflowJob, name)
  } 

  job.definition = new CpsFlowDefinition(script, true)
  job.save()

  println "${name} created"
}

def synchronizedProject (project) {
    def isRunning = project.getLastBuild().isInProgress()
    println "isRunning? ${isRunning}"

    long startTime = System.currentTimeMillis()
    long timeout = 30 * 1000  // 30 seconds in milliseconds
    while (!isRunning && (System.currentTimeMillis() - startTime < timeout)) {
        try {
            this.wait(timeout - (System.currentTimeMillis() - startTime))
            isRunning = project.getLastBuild().isInProgress()
            println "waiting to sync"
        } catch (InterruptedException e) {
            println "ctach in while"
            // handle interruption
        }
    }
  // Perform action appropriate to condition or timeout
}


@NonCPS
def getLastCompletedBuild(project, isScheduled) {
    println "getLastCompletedBuild ...1 "
    println "Build scheduled? ${isScheduled}"

    if(isScheduled){
      synchronizedProject(project)
    }
    // def lastCompletedBuild = project.getLastCompletedBuild()  
    def lastCompletedBuild = project.getLastCompletedBuild() 
    def lastBuild = project.getLastBuild()  
    
     println "lastCompletedBuild ...${lastCompletedBuild} "
     println "lastBuild ...${lastCompletedBuild} "
     
    def isInProgress = lastCompletedBuild.isInProgress()
    
    while ( lastCompletedBuild == null ) {
        sleep(100)
        println "waiting ... "
        lastCompletedBuild = project.getLastCompletedBuild()
    }
    return lastCompletedBuild
}

def runDependendJobs(){
  
  def upstreamProject1 = Hudson.instance.getItem("flaskBuild")
  def upstreamProject2 = Hudson.instance.getItem("nginxBuild")
  def downstreamProject = Hudson.instance.getItem("dslRunAndVerify")

 if (upstreamProject1 != null && upstreamProject2 != null && downstreamProject != null) {
    // trigger builds for the upstream projects
    def prjOne = upstreamProject1.scheduleBuild(new Cause.UserIdCause())
    def prjSecond = upstreamProject2.scheduleBuild(new Cause.UserIdCause())

      
    // wait for the upstream builds to complete

    def build1 = getLastCompletedBuild(upstreamProject1, prjOne)
    def build2 = getLastCompletedBuild(upstreamProject2, prjSecond)

    println "Builds done ... "
    // check the build results for the upstream projects
    def build1Result = build1.getResult()
    def build2Result = build2.getResult()

    if (build1Result == Result.SUCCESS && build2Result == Result.SUCCESS) {
        // trigger the downstream build
        downstreamProject.scheduleBuild(new Cause.UpstreamCause(build1))
    }
  }
}


createJob("flaskBuild", """
pipeline {
  agent any
  environment {
    dockerhub = credentials('dockerhub')
  }
  stages {
    stage('Checkout code from Git repository') {
      steps {
        git branch: 'main', credentialsId: '70da42b3-4632-4314-bcf5-522c5866760d', url: 'https://github.com/BaruchiHalamish20/jenkinsDSL'
      }
    }
    
    stage('DockerHub Build and push') {
			steps {
        script {
          withCredentials([[$stringClass: 'UsernamePasswordMultiBinding', credentialsId: 'dockerhubc', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]){
            dockerImageFlask = docker.build("$stringUsername/bflask:latest", "./flask")
            dockerImageFlask.push()
          }
        }
        
			}
		}
     
  }
}
""")


createJob("nginxBuild", """
pipeline {
  agent any
  environment {
    dockerhub = credentials('dockerhub')
  }
  stages {
    stage('Checkout code from Git repository') {
      steps {
        git branch: 'main', credentialsId: '70da42b3-4632-4314-bcf5-522c5866760d', url: 'https://github.com/BaruchiHalamish20/jenkinsDSL'
      }
    }
    
    stage('DockerHub Build and push') {
			steps {
        script {
          withCredentials([[$stringClass: 'UsernamePasswordMultiBinding', credentialsId: 'dockerhubc', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]){
            dockerImageNginx = docker.build("$stringUsername/bnginx:latest", "./nginx")
            dockerImageNginx.push()
          }
        }
			}
		}
     
  }
}
""")

createJob("dslRunAndVerify", """
pipeline {
  agent any
  stages {
    stage('Checkout code from Git repository') {
      steps {
        git branch: 'main', credentialsId: '70da42b3-4632-4314-bcf5-522c5866760d', url: 'https://github.com/BaruchiHalamish20/jenkinsDSL'
      }
    }  
    stage('Run docker-compose') {
      steps {
        sh "pwd"
        sh "sed -i s/JENKINS_SERVER_IP_ORIG/`cat /tmp/JENKINS_SERVER_IP`/ docker-compose.yaml" 
        sh " docker-compose rm -f"  
        sh "docker-compose up -d"
        sh "date" 
        sh "echo 'docker-compose'"
      }
    }
    stage('Verification') {
      steps {
        script {
          sh "date" 
          sh "echo 'verification'"
          def response = sh(script: "curl ${verificationUrl} | grep -q '404 Not Found' && echo '404' || echo '1'", returnStdout: true)
          if (response == "404") {
            println 'Failure - ${verificationUrl} not Found'
            exit 404
          } else {
            println 'Success - ${verificationUrl} Found'
          }
        }
      }
    }
  }
}

""")

runDependendJobs()