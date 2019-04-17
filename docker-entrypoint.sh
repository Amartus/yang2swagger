#!/bin/bash

# dynamically grab jar name in /usr/src/app/cli/target/
JAR_FILE=$(find /usr/src/app/cli/target -name \*executable.jar)

if [ -z ${1+x} ]; then
  echo 'No module selected. Skipping.'
elif [ "$1" == "EXTRACT_JAR" ]; then
  echo "cp ${JAR_FILE} /usr/src/yang"
  cp ${JAR_FILE} /usr/src/yang
else
  echo "java -jar ${JAR_FILE} ${1}"
  java -jar ${JAR_FILE} ${1}
fi
