package com.ctrip.xpipe.redis.integratedtest.checker;

import com.ctrip.xpipe.api.endpoint.Endpoint;
import com.ctrip.xpipe.api.pool.SimpleObjectPool;
import com.ctrip.xpipe.endpoint.DefaultEndPoint;
import com.ctrip.xpipe.netty.commands.NettyClient;
import com.ctrip.xpipe.pool.XpipeNettyClientKeyedObjectPool;
import com.ctrip.xpipe.redis.core.entity.RedisMeta;
import com.ctrip.xpipe.redis.core.entity.ShardMeta;
import com.ctrip.xpipe.redis.core.entity.ZkServerMeta;
import com.ctrip.xpipe.redis.core.protocal.cmd.PeerOfCommand;
import com.ctrip.xpipe.redis.core.protocal.cmd.PingCommand;
import com.ctrip.xpipe.redis.integratedtest.console.cmd.RedisStartCmd;
import com.ctrip.xpipe.redis.integratedtest.metaserver.AbstractMetaServerMultiDcTest;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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

    ZkServerMeta getZk(String idc) {
        return getXpipeMeta().getDcs().get(idc).getZkServer();
    }
    
    @Before
    public void startServers() throws Exception {

        startProxyServer( 11080, 11443);
        startProxyServer( 11081, 11444);
        startProxyServer( 11082, 11445);
        startProxyServer( 11083, 11446);
        startDb();
        final String localhost = "127.0.0.1";
        String clusterName = "cluster1";
        String shardName = "shard1";
        
        XpipeNettyClientKeyedObjectPool pool = getXpipeNettyClientKeyedObjectPool();
        List<CrdtRedisServer> masters = Lists.newArrayList();
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
    public void waitConsole() throws InterruptedException {
        Thread.currentThread().join();
    }

    @After
    public void stopServers() {
        jqMaster.killProcess();
        fraMaster.killProcess();
//        jqChecker.close();
//        jqConsole.close();
    }
}
