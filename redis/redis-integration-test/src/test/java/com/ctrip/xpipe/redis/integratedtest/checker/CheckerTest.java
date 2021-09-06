package com.ctrip.xpipe.redis.integratedtest.checker;


import com.ctrip.xpipe.api.command.Command;
import com.ctrip.xpipe.api.pool.SimpleObjectPool;
import com.ctrip.xpipe.endpoint.DefaultEndPoint;
import com.ctrip.xpipe.endpoint.HostPort;
import com.ctrip.xpipe.netty.commands.NettyClient;
import com.ctrip.xpipe.pool.XpipeNettyClientKeyedObjectPool;
import com.ctrip.xpipe.redis.core.protocal.cmd.AbstractSentinelCommand;
import com.ctrip.xpipe.redis.integratedtest.metaserver.AbstractMetaServerMultiDcTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.ctrip.xpipe.redis.checker.spring.ConsoleServerModeCondition.SERVER_MODE.CONSOLE;

public class CheckerTest extends AbstractMetaServerMultiDcTest{
    public Map<String, ConsoleInfo> defaultConsoleInfo() {
        Map<String, ConsoleInfo> consoleInfos = new HashMap<>();
        //start console + checker 2server
        consoleInfos.put("jq", new ConsoleInfo(CONSOLE).setConsole_port(18080).setChecker_port(28080));
        //start conset_checker 1server
        consoleInfos.put("oy", new ConsoleInfo(CONSOLE).setConsole_port(18081).setChecker_port(28081));
        //start checker 1 server
        consoleInfos.put("fra", new ConsoleInfo(CONSOLE).setConsole_port(18082).setChecker_port(28082));
        return consoleInfos;
    }
    @Before
    public void testBefore() throws Exception {
        startCRDTAllServer(defaultConsoleInfo());
    }
    
}
