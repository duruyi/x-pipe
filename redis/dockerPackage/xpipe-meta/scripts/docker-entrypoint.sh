#!/bin/bash

#vars
LOCAL_PATH=config
SERVICE_NAME=meta
XPIPE_CONF=xpipe
CONSOLEJQ_URL=consolejq:8080
CONSOLEOY_URL=consoleoy:8080

XPIPE_ROOT_DIR=/xpipe-meta
LOG_DIR=/opt/logs/100004375


#functions
function changeDataCenter(){
    newDc=$1
    sed -i  "s/\(datacenter=\)jq/\1$newDc/g" $LOCAL_PATH/$XPIPE_CONF.properties
    sed -i  "s/\(metaserver-local-\)jq/\1$newDc/g" $LOCAL_PATH/$XPIPE_CONF.properties
}

function makedir(){
    if [ ! -d $1 ]; then
        echo "log dir not exist, create it"
        mkdir -p $1
    fi
}

makedir $LOG_DIR

cd $XPIPE_ROOT_DIR

#until [[ "$(curl -X GET --silent  --connect-timeout 1 --head $CONSOLEJQ_URL | grep "Coyote")" != ""  && "$(curl -X GET --silent --connect-timeout 1 --head $CONSOLEOY_URL | grep "Coyote")" != "" ]];
#do
#    echo "console is unaviable - sleeping"
#    sleep 10
#done

if [ $CONSOLE_ADDRESS ];then
    sed -i "s#http://consolejq:8080#$CONSOLE_ADDRESS#g" $LOCAL_PATH/$XPIPE_CONF.properties
fi

if [ $DATACENTER ];then
    changeDataCenter $DATACENTER
fi

if [ $METASERVER_ID ];then
    sed -i  "s/\(metaserver.id=\)1/\1$METASERVER_ID/g" $LOCAL_PATH/$XPIPE_CONF.properties
fi

if [ $ZKADDRESS ];then
    sed -i  "s#zoo1:2181#$ZKADDRESS#g" $LOCAL_PATH/$XPIPE_CONF.properties
fi

./$SERVICE_NAME.jar start