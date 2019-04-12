#!/bin/bash

# dynamically grab jar name in /usr/src/app/cli/target/
JAR_FILE=$(find /usr/src/app/cli/target -name \*executable.jar)

module=$1
filename=${1:-swagger}

if [ -z ${1+x} ]; then
  echo 'no module selected. skipping.'
elif [ "$module" == "EXTRACT_JAR" ]; then
  echo "cp ${JAR_FILE} /usr/src/yang"
  cp ${JAR_FILE} /usr/src/yang
else
  echo "java -jar ${JAR_FILE} ${module} -api-version 2.0 -format JSON -yang-dir /usr/src/yang -output /usr/src/yang/${filename}.json"
  java -jar ${JAR_FILE} ${module} -api-version 2.0 -format JSON -yang-dir /usr/src/yang -output /usr/src/yang/${filename}.json
fi
