FROM java:8
COPY redis-keeper*.jar /xpipe-keeper/keeper.jar
ADD config/ /xpipe-keeper/config/
COPY scripts/docker-entrypoint.sh /usr/local/bin/

RUN chmod a+x /xpipe-keeper/keeper.jar
ENTRYPOINT ["docker-entrypoint.sh"]