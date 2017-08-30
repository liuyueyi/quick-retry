## 背景
> 当业务执行失败之后，进行重试是一个非常常见的场景，那么如何在业务代码中优雅的实现重试机制呢？

## 设计

我们的目标是实现一个优雅的重试机制，那么先来看下怎么样才算是优雅

- 无侵入：这个好理解，不改动当前的业务逻辑，对于需要重试的地方，可以很简单的实现
- 可配置：包括重试次数，重试的间隔时间，是否使用异步方式等
- 通用性：最好是无改动（或者很小改动）的支持绝大部分的场景，拿过来直接可用


针对上面的几点，分别看下右什么好的解决方案

### 几种解决思路
> 要想做到无侵入或者很小的改动，一般来将比较好的方式就是切面或者消息总线模式；可配置和通用性则比较清晰了，基本上开始做就表示这两点都是基础要求了，唯一的要求就是不要硬编码，不要写死，基本上就能达到这个基础要求，当然要优秀的话，要做的事情并不少

#### 切面方式

这个思路比较清晰，在需要添加重试的方法上添加一个用于重试的自定义注解，然后在切面中实现重试的逻辑，主要的配置参数则根据注解中的选项来初始化

优点：
- 真正的无侵入

缺点：
- 某些方法无法被切面拦截的场景无法覆盖（如spring-aop无法切私有方法，final方法）
- 直接使用aspecj则有些小复杂；如果用spring-aop，则只能切被spring容器管理的bean

#### 消息总线方式

这个也比较容易理解，在需要重试的方法中，发送一个消息，并将业务逻辑作为回调方法传入；由一个订阅了重试消息的consumer来执行重试的业务逻辑

优点：
- 重试机制不受任何限制，即在任何地方你都可以使用
- 利用`EventBus`框架，可以非常容易把框架搭起来

缺点：
- 业务侵入，需要在重试的业务处，主动发起一条重试消息
- 调试理解复杂（消息总线方式的最大优点和缺点，就是过于灵活了，你可能都不知道什么地方处理这个消息，特别是新的童鞋来维护这段代码时）
- 如果要获取返回结果，不太好处理, 上下文参数不好处理


#### 模板方式
> 把这个单独捞出来，主要是某些时候我就一两个地方要用到重试，简单的实现下就好了，也没有必用用到上面这么重的方式；而且我希望可以针对代码快进行重试

这个的设计还是非常简单的，基本上代码都可以直接贴出来，一目了然：

```java
public abstract class RetryTemplate {

    private static final int DEFAULT_RETRY_TIME = 1;

    private int retryTime = DEFAULT_RETRY_TIME;

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

    public int getRetryTime() {
        return retryTime;
    }

    public RetryTemplate setRetryTime(int retryTime) {
        if (retryTime <= 0) {
            throw new IllegalArgumentException("retryTime should bigger than 0");
        }

        this.retryTime = retryTime;
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
    protected abstract Object doBiz() throws Exception;


    public Object execute() throws InterruptedException {
        for (int i = 0; i < retryTime; i++) {
            try {
                return doBiz();
            } catch (Exception e) {
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
```

预留一个`doBiz`方法由业务方来实现，在其中书写需要重试的业务代码，然后执行即可

使用case也比较简单

```java
public void retryDemo() throws InterruptedException {
    Object ans = new RetryTemplate() {
        @Override
        protected Object doBiz() throws Exception {
            int temp = (int) (Math.random() * 10);
            System.out.println(temp);
  
            if (temp > 3) {
                throw new Exception("generate value bigger then 3! need retry");
            }
  
            return temp;
        }
    }.setRetryTime(10).setSleepTime(10).execute();
    System.out.println(ans);
}
```

优点：

- 简单（依赖简单：引入一个类就可以了； 使用简单：实现抽象类，讲业务逻辑填充即可；）
- 灵活（这个是真正的灵活了，你想怎么干都可以，完全由你控制）

缺点：

- 强侵入
- 代码臃肿

## 实现

上面的模板方式基本上就那样了，接下来谈到的实现，毫无疑问将是切面和消息总线的方式

### 1. 切面方式

实现依然是基于前面的模板方式做的，简单来看就是添加一个切面，内部实现模版类即可

注解定义如下

```java
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RetryDot {
    /**
     * 重试次数
     * @return
     */
    int count() default 0;


    /**
     * 重试的间隔时间
     * @return
     */
    int sleep() default 0;


    /**
     * 是否支持异步重试方式
     * @return
     */
    boolean asyn() default false;
}
```

切面逻辑如下

```java
@Aspect
@Component
@Slf4j
public class RetryAspect {

    ExecutorService executorService = new ThreadPoolExecutor(3, 5,
            1, TimeUnit.MINUTES,
            new LinkedBlockingQueue<Runnable>());


    @Around(value = "@annotation(retryDot)")
    public Object execute(ProceedingJoinPoint joinPoint, RetryDot retryDot) throws Exception {
        RetryTemplate retryTemplate = new RetryTemplate() {
            @Override
            protected Object doBiz() throws Throwable {
                return joinPoint.proceed();
            }
        };

        retryTemplate.setRetryCount(retryDot.count())
                .setSleepTime(retryDot.sleep());


        if (retryDot.asyn()) {
            return retryTemplate.submit(executorService);
        } else {
            return retryTemplate.execute();
        }
    }
}
```

### 2. 消息方式

依然是在EventBus的基础上进行开发，结果写到一半，发现这种方式局限性还蛮大，基本上不太适合实际使用，下面依然给出实现逻辑

定义的重试事件`RetryEvent`

```java
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
```

消息处理类

```java
@Component
public class RetryProcess {

    ExecutorService executorService = new ThreadPoolExecutor(3, 5,
            1, TimeUnit.MINUTES,
            new LinkedBlockingQueue<Runnable>());

    private  static EventBus eventBus = new EventBus("retry");
    
    public static void post(RetryEvent event) {
        eventBus.post(event);
    }

    public static void register(Object handler) {
        eventBus.register(handler);
    }

    public static void unregister(Object handler) {
        eventBus.unregister(handler);
    }

    @PostConstruct
    public void init() {
        register(this);
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
```

问题比较明显，返回值以及输入参数的传入，比较不好处理


## 测试

测试下上面两种使用方式, 定义一个实例Service，分别采用注解和消息两种方式

```java
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
```

因为使用了切面，在spring的基础上进行开发的，所以需要加上对应的配置信息 `aop.xml`

```xml
<context:component-scan base-package="com.hui.quickretry"/>

<context:annotation-config/>
<aop:aspectj-autoproxy/>
```

Test代码

```java
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
```

输出

```
genBigNum 9
----9----
now num: 1
------------------
genBigNum 9
----9----
now num: 4
now num: 1
------------------
genBigNum 5
----5----
now num: 6
now num: 6
now num: 0
------------------
```

## 其他

`guava-retrying`和 `spring-retry` 实际上是更好的选择，设计与实现都非常优雅，实际的项目中完全可以直接使用

相关代码：

[https://github.com/liuyueyi/quick-retry](https://github.com/liuyueyi/quick-retry)



## 参考

[Retry重试机制](http://www.2cto.com/kf/201612/582834.html)


