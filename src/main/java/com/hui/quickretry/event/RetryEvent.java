package com.hui.quickretry.event;

import lombok.Data;

import java.util.function.Supplier;

/**
 * Created by yihui on 2017/8/30.
 */
@Data
public class RetryEvent {

    /**
     * 重试间隔时间， ms为单位
     */
    private int sleep;


    /**
     * 重试次数
     */
    private int count;


    /**
     * 是否异步重试
     */
    private boolean asyn;


    /**
     * 回调方法
     */
    private Supplier<Object> callback;
}
