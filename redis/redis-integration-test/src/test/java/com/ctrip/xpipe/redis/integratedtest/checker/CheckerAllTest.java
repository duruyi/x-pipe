package com.ctrip.xpipe.redis.integratedtest.checker;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        CheckerTest.class,
        CheckCrdtRedisByProxy.class,
        TwoChecker2SameConsole.class
})
public class CheckerAllTest {
}
