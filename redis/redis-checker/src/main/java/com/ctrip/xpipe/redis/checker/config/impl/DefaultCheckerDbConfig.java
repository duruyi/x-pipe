package com.ctrip.xpipe.redis.checker.config.impl;

import com.ctrip.xpipe.redis.checker.PersistenceCache;
import com.ctrip.xpipe.redis.checker.alert.AlertDbConfig;
import com.ctrip.xpipe.redis.checker.cache.TimeBoundCache;
import com.ctrip.xpipe.redis.checker.config.CheckerConfig;
import com.ctrip.xpipe.redis.checker.config.CheckerDbConfig;
import com.ctrip.xpipe.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * @author lishanglin
 * date 2021/3/13
 */
public class DefaultCheckerDbConfig implements CheckerDbConfig, AlertDbConfig {

    private PersistenceCache persistenceCache;

    private TimeBoundCache<Set<String>> sentinelCheckWhiteListCache;
    

    private TimeBoundCache<Set<String>> clusterAlertWhiteListCache;

    public DefaultCheckerDbConfig(PersistenceCache persistenceCache, LongSupplier timeoutMilliSupplier) {
        this.persistenceCache = persistenceCache;
        sentinelCheckWhiteListCache = new TimeBoundCache<>(timeoutMilliSupplier,
                () -> this.lowCaseClusters(persistenceCache.sentinelCheckWhiteList()));
        clusterAlertWhiteListCache = new TimeBoundCache<>(timeoutMilliSupplier,
                () -> this.lowCaseClusters(persistenceCache.clusterAlertWhiteList()));
    }
    

    private Set<String> lowCaseClusters(Set<String> clusters) {
        return clusters.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    @Autowired
    public DefaultCheckerDbConfig(PersistenceCache persistence, CheckerConfig config) {
        this(persistence, config::getConfigCacheTimeoutMilli);
    }

    @Override
    public boolean isAlertSystemOn() {
        return persistenceCache.isAlertSystemOn();
    }

    @Override
    public boolean isSentinelAutoProcess() {
        return persistenceCache.isSentinelAutoProcess();
    }

    @Override
    public boolean shouldSentinelCheck(String cluster) {
        if (StringUtil.isEmpty(cluster)) return false;

        Set<String> whiteList = sentinelCheckWhiteList();
        return null != whiteList && !whiteList.contains(cluster.toLowerCase());
    }

    @Override
    public Set<String> sentinelCheckWhiteList() {
        return this.sentinelCheckWhiteListCache.getData(true);
    }

    @Override
    public boolean shouldClusterAlert(String cluster) {
        if (StringUtil.isEmpty(cluster)) return false;

        Set<String> whiteList = clusterAlertWhiteList();
        return null != whiteList && !whiteList.contains(cluster.toLowerCase());
    }

    @Override
    public Set<String> clusterAlertWhiteList() {
        return clusterAlertWhiteListCache.getData(true);
    }
}
