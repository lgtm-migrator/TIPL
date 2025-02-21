#!/bin/bash
# test experimental 
# 
# This script uses the tight integration of openmpi-1.4.3-intel-12.1 in SGE
# using the parallel environment (PE) "orte".  
# This script must be used only with qsub command - do NOT run it as a stand-alone 
# shell script because it will start all processes on the local node.   
# Define your job name, parallel environment with the number of slots, and run time: 
#$ -cwd
#$ -N ULPOR
#$ -j y
#$ -M maderk@student.ethz.ch
#$ -pe smp 2
#$ -o ULPOR.log
#$ -l mem_free=16G,s_rt=23:59:00,h_rt=24:00:00
###################################################
# Fix the SGE environment-handling bug (bash):
source /usr/share/Modules/init/sh
export -n -f module


# Load the environment modules for this job (the order may be important): 

###################################################
# Set the environment variables:

export CLASSPATH=/gpfs/home/mader/jar/jai_imageio.jar:/gpfs/home/mader/jar/jai_core.jar:/gpfs/home/mader/jar/jai_codec.jar:/gpfs/home/mader/jar/ShapeTools.jar:/gpfs/home/mader/jar/Jama.jar:/gpfs/home/mader/jar/TIPLScripts.jar:/gpfs/home/mader/jar/Fiji.app/jars/ij.jar
JCMD="/usr/bin/java"
JARGS="-d64 -Xmx16G -Xms15G" 



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
echo "Running environment: Theshold=$1, Mask=$2 "
env
echo "================================================================"
echo "Running Program"
echo "+++++ Running CLPOR on labels lab "
TIPLCMD="CLPOR -thresh=$1 -mask=$2 -labelneighbors -stage=1 -minVolume=5"
CLPORCMD="$JCMD $JARGS $TIPLCMD"
echo "$CLPORCMD"
$CLPORCMD
###################################################
