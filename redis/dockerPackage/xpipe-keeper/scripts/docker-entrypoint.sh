#!/bin/bash

#vars
SERVICE_NAME=keeper
XPIPE_CONF=xpipe
XPIPE_ROOT_DIR=/xpipe-keeper

cd $XPIPE_ROOT_DIR

#functions
function changeDataCenter(){
    newDc=$1
    sed -i  "s/\(datacenter=\)jq/\1$newDc/g" $XPIPE_CONF.properties
    sed -i  "s/\(metaserver-local-\)jq/\1$newDc/g" $XPIPE_CONF.properties
}


if [ $DATACENTER ];then
    changeDataCenter $DATACENTER
fi

if [ $ZKADDRESS ];then
    sed -i  "s#zoo1:2181#$ZKADDRESS#g" $XPIPE_CONF.properties
fi

./$SERVICE_NAME.jar start