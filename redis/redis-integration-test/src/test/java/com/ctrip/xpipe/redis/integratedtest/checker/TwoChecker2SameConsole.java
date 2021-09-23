package com.ctrip.xpipe.redis.integratedtest.checker;

import com.ctrip.xpipe.api.endpoint.Endpoint;
import com.ctrip.xpipe.api.pool.SimpleKeyedObjectPool;
import com.ctrip.xpipe.api.pool.SimpleObjectPool;
import com.ctrip.xpipe.endpoint.DefaultEndPoint;
import com.ctrip.xpipe.netty.commands.NettyClient;
import com.ctrip.xpipe.pool.XpipeNettyClientKeyedObjectPool;
import com.ctrip.xpipe.redis.core.entity.ClusterMeta;
import com.ctrip.xpipe.redis.core.entity.RedisMeta;
import com.ctrip.xpipe.redis.core.entity.ShardMeta;
import com.ctrip.xpipe.redis.core.foundation.IdcUtil;
import com.ctrip.xpipe.redis.core.meta.DcInfo;
import com.ctrip.xpipe.redis.core.protocal.cmd.PeerOfCommand;
import com.ctrip.xpipe.redis.core.protocal.cmd.PingCommand;
import com.ctrip.xpipe.redis.integratedtest.console.cmd.RedisStartCmd;
import com.ctrip.xpipe.redis.integratedtest.metaserver.AbstractMetaServerMultiDcTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.KEY_SERVER_MODE;
import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.SERVER_MODE.CONSOLE;
import static com.ctrip.xpipe.redis.console.config.impl.DefaultConsoleConfig.KEY_CLUSTER_SHARD_FOR_MIGRATE_SYS_CHECK;

public class TwoChecker2SameConsole extends AbstractMetaServerMultiDcTest {
    
    
    ApplicationContext jqConsole;
    ApplicationContext jqChecker;
    ApplicationContext fraChecker;
    RedisStartCmd jqMaster;
    RedisStartCmd fraMaster;
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
    
    
    @Before 
    public void startServers() throws Exception {
        startDb();
        int consolePort = 18080;
        final String JQ_IDC = "jq";
        final String FRA_IDC = "fra";
        final String localhost = "127.0.0.1";
        String clusterName = "cluster1";
        String shardName = "shard1";
        

        XpipeNettyClientKeyedObjectPool pool = getXpipeNettyClientKeyedObjectPool();
        
        RedisMeta jqMasterInfo = getMasterRedis(JQ_IDC, clusterName, shardName);
        jqMaster = startCrdtRedis(getGid(JQ_IDC), jqMasterInfo.getPort());
        RedisMeta fraMasterInfo = getMasterRedis(FRA_IDC, clusterName, shardName);
        fraMaster = startCrdtRedis(getGid(FRA_IDC), fraMasterInfo.getPort());
        Endpoint fraMasterEndpoint = new DefaultEndPoint(localhost, fraMasterInfo.getPort());
        Endpoint jqMasterEndpoint = new DefaultEndPoint(localhost, jqMasterInfo.getPort());
        SimpleObjectPool<NettyClient> jqRedisPool = pool.getKeyPool(jqMasterEndpoint);
        SimpleObjectPool<NettyClient> fraRedisPool = pool.getKeyPool(fraMasterEndpoint);
        waitConditionUntilTimeOut(() -> {
            try {
                return new PingCommand(jqRedisPool,scheduled).execute().get().equals("PONG");
            } catch (InterruptedException e) {
                return false;
            } catch (ExecutionException e) {
                return false;
            }
        }, 10000, 1000);
        
        
        new PeerOfCommand(jqRedisPool, getGid(FRA_IDC), fraMasterEndpoint, scheduled).execute();
        new PeerOfCommand(fraRedisPool, getGid(JQ_IDC), jqMasterEndpoint, scheduled).execute();
        
//        
        Map<String, String> consoles = new HashMap<>();
        consoles.put("jq", "http://127.0.0.1:" + consolePort);
        consoles.put("fra", "http://127.0.0.1:" + consolePort);
        Map<String, String> metaServers = new HashMap<>();
        Map<String, String> extraOptions = new HashMap<>();
        extraOptions.put(KEY_CLUSTER_SHARD_FOR_MIGRATE_SYS_CHECK, "cluster-dr,cluster-dr-shard1");
        extraOptions.put(KEY_SERVER_MODE, CONSOLE.name());
        extraOptions.put("console.cluster.types", "one_way,bi_direction,ONE_WAY,BI_DIRECTION");
        
        startSpringConsole(consolePort, JQ_IDC, "127.0.0.1:2181", Collections.singletonList("127.0.0.1:8080"), consoles, metaServers, extraOptions);

//        startSpringChecker();
//        startSpringChecker();
        
    }
    
    @Test
    public void waitConsole() throws InterruptedException {
        Thread.currentThread().join();
    }
    
    @After
    public void stopServers() throws Exception {
        jqMaster.killProcess();
        fraMaster.killProcess();
    }
}
