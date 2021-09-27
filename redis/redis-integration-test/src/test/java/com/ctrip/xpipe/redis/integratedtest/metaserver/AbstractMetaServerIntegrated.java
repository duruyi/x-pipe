package com.ctrip.xpipe.redis.integratedtest.metaserver;

import com.ctrip.xpipe.codec.JsonCodec;
import com.ctrip.xpipe.endpoint.DefaultEndPoint;
import com.ctrip.xpipe.pool.XpipeNettyClientKeyedObjectPool;
import com.ctrip.xpipe.redis.checker.healthcheck.HealthChecker;
import com.ctrip.xpipe.redis.core.entity.RedisMeta;
import com.ctrip.xpipe.redis.core.protocal.cmd.PeerOfCommand;
import com.ctrip.xpipe.redis.core.protocal.cmd.PingCommand;
import com.ctrip.xpipe.redis.integratedtest.console.AbstractXPipeClusterTest;
import com.ctrip.xpipe.redis.integratedtest.console.app.ConsoleSpringApp;
import com.ctrip.xpipe.redis.integratedtest.console.cmd.RedisStartCmd;
import com.ctrip.xpipe.redis.integratedtest.metaserver.proxy.LocalProxyConfig;
import com.ctrip.xpipe.redis.integratedtest.metaserver.proxy.LocalResourceManager;
import com.ctrip.xpipe.redis.proxy.DefaultProxyServer;
import com.ctrip.xpipe.redis.proxy.ProxyServer;
import com.ctrip.xpipe.redis.proxy.monitor.DefaultTunnelMonitorManager;
import com.ctrip.xpipe.redis.proxy.monitor.TunnelMonitorManager;
import com.ctrip.xpipe.redis.proxy.monitor.stats.impl.DefaultPingStatsManager;
import com.ctrip.xpipe.redis.proxy.resource.ResourceManager;
import com.ctrip.xpipe.redis.proxy.tunnel.DefaultTunnelManager;
import com.ctrip.xpipe.redis.proxy.tunnel.TunnelManager;
import com.ctrip.xpipe.spring.AbstractProfile;
import com.ctrip.xpipe.zk.ZkTestServer;
import com.google.common.collect.Maps;
import org.junit.After;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.ctrip.xpipe.foundation.DefaultFoundationService.DATA_CENTER_KEY;
import static com.ctrip.xpipe.foundation.DefaultFoundationService.LOCAL_IP_KEY;
import static com.ctrip.xpipe.redis.checker.cluster.AbstractCheckerLeaderElector.KEY_CHECKER_ID;
import static com.ctrip.xpipe.redis.checker.config.CheckerConfig.*;
import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.KEY_SERVER_MODE;
import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.SERVER_MODE.CHECKER;
import static com.ctrip.xpipe.redis.console.config.impl.DefaultConsoleConfig.KEY_METASERVERS;
import static com.ctrip.xpipe.redis.core.config.AbstractCoreConfig.KEY_ZK_ADDRESS;


public class AbstractMetaServerIntegrated extends AbstractXPipeClusterTest {


    protected ZkTestServer startZk(int zkPort) {
        try {
            logger.info(remarkableMessage("[startZK]{}"), zkPort);
            ZkTestServer zkTestServer = new ZkTestServer(zkPort);
            zkTestServer.initialize();
            zkTestServer.start();
            add(zkTestServer);
            return zkTestServer;
        } catch (Exception e) {
            logger.error("[startZk]", e);
            throw new IllegalStateException("[startZk]" + zkPort, e);
        }
    }

    protected ProxyServer startProxyServer( int tcp_port, int tls_port) throws Exception {
        LocalProxyConfig proxyConfig = new LocalProxyConfig();
        proxyConfig.setFrontendTcpPort(tcp_port).setFrontendTlsPort(tls_port);
        ResourceManager resourceManager = new LocalResourceManager(proxyConfig);
        TunnelMonitorManager tunnelMonitorManager = new DefaultTunnelMonitorManager(resourceManager);
        TunnelManager tunnelManager = new DefaultTunnelManager()
                .setConfig(proxyConfig)
                .setProxyResourceManager(resourceManager)
                .setTunnelMonitorManager(tunnelMonitorManager);
        DefaultProxyServer server = new DefaultProxyServer().setConfig(proxyConfig);
        server.setTunnelManager(tunnelManager);
        server.setResourceManager(resourceManager);
        server.setPingStatsManager(new DefaultPingStatsManager());
        server.start();
        return server;
    }

    private ConfigurableApplicationContext buildSpringContext(Class<?> mainClass, Map<String, String> args) {
        String[] rawArgs = args.entrySet().stream().map(arg -> String.format("--%s=%s", arg.getKey(), arg.getValue(), arg.getKey(), arg.getValue())).collect(Collectors.toList())
                .toArray(new String[args.size()]);
        args.forEach((key, value) -> {
            System.setProperty(key, value);
        });
        ConfigurableApplicationContext cac =  new SpringApplicationBuilder(mainClass).run(rawArgs);
        args.forEach((key, value) -> {
            System.setProperty(key, "");
        });
        return cac;
    }


    Map<String, ConfigurableApplicationContext> springCACs = Maps.newConcurrentMap();
    @After
    public void closeAllSpringCACs() {
        springCACs.forEach((name, cac) -> {
            cac.close();
        });
    }


    protected ConfigurableApplicationContext startSpringConsole(int port, String idc, String zk, List<String> localDcConsoles,
                                                                Map<String, String> crossDcConsoles, Map<String, String> metaservers,
                                                                Map<String, String> extras) {

        ConfigurableApplicationContext cac = buildSpringContext(ConsoleSpringApp.class, new HashMap<String, String>() {{
            put(HealthChecker.ENABLED, "true");
            put("server.port", String.valueOf(port));
            put(KEY_CONSOLE_ADDRESS, "http://" + localDcConsoles.get(0));
            put("cat.client.enabled", "false");
            put("spring.profiles.active", AbstractProfile.PROFILE_NAME_PRODUCTION);
            put(DATA_CENTER_KEY, idc);
            put(KEY_ZK_ADDRESS, zk);
            put(KEY_METASERVERS, JsonCodec.INSTANCE.encode(metaservers));
            put("console.domains", JsonCodec.INSTANCE.encode(crossDcConsoles));
            put("console.all.addresses", String.join(",", localDcConsoles));
            put(KEY_CHECKER_META_REFRESH_INTERVAL, "2000");
            put(KEY_SENTINEL_CHECK_INTERVAL, "15000");
            putAll(extras);
        }});
        springCACs.put("xpipe-console-" + port, cac);
        return cac;
    }
    
    protected ConfigurableApplicationContext startSpringChecker(int port, String idc, String zk, List<String> localDcConsoles, String localIp) {
        ConfigurableApplicationContext cac = startSpringConsole(port, idc, zk,
                localDcConsoles, Collections.emptyMap(),
                Collections.emptyMap(),
                new HashMap<String, String>() {{
                    put(KEY_CONSOLE_ADDRESS, "http://" + localDcConsoles.get(0));
                    put(KEY_CHECKER_ID, idc + port);
                    put(KEY_SERVER_MODE, CHECKER.name());
                    put(LOCAL_IP_KEY, localIp);
                }});
        return cac;
    }
    
    protected class CrdtRedisServer {
        public int gid;
        public RedisMeta meta;
        public RedisStartCmd cmd;
        public CrdtRedisServer(int gid, RedisMeta meta) {
            this.gid = gid;
            this.meta = meta;
        }
        public void start() {
            if(cmd != null) return;
            cmd = startCrdtRedis(gid, meta.getPort());
        }
        public boolean checkRunning(XpipeNettyClientKeyedObjectPool pool, ScheduledExecutorService scheduled) {
            try {
                return new PingCommand(pool.getKeyPool(new DefaultEndPoint(meta.getIp(), meta.getPort())),scheduled).execute().get().equals("PONG");
            } catch (InterruptedException e) {
                return false;
            } catch (ExecutionException e) {
                return false;
            }
        }
        
        public void peerof(CrdtRedisServer crdtRedisServer, XpipeNettyClientKeyedObjectPool pool, ScheduledExecutorService scheduled) {
            new PeerOfCommand(pool.getKeyPool(new DefaultEndPoint(meta.getIp(), meta.getPort())), crdtRedisServer.gid, new DefaultEndPoint(crdtRedisServer.meta.getIp(), crdtRedisServer.meta.getPort()), scheduled).execute();
        }
        
        public void stop() {
            if(cmd == null) return;
            cmd.killProcess();
        }
        
        
    }
    
    protected void startCrdtMasters(List<CrdtRedisServer> masters, XpipeNettyClientKeyedObjectPool pool, ScheduledExecutorService scheduled) throws TimeoutException {
        int length = masters.size();
        for(int i = 0; i < length; i++) {
            masters.get(i).start();
        }
        for(int i = 0; i < length; i++) {
            CrdtRedisServer master= masters.get(i);
            waitConditionUntilTimeOut(()-> master.checkRunning(pool, scheduled), 5000, 100);
            for(int j = 0; j < length; j++) {
                if(i == j) return;
                master.peerof(masters.get(j),  pool, scheduled);
            }
        }
    }
}
