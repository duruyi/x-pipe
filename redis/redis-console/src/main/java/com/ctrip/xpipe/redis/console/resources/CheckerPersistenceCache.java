package com.ctrip.xpipe.redis.console.resources;

import com.ctrip.xpipe.api.email.EmailResponse;
import com.ctrip.xpipe.api.server.Server;
import com.ctrip.xpipe.redis.checker.CheckerConsoleService;
import com.ctrip.xpipe.redis.checker.alert.AlertMessageEntity;
import com.ctrip.xpipe.redis.checker.config.CheckerConfig;
import com.ctrip.xpipe.redis.checker.healthcheck.RedisHealthCheckInstance;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

public class CheckerPersistenceCache extends AbstractPersistenceCache {
    CheckerConsoleService service;
    public CheckerPersistenceCache(CheckerConfig config, CheckerConsoleService service, ScheduledExecutorService scheduled) {
        super(config, scheduled);
        this.service = service;
    }

    @Override
    public boolean isClusterOnMigration(String clusterId) {
        return service.isClusterOnMigration(config.getConsoleAddress(), clusterId);
    }

    @Override
    public void updateRedisRole(RedisHealthCheckInstance instance, Server.SERVER_ROLE role) {
        service.updateRedisRole(config.getConsoleAddress(), instance, role);
    }

    @Override
    public void recordAlert(AlertMessageEntity message, EmailResponse response) {
        service.recordAlert(config.getConsoleAddress(), message, response);
    }

    @Override
    Set<String> doSentinelCheckWhiteList() {
        return service.sentinelCheckWhiteList(config.getConsoleAddress());
    }

    @Override
    Set<String> doClusterAlertWhiteList() {
        return service.clusterAlertWhiteList(config.getConsoleAddress());
    }

    @Override
    boolean doIsSentinelAutoProcess() {
        return service.isSentinelAutoProcess(config.getConsoleAddress());
    }

    @Override
    boolean doIsAlertSystemOn() {
        return service.isAlertSystemOn(config.getConsoleAddress());
    }

    @Override
    Map<String, Date> doLoadAllClusterCreateTime() {
        return service.loadAllClusterCreateTime(config.getConsoleAddress());
    }
}