package com.ctrip.framework.xpipe.redis.proxy;

import com.alibaba.arthas.deps.org.slf4j.Logger;
import com.alibaba.arthas.deps.org.slf4j.LoggerFactory;
import com.ctrip.framework.xpipe.redis.utils.ConnectionUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Slight
 * <p>
 * Sep 06, 2021 9:09 PM
 */
public class ProxyInetSocketAddress extends InetSocketAddress {
    private static final Logger logger = LoggerFactory.getLogger(ProxyInetSocketAddress.class);
    
    public volatile boolean sick = false;

    public volatile boolean down = false;
    
    public volatile int retryUpCounter = 0;
    
    public volatile int retryDownCounter = 0;

    public AtomicLong reference = new AtomicLong(0);

    public ProxyInetSocketAddress(int port) {
        super(port);
    }

    public ProxyInetSocketAddress(InetAddress addr, int port) {
        super(addr, port);
    }

    public ProxyInetSocketAddress(String hostname, int port) {
        super(hostname, port);
    }
    
    public void tryUp(int max) {
        retryDownCounter = 0;
        if((++retryUpCounter) >= max) {
            logger.info("address {} up", this);
            down = false;
            sick = false;
        }
    }
    
    public void tryDown(int max) {
        retryUpCounter = 0;
        if((++retryDownCounter) >= max && !down) {
            logger.info("address {} down", this);
            down = true;
        }
    }
}