package com.ctrip.xpipe.redis.integratedtest.checker;

import com.ctrip.xpipe.redis.integratedtest.metaserver.AbstractMetaServerMultiDcTest;
import org.junit.Test;

public class LocalTest extends AbstractMetaServerMultiDcTest {
    @Test 
    public void test() throws Exception {
        startProxyServer( 11080, 11443);
        startProxyServer( 11081, 11444);
        Thread.currentThread().join();
    }
}
