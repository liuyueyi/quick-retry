package com.hui.quickretry.demo;

import com.hui.quickretry.aop.RetryDot;
import com.hui.quickretry.event.RetryEvent;
import com.hui.quickretry.event.RetryProcess;
import org.springframework.stereotype.Service;

/**
 * Created by yihui on 2017/8/30.
 */
@Service
public class RetryDemoService {


    private int genNum() {
        return (int) (Math.random() * 10);
    }


    @RetryDot(count = 5, sleep = 10)
    public int genBigNum() throws Exception {
        int a = genNum();
        System.out.println("genBigNum " + a);
        if (a < 3) {
            throw new Exception("num less than 3");
        }

        return a;
    }



    public void genSmallNum() throws Exception {
        RetryEvent retryEvent = new RetryEvent();
        retryEvent.setSleep(10);
        retryEvent.setCount(5);
        retryEvent.setAsyn(false);
        retryEvent.setCallback(() -> {
            int a = genNum();
            System.out.println("now num: " + a);
            if (a > 3) {
                throw new RuntimeException("num bigger than 3");
            }

            return a;
        });

        RetryProcess.post(retryEvent);
    }


}
