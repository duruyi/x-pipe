package com.ctrip.xpipe.redis.integratedtest.checker;

import com.ctrip.xpipe.api.server.Server;
import com.ctrip.xpipe.cluster.ClusterType;
import com.ctrip.xpipe.endpoint.DefaultEndPoint;
import com.ctrip.xpipe.endpoint.HostPort;
import com.ctrip.xpipe.pool.XpipeNettyClientKeyedObjectPool;
import com.ctrip.xpipe.redis.checker.CheckerConsoleService;
import com.ctrip.xpipe.redis.checker.alert.AlertMessageEntity;
import com.ctrip.xpipe.redis.checker.healthcheck.config.DefaultHealthCheckConfig;
import com.ctrip.xpipe.redis.checker.healthcheck.impl.DefaultRedisHealthCheckInstance;
import com.ctrip.xpipe.redis.checker.healthcheck.impl.DefaultRedisInstanceInfo;
import com.ctrip.xpipe.redis.checker.healthcheck.session.RedisSession;
import com.ctrip.xpipe.redis.checker.resource.DefaultCheckerConsoleService;
import com.ctrip.xpipe.redis.core.entity.RedisMeta;
import com.ctrip.xpipe.redis.core.entity.ZkServerMeta;
import com.ctrip.xpipe.redis.integratedtest.metaserver.AbstractXpipeServerMultiDcTest;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.KEY_SERVER_MODE;
import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.SERVER_MODE.CONSOLE;
import static com.ctrip.xpipe.redis.console.config.impl.DefaultConsoleConfig.KEY_CLUSTER_SHARD_FOR_MIGRATE_SYS_CHECK;

public class TestConsoleWeb extends AbstractXpipeServerMultiDcTest {
    @Test
    public void testPersistenceCache() throws Exception {
        XpipeNettyClientKeyedObjectPool pool = getXpipeNettyClientKeyedObjectPool();
        final int consolePort = 18080;
        final String consoleUrl = "http://127.0.0.1:" + 18080;

        Map<String, String> consoles = new HashMap<>();
        consoles.put("jq", "http://127.0.0.1:" + 1);
        consoles.put("fra", "http://127.0.0.1:" + consolePort);
        Map<String, String> metaServers = new HashMap<>();
        Map<String, String> extraOptions = new HashMap<>();
        extraOptions.put(KEY_CLUSTER_SHARD_FOR_MIGRATE_SYS_CHECK, "cluster-dr,cluster-dr-shard1");
        extraOptions.put(KEY_SERVER_MODE, CONSOLE.name());
        extraOptions.put("console.cluster.types", "one_way,bi_direction,ONE_WAY,BI_DIRECTION");

        ZkServerMeta jqZk = getZk(JQ_IDC);
        startSpringConsole(consolePort, JQ_IDC, jqZk.getAddress(), Collections.singletonList("127.0.0.1:" + consolePort), consoles, metaServers, extraOptions);

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
    public void stop() throws Exception {
        stopAllServer();
    }
}
