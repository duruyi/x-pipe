package com.ctrip.xpipe.redis.integratedtest.checker;

import com.ctrip.xpipe.api.endpoint.Endpoint;
import com.ctrip.xpipe.api.pool.SimpleKeyedObjectPool;
import com.ctrip.xpipe.api.pool.SimpleObjectPool;
import com.ctrip.xpipe.endpoint.DefaultEndPoint;
import com.ctrip.xpipe.endpoint.HostPort;
import com.ctrip.xpipe.netty.commands.NettyClient;
import com.ctrip.xpipe.pool.XpipeNettyClientKeyedObjectPool;
import com.ctrip.xpipe.redis.core.entity.ClusterMeta;
import com.ctrip.xpipe.redis.core.entity.RedisMeta;
import com.ctrip.xpipe.redis.core.entity.ShardMeta;
import com.ctrip.xpipe.redis.core.entity.ZkServerMeta;
import com.ctrip.xpipe.redis.core.foundation.IdcUtil;
import com.ctrip.xpipe.redis.core.meta.DcInfo;
import com.ctrip.xpipe.redis.core.protocal.cmd.PeerOfCommand;
import com.ctrip.xpipe.redis.core.protocal.cmd.PingCommand;
import com.ctrip.xpipe.redis.integratedtest.console.cmd.RedisStartCmd;
import com.ctrip.xpipe.redis.integratedtest.metaserver.AbstractMetaServerMultiDcTest;
import com.ctrip.xpipe.spring.RestTemplateFactory;
import com.ctrip.xpipe.tuple.Pair;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestOperations;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.KEY_SERVER_MODE;
import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.SERVER_MODE.CONSOLE;
import static com.ctrip.xpipe.redis.console.config.impl.DefaultConsoleConfig.KEY_CLUSTER_SHARD_FOR_MIGRATE_SYS_CHECK;

public class TwoChecker2SameConsole extends AbstractMetaServerMultiDcTest {
    
    
    private ApplicationContext jqConsole;
    
    private ApplicationContext jqChecker;
    
    private ApplicationContext fraChecker;
    private RedisStartCmd jqMaster;
    private RedisStartCmd fraMaster;

    private RestOperations restOperations;
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
    
    ZkServerMeta getZk(String idc) {
        return getXpipeMeta().getDcs().get(idc).getZkServer();
    }

    private final String JQ_IDC = "jq";
    
    private final String FRA_IDC = "fra";
    
    private int consolePort = 18080;
    @Before 
    public void startServers() throws Exception {
        startDb();
       
        
        
        final String localhost = "127.0.0.1";
        String clusterName = "cluster1";
        String shardName = "shard1";

        ZkServerMeta jqZk = getZk(JQ_IDC);
        startZk(jqZk);
        ZkServerMeta fraZk = getZk(FRA_IDC);
        startZk(fraZk);
        XpipeNettyClientKeyedObjectPool pool = getXpipeNettyClientKeyedObjectPool();
        
        List<CrdtRedisServer> masters = Lists.newArrayList();
        RedisMeta jqMasterInfo = getMasterRedis(JQ_IDC, clusterName, shardName);
        masters.add(new CrdtRedisServer(getGid(JQ_IDC), jqMasterInfo));
        RedisMeta fraMasterInfo = getMasterRedis(FRA_IDC, clusterName, shardName);
        masters.add(new CrdtRedisServer(getGid(FRA_IDC), fraMasterInfo));
        startCrdtMasters(masters , pool, scheduled);
        
//        
        Map<String, String> consoles = new HashMap<>();
        consoles.put("jq", "http://127.0.0.1:" + consolePort);
        consoles.put("fra", "http://127.0.0.1:" + consolePort);
        Map<String, String> metaServers = new HashMap<>();
        Map<String, String> extraOptions = new HashMap<>();
        extraOptions.put(KEY_CLUSTER_SHARD_FOR_MIGRATE_SYS_CHECK, "cluster-dr,cluster-dr-shard1");
        extraOptions.put(KEY_SERVER_MODE, CONSOLE.name());
        extraOptions.put("console.cluster.types", "one_way,bi_direction,ONE_WAY,BI_DIRECTION");

        jqConsole = startSpringConsole(consolePort, JQ_IDC, jqZk.getAddress(), Collections.singletonList("127.0.0.1:" + consolePort), consoles, metaServers, extraOptions);

        int checkerPort = 18001;
        jqChecker = startSpringChecker(checkerPort++, JQ_IDC, jqZk.getAddress(), Collections.singletonList("127.0.0.1:" + consolePort), "127.0.0.2");

        fraChecker = startSpringChecker(checkerPort++, FRA_IDC, fraZk.getAddress(), Collections.singletonList("127.0.0.1:" + consolePort), "127.0.0.3");

        restOperations = RestTemplateFactory.createCommonsHttpRestTemplate(1000, 1000, 1000, 15000);

    }
    
    public BooleanSupplier checkDelay(int consolePort, String srcIdc, String targetIdc) {
        return ()-> {
            Map<String, Map<HostPort, Object>> result = restOperations.getForObject("http://127.0.0.1:"+consolePort+"/console/cross-master/delay/bi_direction/"+srcIdc+"/cluster1/shard1", Map.class);
            if(result == null || result.get(targetIdc) == null) return false;
            for(Object value : result.get(targetIdc).values()) {
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
        };
    }
    
    @Test
    public void waitConsole() throws InterruptedException, TimeoutException {
        waitConditionUntilTimeOut(checkDelay(consolePort, JQ_IDC, FRA_IDC), 50000, 1000);
        waitConditionUntilTimeOut(checkDelay(consolePort, FRA_IDC, JQ_IDC), 50000, 1000);
    }
    
    @After
    public void stopServers() throws Exception {
        if(jqMaster != null) jqMaster.killProcess();
        if(fraMaster != null) fraMaster.killProcess();
    }
}
