#!/bin/bash

# export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.7.0_75.jdk/Contents/Home
cd ..
nohup $JAVA_HOME/bin/java -Dapple.awt.UIElement=false -Dlog4j.properties=conf/log4j.properties -jar target/hipchatbuddy-0.0.1*.jar &> /dev/null &
