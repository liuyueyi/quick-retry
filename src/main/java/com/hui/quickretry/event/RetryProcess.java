package com.hui.quickretry.event;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.hui.quickretry.template.RetryTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by yihui on 2017/8/30.
 */
@Component
public class RetryProcess {

    ExecutorService executorService = new ThreadPoolExecutor(3, 5,
            1, TimeUnit.MINUTES,
            new LinkedBlockingQueue<Runnable>());

    private  static EventBus eventBus = new EventBus("retry");

    @PostConstruct
    public void init() {
        register(this);
    }


    public static void post(RetryEvent event) {
        eventBus.post(event);
    }

    public static void register(Object handler) {
        eventBus.register(handler);
    }

    public static void unregister(Object handler) {
        eventBus.unregister(handler);
    }


    @Subscribe
    public void process(RetryEvent event) throws InterruptedException {

        RetryTemplate retryTemplate = new RetryTemplate() {
            @Override
            protected Object doBiz() throws Throwable {
                return event.getCallback().get();
            }
        };


        retryTemplate.setSleepTime(event.getSleep())
                .setRetryCount(event.getCount());

        if(event.isAsyn()) {
            retryTemplate.submit(executorService);
        } else {
            retryTemplate.execute();
        }
    }


}
