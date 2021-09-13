package com.ctrip.xpipe.redis.integratedtest.checker;


import com.ctrip.xpipe.api.command.Command;
import com.ctrip.xpipe.api.email.EmailResponse;
import com.ctrip.xpipe.api.pool.SimpleObjectPool;
import com.ctrip.xpipe.api.server.Server;
import com.ctrip.xpipe.cluster.ClusterType;
import com.ctrip.xpipe.endpoint.DefaultEndPoint;
import com.ctrip.xpipe.endpoint.HostPort;
import com.ctrip.xpipe.netty.commands.NettyClient;
import com.ctrip.xpipe.pool.XpipeNettyClientKeyedObjectPool;
import com.ctrip.xpipe.redis.checker.CheckerConsoleService;
import com.ctrip.xpipe.redis.checker.alert.AlertMessageEntity;
import com.ctrip.xpipe.redis.checker.healthcheck.RedisHealthCheckInstance;
import com.ctrip.xpipe.redis.checker.healthcheck.config.DefaultHealthCheckConfig;
import com.ctrip.xpipe.redis.checker.healthcheck.impl.DefaultRedisHealthCheckInstance;
import com.ctrip.xpipe.redis.checker.healthcheck.impl.DefaultRedisInstanceInfo;
import com.ctrip.xpipe.redis.checker.healthcheck.session.RedisSession;
import com.ctrip.xpipe.redis.checker.resource.DefaultCheckerConsoleService;
import com.ctrip.xpipe.redis.core.entity.RedisMeta;
import com.ctrip.xpipe.redis.core.protocal.cmd.AbstractSentinelCommand;
import com.ctrip.xpipe.redis.integratedtest.metaserver.AbstractMetaServerMultiDcTest;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.SERVER_MODE.*;

public class CheckerTest extends AbstractMetaServerMultiDcTest{
    public Map<String, ConsoleInfo> defaultConsoleInfo() {
        Map<String, ConsoleInfo> consoleInfos = new HashMap<>();
        //start console + checker 2server
        consoleInfos.put("jq", new ConsoleInfo(CONSOLE).setConsole_port(18080).setChecker_port(28080));
        //start conset_checker 1server
        consoleInfos.put("oy", new ConsoleInfo(CONSOLE_CHECKER).setConsole_port(18081).setChecker_port(28081));
        //start checker 1 server
        consoleInfos.put("fra", new ConsoleInfo(CHECKER).setConsole_port(18080).setChecker_port(28082));
        return consoleInfos;
    }
    
    XpipeNettyClientKeyedObjectPool pool;
    
    @Before
    public void testBefore() throws Exception {
        startCRDTAllServer(defaultConsoleInfo());
        pool = getXpipeNettyClientKeyedObjectPool();
    }
    
    @Test
    public void SentinelCheck() throws Exception {
       testSentinel("jq", 5000);
       testSentinel("oy", 17170);
       testSentinel("fra", 32222);
    }
    
    public void testSentinel(String idc, int sentinel_port) throws Exception {
        final String sentinelMaster = "will-remove-master-name";
        final String localHost = "127.0.0.1";
        final int localPort = 6379;
        final int waitTime = 2000;
        
        SimpleObjectPool<NettyClient> clientPool = pool.getKeyPool(new DefaultEndPoint(localHost, sentinel_port));
        String addResult = new AbstractSentinelCommand.SentinelAdd(clientPool, sentinelMaster, localHost, localPort, 3, scheduled).execute().get();
        HostPort master = new AbstractSentinelCommand.SentinelMaster(clientPool, scheduled, sentinelMaster).execute().get();
        Assert.assertEquals(master.getHost(), localHost);
        Assert.assertEquals(master.getPort(), localPort);
        waitConditionUntilTimeOut(() -> {
            HostPort port = null;
            try {
                port = new AbstractSentinelCommand.SentinelMaster(clientPool, scheduled, sentinelMaster).execute().get();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return port == null;
        }, waitTime, 1000);
        closeCheck(idc);
        addResult = new AbstractSentinelCommand.SentinelAdd(clientPool, sentinelMaster, localHost, localPort, 3, scheduled).execute().get();
        master = new AbstractSentinelCommand.SentinelMaster(clientPool, scheduled, sentinelMaster).execute().get();
        Assert.assertEquals(master.getHost(), localHost);
        Assert.assertEquals(master.getPort(), localPort);
        Thread.currentThread().sleep(waitTime);
        master = new AbstractSentinelCommand.SentinelMaster(clientPool, scheduled, sentinelMaster).execute().get();
        Assert.assertEquals(master.getHost(), localHost);
        Assert.assertEquals(master.getPort(), localPort);
    }

    @Test
    public void testUrl() {
        final String consoleUrl = "http://127.0.0.1:18080";
//        final String consoleCheckUrl = "http://127.0.0.1:18081";
        
        CheckerConsoleService service = new DefaultCheckerConsoleService();
        
        
        logger.info("------------------------------------");
        logger.info("{}", service.clusterAlertWhiteList(consoleUrl));
        logger.info("{}", service.getProxyTunnelInfos(consoleUrl));
        logger.info("{}", service.loadAllClusterCreateTime(consoleUrl));
        logger.info("{}", service.isSentinelAutoProcess(consoleUrl));
        logger.info("{}", service.sentinelCheckWhiteList(consoleUrl));
        logger.info("{}", service.getClusterCreateTime(consoleUrl, "cluster1"));
        Assert.assertEquals(service.isAlertSystemOn(consoleUrl), true);
        Assert.assertEquals(service.isClusterOnMigration(consoleUrl, "cluster1"), false);


        RedisMeta redisMeta = newRandomFakeRedisMeta().setPort(1000);
        DefaultRedisInstanceInfo info = new DefaultRedisInstanceInfo(redisMeta.parent().parent().parent().getId(),
                redisMeta.parent().parent().getId(), redisMeta.parent().getId(),
                new HostPort(redisMeta.getIp(), redisMeta.getPort()),
                redisMeta.parent().getActiveDc(), ClusterType.BI_DIRECTION);
        DefaultRedisHealthCheckInstance instance = new DefaultRedisHealthCheckInstance();
        instance.setInstanceInfo(info);
        instance.setEndpoint(new DefaultEndPoint(info.getHostPort().getHost(), info.getHostPort().getPort()));
        instance.setHealthCheckConfig(new DefaultHealthCheckConfig(buildCheckerConfig()));
        instance.setSession(new RedisSession(instance.getEndpoint(), scheduled, pool));
        service.updateRedisRole( consoleUrl, instance, Server.SERVER_ROLE.SLAVE);

        AlertMessageEntity alertMessageEntity = new AlertMessageEntity("Test", "test", Lists.newArrayList("test-list"));
        service.recordAlert(consoleUrl,  alertMessageEntity,  () -> {
            Properties properties = new Properties();
            properties.setProperty("h", "t");
            return properties;
        });
    }
    
    @After
    public void testAfter() throws Exception {
        stopAllServer();
    }
}


