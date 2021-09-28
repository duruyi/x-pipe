#!/bin/bash

#vars
SERVICE_NAME=proxy
XPIPE_CONF=xpipe

XPIPE_ROOT_DIR=/xpipe-proxy
cd $XPIPE_ROOT_DIR && ./$SERVICE_NAME.jar start

