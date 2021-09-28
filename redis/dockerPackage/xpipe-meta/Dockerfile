FROM java:8
COPY redis-meta*.jar /xpipe-meta/meta.jar
COPY config/app.properties /xpipe-meta/app.properties
COPY config/xpipe.properties /xpipe-meta/xpipe.properties
COPY config/log4j2.xml /xpipe-meta/config/log4j2.xml
COPY config/redis-meta.conf /xpipe-meta/redis-meta.conf
COPY scripts/docker-entrypoint.sh /usr/local/bin/

RUN chmod a+x /xpipe-meta/meta.jar
ENTRYPOINT ["docker-entrypoint.sh"]