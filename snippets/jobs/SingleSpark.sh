#!/bin/bash
# test experimental 
# 
# This script uses the tight integration of openmpi-1.4.3-intel-12.1 in SGE
# using the parallel environment (PE) "orte".  
# This script must be used only with qsub command - do NOT run it as a stand-alone 
# shell script because it will start all processes on the local node.   
# Define your job name, parallel environment with the number of slots, and run time: 
#$ -cwd
#$ -j y
#$ -pe smp 1
#$ -M kevinmader+merlinsge@gmail.com
#$ -m ae
#$ -o singlesparkworker.log
#$ -l mem_free=22G,ram=8G,s_rt=0:59:00,h_rt=1:00:00
###################################################
# Fix the SGE environment-handling bug (bash):
source /usr/share/Modules/init/sh
export -n -f module


# Load the environment modules for this job (the order may be important): 

###################################################
# Set the environment variables:

export CLASSPATH=/afs/psi.ch/project/tipl/jar/TIPL.jar
JCMD="/afs/psi.ch/project/tipl/spark/bin/spark-class"
SPCMD="org.apache.spark.deploy.worker.Worker -d /scratch -m 16G -c 1"

##############
# BEGIN DEBUG 
# Print the SGE environment on master host: 
echo "================================================================"
echo "=== SGE job  JOB_NAME=$JOB_NAME  JOB_ID=$JOB_ID"
echo "================================================================"
echo DATE=`date`
echo HOSTNAME=`hostname`
echo PWD=`pwd`
echo "NSLOTS=$NSLOTS"
echo "PE_HOSTFILE=$PE_HOSTFILE"
cat $PE_HOSTFILE
free -m
echo "================================================================"
echo "================================================================"
echo "Running environment: $@"
echo "================================================================"
echo "Running Program"
MakeSparkWorker="$JCMD $SPCMD $@"
echo "$MakeSparkWorker"
$MakeSparkWorker
echo "+++++ Spark Complete, Remaining TempFiles"
ls -lh /scratch/

###################################################
