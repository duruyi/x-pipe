#!/bin/bash

###############################copy cert####################
CUR_DIR="$PWD"
CERT_DIR=$CUR_DIR/../cert
ls $CERT_DIR
mkdir -p /opt/data/100013684/openssl
#chown deploy:deploy /opt/data/100013684/openssl
cp $CERT_DIR/* /opt/data/100013684/openssl
#chown deploy:deploy /opt/data/100013684/openssl/*