package com.ctrip.xpipe.redis.integratedtest.checker;

import com.ctrip.xpipe.api.endpoint.Endpoint;
import com.ctrip.xpipe.api.pool.SimpleObjectPool;
import com.ctrip.xpipe.endpoint.DefaultEndPoint;
import com.ctrip.xpipe.netty.commands.NettyClient;
import com.ctrip.xpipe.pool.XpipeNettyClientKeyedObjectPool;
import com.ctrip.xpipe.redis.core.entity.RedisMeta;
import com.ctrip.xpipe.redis.core.entity.ShardMeta;
import com.ctrip.xpipe.redis.core.protocal.cmd.PeerOfCommand;
import com.ctrip.xpipe.redis.core.protocal.cmd.PingCommand;
import com.ctrip.xpipe.redis.integratedtest.console.cmd.RedisStartCmd;
import com.ctrip.xpipe.redis.integratedtest.metaserver.AbstractMetaServerMultiDcTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.ExecutionException;

public class CheckCrdtRedisByProxy extends AbstractMetaServerMultiDcTest {
    
    ApplicationContext jqChecker;
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

    private int consolePort = 18080;
    private final String JQ_IDC = "jq";
    private final String FRA_IDC = "fra";
    
    @Before
    public void startServers() throws Exception {
        startDb();
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
//        startConsole(consolePort);
//        startChecker();
//        startChecker();

    }

    @Test
    public void waitConsole() {
        
    }

    @After
    public void stopServers() {
        jqMaster.killProcess();
        fraMaster.killProcess();
//        jqChecker.close();
//        jqConsole.close();
    }
}
