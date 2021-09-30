FROM java:8
COPY redis-meta*.jar /xpipe-meta/meta.jar
ADD config /xpipe-meta/config
COPY scripts/docker-entrypoint.sh /usr/local/bin/

RUN chmod a+x /xpipe-meta/meta.jar
ENTRYPOINT ["docker-entrypoint.sh"]