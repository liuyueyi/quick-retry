package com.hui.quickretry.template;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * 重试模板类
 * <p>
 * 1. 异步重试
 * 2. 重试次数
 * 3. 间隔时间
 * 4. 重试队列
 * <p>
 * Created by yihui on 2017/8/30.
 */
@Slf4j
public abstract class RetryTemplate {

    private static final int DEFAULT_RETRY_COUNT = 3;

    private int retryCount = DEFAULT_RETRY_COUNT;

    // 重试的睡眠时间
    private int sleepTime = 0;

    public int getSleepTime() {
        return sleepTime;
    }

    public RetryTemplate setSleepTime(int sleepTime) {
        if(sleepTime < 0) {
            throw new IllegalArgumentException("sleepTime should equal or bigger than 0");
        }

        this.sleepTime = sleepTime;
        return this;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public RetryTemplate setRetryCount(int retryCount) {
        if (retryCount <= 0) {
            throw new IllegalArgumentException("retryCount should bigger than 0");
        }

        this.retryCount = retryCount;
        return this;
    }

    /**
     * 重试的业务执行代码
     * 失败时请抛出一个异常
     *
     * todo 确定返回的封装类，根据返回结果的状态来判定是否需要重试
     *
     * @return
     */
    protected abstract Object doBiz() throws Throwable;


    public Object execute() throws InterruptedException {
        for (int i = 0; i <= retryCount; i++) {
            try {
                return doBiz();
            } catch (Throwable  e) {
                log.error("业务执行出现异常，e: {}", e);
                Thread.sleep(sleepTime);
            }
        }

        return null;
    }


    public Object submit(ExecutorService executorService) {
        if (executorService == null) {
            throw new IllegalArgumentException("please choose executorService!");
        }

        return executorService.submit((Callable) () -> execute());
    }

}
