package com.ctrip.xpipe.redis.console.resources;

import com.ctrip.xpipe.redis.checker.PersistenceCache;
import com.ctrip.xpipe.redis.checker.cache.TimeBoundCache;
import com.ctrip.xpipe.redis.checker.config.CheckerConfig;
import com.ctrip.xpipe.utils.job.DynamicDelayPeriodTask;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

public abstract class AbstractPersistenceCache implements PersistenceCache {
    protected CheckerConfig config;

    DynamicDelayPeriodTask loadCacheTask;
    TimeBoundCache<Set<String>> sentinelCheckWhiteListCache;
    TimeBoundCache<Set<String>> clusterAlertWhiteListCache;
    TimeBoundCache<Boolean> isSentinelAutoProcessCache;
    TimeBoundCache<Boolean> isAlertSystemOnCache;
    TimeBoundCache<Map<String, Date>> allClusterCreateTimeCache;

//    Map<String, TimeBoundCache<List<ProxyEndpoint>>> monitorActiveProxiesCache;
    abstract Set<String> doSentinelCheckWhiteList();
    abstract Set<String> doClusterAlertWhiteList();
    abstract boolean doIsSentinelAutoProcess();
    abstract boolean doIsAlertSystemOn();
    abstract Map<String, Date> doLoadAllClusterCreateTime();
    public AbstractPersistenceCache(CheckerConfig config, ScheduledExecutorService scheduled) {
        this.config = config;
        this.sentinelCheckWhiteListCache = new TimeBoundCache<>(config::getConfigCacheTimeoutMilli, this::doSentinelCheckWhiteList);
        this.clusterAlertWhiteListCache = new TimeBoundCache<>(config::getConfigCacheTimeoutMilli, this::doClusterAlertWhiteList);
        this.isSentinelAutoProcessCache = new TimeBoundCache<>(config::getConfigCacheTimeoutMilli, this::doIsSentinelAutoProcess);
        this.isAlertSystemOnCache = new TimeBoundCache<>(config::getConfigCacheTimeoutMilli, this::doIsAlertSystemOn);
        this.allClusterCreateTimeCache = new TimeBoundCache<>(config::getConfigCacheTimeoutMilli, this::doLoadAllClusterCreateTime);
        this.loadCacheTask = new DynamicDelayPeriodTask("persistenceCacheLoader", this::loadCache, config::getCheckerMetaRefreshIntervalMilli, scheduled);
    }

    private void loadCache() {
        //update
        sentinelCheckWhiteListCache.getData(true);
        clusterAlertWhiteListCache.getData(true);
        isSentinelAutoProcessCache.getData(true);
        isAlertSystemOnCache.getData(true);
        allClusterCreateTimeCache.getData(true);
    }

//    @Override
//    public boolean isClusterOnMigration(String clusterId) {
//        //TODO will call redis db9 get master idc
//        return persistence.isClusterOnMigration(clusterId);
//    }

//    @Override
//    public void updateRedisRole(RedisHealthCheckInstance instance, Server.SERVER_ROLE role) {
//        //no cache
//        this.persistence.updateRedisRole(instance, role);
//    }

//    @Override
//    public void recordAlert(AlertMessageEntity message, EmailResponse response) {
//        this.persistence.recordAlert(message, response);
//    }

    @Override
    public Set<String> sentinelCheckWhiteList() {
        return sentinelCheckWhiteListCache.getData(true);
    }

    @Override
    public Set<String> clusterAlertWhiteList() {
        return clusterAlertWhiteListCache.getData(true);
    }

    @Override
    public boolean isSentinelAutoProcess() {
        return isSentinelAutoProcessCache.getData(true);
    }

    @Override
    public boolean isAlertSystemOn() {
        return isAlertSystemOnCache.getData(true);
    }

    @Override
    public Date getClusterCreateTime(String clusterId) {
        Map<String, Date> dates = allClusterCreateTimeCache.getData(true);
        return dates.get(clusterId);
    }

    @Override
    public Map<String, Date> loadAllClusterCreateTime() {
        return allClusterCreateTimeCache.getData(true);
    }

}
