FROM java:8
COPY redis-keeper*.jar /xpipe-keeper/keeper.jar
COPY config/app.properties /xpipe-keeper/app.properties
COPY config/xpipe.properties /xpipe-keeper/xpipe.properties
COPY config/log4j2.xml /xpipe-keeper/config/log4j2.xml
COPY config/redis-keeper.conf /xpipe-keeper/redis-keeper.conf
COPY scripts/docker-entrypoint.sh /usr/local/bin/

RUN chmod a+x /xpipe-keeper/keeper.jar
ENTRYPOINT ["docker-entrypoint.sh"]