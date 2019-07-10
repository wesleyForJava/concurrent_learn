package com.concuurent.learn.logback;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 
 * AsyncAppender继承自AsyncAppenderBase，
 * 其中后者具体实现了异步日志模型的主要功能，
 * 前者只是重写了其中的一些方法。由该图可知，
 * logback中的异步日志队列是一个阻塞队列，
 * 其实就是界阻塞队列ArrayBlockingQueue，
 * 其中queueSize表示有界队列的元素个数， * 默认为256个.<br>
 * worker是个线程，也就是异步日志打印模型中的单消费者线程.
 * aai是一个appender的装饰器，里面存放同步日志的appender，
 * 其中appenderCount记录aai里面附加的同步appeder的个数。
 * neverBlock用来指示当日志队列满时是否阻塞打印日志的线程。
 * discardingThreshold是一个阀值，当日志队列里面的空闲元素个数小于该值时，
 * 新来的某些级别的日志会被直接丢弃，下面会具体讲。
 * 
 * 首先我们来何时创建日志队列，
 * 以及何时启动消费线程，这需要看AsyncAppenderBase的start方法。
 * 该方法在解析完配置AsyncAppenderBase的xml的节点元素后被调用。
 * @author Wesley
 *
 * @param <E>
 * 2019年7月9日下午3:05:31
 * @Version 1.0
 */
public class AsyncAppender extends AsyncAppenderBase<ILoggingEvent> {

    boolean includeCallerData = false;

    /**
     * Events of level TRACE, DEBUG and INFO are deemed to be discardable.
     * @param event
     * @return true if the event is of level TRACE, DEBUG or INFO false otherwise.
     * 如果当前日志的级别小于等于INFO_INT并且当前队列的剩余容量小于discardingThreshold则会直接丢弃这些日志任务。
     */
    protected boolean isDiscardable(ILoggingEvent event) {
        Level level = event.getLevel();
        return level.toInt() <= Level.INFO_INT;
    }

    protected void preprocess(ILoggingEvent eventObject) {
        eventObject.prepareForDeferredProcessing();
        if (includeCallerData)
            eventObject.getCallerData();
    }

    public boolean isIncludeCallerData() {
        return includeCallerData;
    }

    public void setIncludeCallerData(boolean includeCallerData) {
        this.includeCallerData = includeCallerData;
    }

}
