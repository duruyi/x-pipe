FROM java:8
COPY redis-console*.jar /xpipe-console/console.jar
COPY config/datasources.xml /xpipe-console/datasources.xml
COPY config/app.properties /xpipe-console/app.properties
COPY config/xpipe.properties /xpipe-console/xpipe.properties
COPY config/log4j2.xml /xpipe-console/config/log4j2.xml
COPY config/redis-console.conf /xpipe-console/redis-console.conf
COPY scripts/docker-entrypoint.sh /usr/local/bin/

RUN chmod a+x /xpipe-console/console.jar
ENTRYPOINT ["docker-entrypoint.sh"]