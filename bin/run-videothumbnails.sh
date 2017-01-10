#!/bin/sh

cwd=`dirname "$0"`
expr "$0" : "/.*" > /dev/null || cwd=`(cd "$cwd" && pwd)`

BASE_DIR="${cwd}/.."
ASSET_NAME="<ASSET NAME>"
VIDEO_FILE="<SAMPLE VIDEO FILE PATH(ex. /path/sample.mp4)>"
AMS_CONF_FILE="${BASE_DIR}/app.config"
AMSMP_THUMB_PARAM_FILE="${BASE_DIR}/conf/default-videothumbnails.json"
BATCH_OUTPUT_DIR="<OUTPUT DIRECTORY>"

cd ${BASE_DIR}
mvn exec:java -Dexec.args="-t 16 -a ${ASSET_NAME} -f ${VIDEO_FILE} -c ${AMS_CONF_FILE} -p ${AMSMP_THUMB_PARAM_FILE} -o ${BATCH_OUTPUT_DIR} -d true"
