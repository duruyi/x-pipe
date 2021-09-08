package com.ctrip.xpipe.redis.console.resources;

import com.ctrip.xpipe.api.email.EmailResponse;
import com.ctrip.xpipe.api.server.Server;
import com.ctrip.xpipe.config.DefaultConfig;
import com.ctrip.xpipe.endpoint.ClusterShardHostPort;
import com.ctrip.xpipe.endpoint.DefaultEndPoint;
import com.ctrip.xpipe.endpoint.HostPort;
import com.ctrip.xpipe.proxy.ProxyEndpoint;
import com.ctrip.xpipe.redis.checker.AbstractCheckerTest;
import com.ctrip.xpipe.redis.checker.CheckerConsoleService;
import com.ctrip.xpipe.redis.checker.PersistenceCache;
import com.ctrip.xpipe.redis.checker.alert.ALERT_TYPE;
import com.ctrip.xpipe.redis.checker.alert.AlertEntity;
import com.ctrip.xpipe.redis.checker.alert.AlertMessageEntity;
import com.ctrip.xpipe.redis.checker.config.CheckerConfig;
import com.ctrip.xpipe.redis.checker.healthcheck.RedisHealthCheckInstance;
import com.ctrip.xpipe.redis.checker.healthcheck.RedisInstanceInfo;
import com.ctrip.xpipe.redis.checker.healthcheck.impl.DefaultRedisHealthCheckInstance;
import com.ctrip.xpipe.redis.checker.healthcheck.impl.DefaultRedisInstanceInfo;
import com.ctrip.xpipe.redis.checker.resource.DefaultCheckerConsoleService;
import com.ctrip.xpipe.redis.core.console.ConsoleCheckerPath;
import com.ctrip.xpipe.redis.core.proxy.endpoint.DefaultProxyEndpoint;
import com.ctrip.xpipe.retry.RetryPolicyFactories;
import com.ctrip.xpipe.spring.RestTemplateFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections.SetUtils;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import static com.ctrip.xpipe.redis.checker.healthcheck.actions.interaction.HEALTH_STATE.INSTANCEUP;
import static org.mockito.Mockito.when;

public class CheckerPersistenceCacheTest extends AbstractCheckerTest {
    private MockWebServer webServer;
    @Mock
    CheckerConfig config;
    @Mock
    PersistenceCache persistence;

    @Test
    public void mockHttp() throws IOException, InterruptedException {
        MockitoAnnotations.initMocks(this);
        webServer  = new MockWebServer();
        ObjectMapper objectMapper = new ObjectMapper();
        final CheckerConsoleService.AlertMessage[] acceptAlertMessage = new CheckerConsoleService.AlertMessage[1];
        
        final Dispatcher dispatcher = new Dispatcher() {

            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                switch (request.getPath()) {
                    case ConsoleCheckerPath.PATH_GET_CLUSTER_ALERT_WHITE_LIST:
                        try {
                            return new MockResponse().setResponseCode(200).setBody(objectMapper.writeValueAsString(persistence.clusterAlertWhiteList())).setHeader("Content-Type", "application/json");
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        };
                        break;
                    case ConsoleCheckerPath.PATH_GET_LOAD_ALL_CLUSTER_CREATE_TIME:
                        try {
                            return new MockResponse().setResponseCode(200).setBody(objectMapper.writeValueAsString(persistence.loadAllClusterCreateTime())).setHeader("Content-Type", "application/json");
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }
                        break;
                    case ConsoleCheckerPath.PATH_GET_IS_ALERT_SYSTEM_ON:
                        try {
                            return new MockResponse().setResponseCode(200).setBody(objectMapper.writeValueAsString(persistence.isAlertSystemOn())).setHeader("Content-Type", "application/json");
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }
                        break;
                    case ConsoleCheckerPath.PATH_POST_RECORD_ALERT:
                        try {
                            String body = new String(request.getBody().readByteArray());
                            acceptAlertMessage[0] = objectMapper.readValue(body, CheckerConsoleService.AlertMessage.class);
                            return new MockResponse().setResponseCode(200);
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                        break;

                }
                return new MockResponse().setResponseCode(404);

            }
        };
        webServer.setDispatcher(dispatcher);
        int port = randomPort();
        webServer.start(port);
        when(config.getConsoleAddress()).thenReturn("http://127.0.0.1:" + port);
        when(config.getConfigCacheTimeoutMilli()).thenReturn(10L);
        Set<String> clusterAlertWhiteList = new LinkedHashSet<>();
        clusterAlertWhiteList.add("test");
        when(persistence.clusterAlertWhiteList()).thenReturn(clusterAlertWhiteList);
        Map<String,Date> map = Maps.newConcurrentMap();
        map.put("test",new Date(1L));
        when(persistence.loadAllClusterCreateTime()).thenReturn(map);

        CheckerConsoleService  service = new DefaultCheckerConsoleService();
        CheckerPersistenceCache checkerPersistenceCache = new CheckerPersistenceCache(config, service, scheduled);
        
       
        Assert.assertEquals(checkerPersistenceCache.clusterAlertWhiteList().size(), 1);
        Assert.assertEquals(checkerPersistenceCache.clusterAlertWhiteList().contains("test"), true);


        Date d = checkerPersistenceCache.getClusterCreateTime("test");
        Assert.assertEquals(d, new Date(1));

        when(persistence.isAlertSystemOn()).thenReturn(true);
        Assert.assertEquals(checkerPersistenceCache.isAlertSystemOn(), true);
        when(persistence.isAlertSystemOn()).thenReturn(false);
        Assert.assertEquals(checkerPersistenceCache.isAlertSystemOn(), true);
        Thread.sleep(10L);
        Assert.assertEquals(checkerPersistenceCache.isAlertSystemOn(), false);
        AlertMessageEntity alertMessageEntity = new AlertMessageEntity("Test", "test", Lists.newArrayList("test-list"));
        Properties properties = new Properties();
        properties.setProperty("hello", "test");
        EmailResponse response = new EmailResponse() {
            @Override
            public Properties getProperties() {
                return properties;
            }
        };
        checkerPersistenceCache.recordAlert(alertMessageEntity, response);
        Assert.assertEquals(acceptAlertMessage[0].getMessage().toString(), alertMessageEntity.toString());
        Assert.assertEquals(acceptAlertMessage[0].getEmailResponse().getProperties(), response.getProperties());
    }



}

