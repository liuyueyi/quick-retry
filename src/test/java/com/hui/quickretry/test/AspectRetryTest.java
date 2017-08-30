package com.hui.quickretry.test;

import com.hui.quickretry.demo.RetryDemoService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Created by yihui on 2017/8/30.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"classpath:aop.xml"})
public class AspectRetryTest {

    @Autowired
    private RetryDemoService retryDemoService;

    @Test
    public void testRetry() throws Exception {
        for (int i = 0; i < 3; i++) {
            int ans = retryDemoService.genBigNum();

            System.out.println("----" + ans + "----");

            retryDemoService.genSmallNum();

            System.out.println("------------------");
        }
    }

}
