FROM java:8
#FROM openjdk:11.0.4
COPY redis-proxy*.jar /xpipe-proxy/proxy.jar
COPY config/app.properties /xpipe-proxy/app.properties
COPY config/xpipe.properties /xpipe-proxy/xpipe.properties
COPY config/log4j2.xml /xpipe-proxy/config/log4j2.xml
COPY config/redis-proxy.conf /xpipe-proxy/redis-proxy.conf
COPY scripts/docker-entrypoint.sh /usr/local/bin/
#传递证书
COPY setup/cert/ /opt/data/100013684/openssl/
COPY setup/env_set/init.sh /xpipe-proxy/init.sh

RUN chmod a+x /xpipe-proxy/proxy.jar && bash /xpipe-proxy/init.sh
ENTRYPOINT ["docker-entrypoint.sh"]