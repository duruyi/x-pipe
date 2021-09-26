package com.ctrip.xpipe.redis.integratedtest.checker;

import com.ctrip.xpipe.endpoint.HostPort;
import com.ctrip.xpipe.pool.XpipeNettyClientKeyedObjectPool;
import com.ctrip.xpipe.redis.core.entity.RedisMeta;
import com.ctrip.xpipe.redis.core.entity.ShardMeta;
import com.ctrip.xpipe.redis.core.entity.ZkServerMeta;
import com.ctrip.xpipe.redis.integratedtest.console.cmd.RedisStartCmd;
import com.ctrip.xpipe.redis.integratedtest.console.cmd.ServerStartCmd;
import com.ctrip.xpipe.redis.integratedtest.metaserver.AbstractMetaServerMultiDcTest;
import com.ctrip.xpipe.spring.RestTemplateFactory;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestOperations;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.KEY_SERVER_MODE;
import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.SERVER_MODE.CONSOLE;
import static com.ctrip.xpipe.redis.console.config.impl.DefaultConsoleConfig.KEY_CLUSTER_SHARD_FOR_MIGRATE_SYS_CHECK;

public class CheckCrdtRedisByProxy extends AbstractMetaServerMultiDcTest {
    private ApplicationContext jqConsole;
    private ApplicationContext jqChecker;
    private RedisStartCmd jqMaster;
    private RedisStartCmd fraMaster;

    protected String prepareDatas() throws IOException {
        return prepareDatasFromFile("src/test/resources/xpipe-crdt-checker-proxy.sql");
    }
    ShardMeta getClusterMeta(String idc, String cluster, String shard) {
        return getXpipeMeta().getDcs().get(idc).getClusters().get(cluster).getShards().get(shard);
    }

    RedisMeta getMasterRedis(String idc, String cluster, String shard) throws Exception {
        ShardMeta shardMeta = getClusterMeta(idc, cluster, shard);
        RedisMeta master = findMaster(shardMeta.getRedises());
        if(master == null) {
            return null;
        }
        stopRedis(master);
        return master;
    }

    private int consolePort = 18080;
    private final String JQ_IDC = "jq";
    private final String FRA_IDC = "fra";
    private RestOperations restOperations;
    List<ServerStartCmd> proxyServers = Lists.newArrayList();
    List<CrdtRedisServer> masters = Lists.newArrayList();
    ZkServerMeta getZk(String idc) {
        return getXpipeMeta().getDcs().get(idc).getZkServer();
    }
    
    @Before
    public void startServers() throws Exception {
        restOperations = RestTemplateFactory.createCommonsHttpRestTemplate(1000, 1000, 1000, 15000);

        proxyServers.add(startProxy( "jq", 11080, 11443));
        proxyServers.add(startProxy( "fra",11081, 11444));
        proxyServers.add(startProxy( "jq",11082, 11445));
        proxyServers.add(startProxy( "fra",11083, 11446));
        
        startDb();
        final String localhost = "127.0.0.1";
        String clusterName = "cluster1";
        String shardName = "shard1";
        
        XpipeNettyClientKeyedObjectPool pool = getXpipeNettyClientKeyedObjectPool();
        
        RedisMeta jqMasterInfo = getMasterRedis(JQ_IDC, clusterName, shardName);
        masters.add(new CrdtRedisServer(getGid(JQ_IDC), jqMasterInfo));
        RedisMeta fraMasterInfo = getMasterRedis(FRA_IDC, clusterName, shardName);
        masters.add(new CrdtRedisServer(getGid(FRA_IDC), fraMasterInfo));
        startCrdtMasters(masters , pool, scheduled);

        
        
        
        ZkServerMeta jqZk = getZk(JQ_IDC);
        startZk(jqZk);    
        
        Map<String, String> consoles = new HashMap<>();
        consoles.put("jq", "http://127.0.0.1:" + consolePort);
        consoles.put("fra", "http://127.0.0.1:" + consolePort);
        Map<String, String> metaServers = new HashMap<>();
        Map<String, String> extraOptions = new HashMap<>();
        extraOptions.put(KEY_CLUSTER_SHARD_FOR_MIGRATE_SYS_CHECK, "cluster-dr,cluster-dr-shard1");
        extraOptions.put(KEY_SERVER_MODE, CONSOLE.name());
        extraOptions.put("console.cluster.types", "one_way,bi_direction,ONE_WAY,BI_DIRECTION");
        int checkerPort = 18000;

        jqConsole = startSpringConsole(consolePort, JQ_IDC, jqZk.getAddress(), Collections.singletonList("127.0.0.1:" + consolePort), consoles, metaServers, extraOptions);


        jqChecker = startSpringChecker(checkerPort++, JQ_IDC, jqZk.getAddress(), Collections.singletonList("127.0.0.1:" + consolePort), "127.0.0.2");

    }
    
    @Test
    public void checkHelath() throws Exception {
        waitConditionUntilTimeOut(() -> {
            Map<String, Map<HostPort, Object>> result = restOperations.getForObject("http://127.0.0.1:"+consolePort+"/console/cross-master/delay/bi_direction/"+JQ_IDC+"/cluster1/shard1", Map.class);
            if(result == null || result.get(FRA_IDC) == null || result.get(FRA_IDC).size() == 0) return false;
            for(Object value : result.get(FRA_IDC).values()) {
                if(value instanceof  Integer) {
                    int v = (int)value;
                    if(v < 0 || v >= 999000) {
                        return false;
                    }
                } else if(value instanceof Long) {
                    long v = (long)value;
                    if(v < 0 || v >= 999000) {
                        return false;
                    }
                }
            }
            return true;
        }, 50000, 1000);
    }

    @After
    public void stopServers() {
        if(jqMaster != null) jqMaster.killProcess();
        if(fraMaster != null) fraMaster.killProcess();
        proxyServers.forEach(proxyServer -> {
            try {
                proxyServer.killProcess();
            }catch (Exception e) {
                e.printStackTrace();
            }
            
        });
        masters.forEach(master -> {
            try {
                master.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        }
}
