package com.concuurent.learn.logback;


import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;

/**
 * 首先我们来何时创建日志队列，
 * 以及何时启动消费线程，这需要看AsyncAppenderBase的start方法。
 * 该方法在解析完配置AsyncAppenderBase的xml的节点元素后被调用。
 * @author Wesley
 *
 * @param <E>
 * 2019年7月9日下午3:10:17
 * @Version 1.0
 */
public class AsyncAppenderBase<E> extends UnsynchronizedAppenderBase<E> implements AppenderAttachable<E> {

	//aai是一个appender的装饰器，里面存放同步日志的appender，其中appenderCount记录aai里面附加的同步appeder的个数。
    AppenderAttachableImpl<E> aai = new AppenderAttachableImpl<E>();
    //logback中的异步日志队列是一个阻塞队列，其实就是界阻塞队列ArrayBlockingQueue，
    BlockingQueue<E> blockingQueue;

    /**
     * 其中queueSize表示有界队列的元素个数， 默认为256个.
     */
    public static final int DEFAULT_QUEUE_SIZE = 256;
    int queueSize = DEFAULT_QUEUE_SIZE;

    int appenderCount = 0;

    static final int UNDEFINED = -1;
//    discardingThreshold是一个阀值，当日志队列里面的空闲元素个数小于该值时， 新来的某些级别的日志会被直接丢弃
    int discardingThreshold = UNDEFINED;
//    neverBlock用来指示当日志队列满时是否阻塞打印日志的线程。
    boolean neverBlock = false;
   
    //worker是个线程，也就是异步日志打印模型中的单消费者线程
    Worker worker = new Worker();

    /**
     * The default maximum queue flush time allowed during appender stop. If the 
     * worker takes longer than this time it will exit, discarding any remaining 
     * items in the queue
     */
    public static final int DEFAULT_MAX_FLUSH_TIME = 1000;
    int maxFlushTime = DEFAULT_MAX_FLUSH_TIME;

    /**
     * Is the eventObject passed as parameter discardable? The base class's implementation of this method always returns
     * 'false' but sub-classes may (and do) override this method.
     * <p/>
     * <p>Note that only if the buffer is nearly full are events discarded. Otherwise, when the buffer is "not full"
     * all events are logged.
     *
     * @param eventObject
     * @return - true if the event can be discarded, false otherwise
     */
    protected boolean isDiscardable(E eventObject) {
        return false;
    }

    /**
     * Pre-process the event prior to queueing. The base class does no pre-processing but sub-classes can
     * override this behavior.
     *
     * @param eventObject
     */
    protected void preprocess(E eventObject) {
    }

    @Override
    public void start() {
        if (isStarted())
            return;
        if (appenderCount == 0) {
            addError("No attached appenders found.");
            return;
        }
        if (queueSize < 1) {
            addError("Invalid queue size [" + queueSize + "]");
            return;
        }
        //1.日志队列为有界阻塞队列
        blockingQueue = new ArrayBlockingQueue<E>(queueSize);
        //2.如果没有设置discardingThreshold则设置为队列大小的1/5
        if (discardingThreshold == UNDEFINED)
            discardingThreshold = queueSize / 5;
        addInfo("Setting discardingThreshold to " + discardingThreshold);
       //3.设置消费线程为守护线程，并设置日志名称
        worker.setDaemon(true);
        worker.setName("AsyncAppender-Worker-" + getName());
        // make sure this instance is marked as "started" before staring the worker Thread
       //4.设置启动消费线程
        super.start();
        worker.start();
    }

    @Override
    public void stop() {
        if (!isStarted())
            return;

        // mark this appender as stopped so that Worker can also processPriorToRemoval if it is invoking
        // aii.appendLoopOnAppenders
        // and sub-appenders consume the interruption
        super.stop();

        // interrupt the worker thread so that it can terminate. Note that the interruption can be consumed
        // by sub-appenders
        worker.interrupt();
        try {
            worker.join(maxFlushTime);

            // check to see if the thread ended and if not add a warning message
            if (worker.isAlive()) {
                addWarn("Max queue flush timeout (" + maxFlushTime + " ms) exceeded. Approximately " + blockingQueue.size()
                                + " queued events were possibly discarded.");
            } else {
                addInfo("Queue flush finished successfully within timeout.");
            }

        } catch (InterruptedException e) {
            addError("Failed to join worker thread. " + blockingQueue.size() + " queued events may be discarded.", e);
        }
    }
    
    /**
     * 由以上start()方法代码可，logback使用的是有界队列ArrayBlockingQueue，
     * 之所以使用有界队列是考虑内存溢出问题。在高并发下写日志的QPS会很高，
     * 如果设置为无界队列，队列本身会占用很大的内存，很可能会造成OOM。
     * 这里消费日志队列的worker线程被设置为守护线程，这意味着当主线程运行结束并且当前没有用户线程时，
     * 该worker线程会随着JVM的退出而终止，而不管日志队列里面是否还有日志任务未被处理。
     * 另外，这里设置了线程的名称，这是个很好的习惯，因为在查找问题时会很有帮助，
     * 根据线程名字就可定位线程。既然是有界队列，那么肯定需要考虑队列满的问题，
     * 是丢弃老的日志任务，还是阻塞日志打印线程直到队列有空余元素呢？要回答这个问题，
     * 我们需要看看具体进行日志打印的AsyncAppenderBase的append方法。
     */
    @Override
    protected void append(E eventObject) {
    	//5.调用AsyncAppender重写的isDiscardable方法
    	//如果当前日志的级别小于等于INFO_INT并且当前队列的剩余容量小于discardingThreshold则会直接丢弃这些日志任务。
        if (isQueueBelowDiscardingThreshold() && isDiscardable(eventObject)) {
            return;
        }
        preprocess(eventObject);
        //6.将日志任务放入队列
        put(eventObject);
    }

    private boolean isQueueBelowDiscardingThreshold() {
        return (blockingQueue.remainingCapacity() < discardingThreshold);
    }

    /**
     * 如果neverBlock被设置为false（默认为false）
     * 则会调用阻塞队列的put方法，而put是阻塞的，
     * 也就是说如果当前队列满，
     * 则在调put方法向队列放入一个元素时调用线程被阻塞直到队列有空余空间。
     * 这里可以看下put法的实现
     * @param eventObject
     */
    private void put(E eventObject) {
    	//8
        if (neverBlock) {
            blockingQueue.offer(eventObject);
        } else {
            try {//9 当日志队列满时put方法会调用await（）方法阻塞当前线程，而如果其他线程中断了该线程，那么该线程会抛出InterruptedException异常，并且当钱的日志任务就会被丢弃。在logback-classic的1.2.3版本中，则添加了不对中断进行响应的方法。
                blockingQueue.put(eventObject);
            } catch (InterruptedException e) {
                // Interruption of current thread when in doAppend method should not be consumed
                // by AsyncAppender
                Thread.currentThread().interrupt();
            }
        }
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getDiscardingThreshold() {
        return discardingThreshold;
    }

    public void setDiscardingThreshold(int discardingThreshold) {
        this.discardingThreshold = discardingThreshold;
    }

    public int getMaxFlushTime() {
        return maxFlushTime;
    }

    public void setMaxFlushTime(int maxFlushTime) {
        this.maxFlushTime = maxFlushTime;
    }

    /**
     * Returns the number of elements currently in the blocking queue.
     *
     * @return number of elements currently in the queue.
     */
    public int getNumberOfElementsInQueue() {
        return blockingQueue.size();
    }

    public void setNeverBlock(boolean neverBlock) {
        this.neverBlock = neverBlock;
    }

    public boolean isNeverBlock() {
        return neverBlock;
    }

    /**
     * The remaining capacity available in the blocking queue.
     *
     * @return the remaining capacity
     * @see {@link java.util.concurrent.BlockingQueue#remainingCapacity()}
     */
    public int getRemainingCapacity() {
        return blockingQueue.remainingCapacity();
    }
    /**
     * 一个异步appender只能绑定一个同步appender。
     * 这个appender会被放到AppenderAttachableimpl的appenderList列表里面。
     */
    public void addAppender(Appender<E> newAppender) {
        if (appenderCount == 0) {
            appenderCount++;
            addInfo("Attaching appender named [" + newAppender.getName() + "] to AsyncAppender.");
            aai.addAppender(newAppender);
        } else {
            addWarn("One and only one appender may be attached to AsyncAppender.");
            addWarn("Ignoring additional appender named [" + newAppender.getName() + "]");
        }
    }

    public Iterator<Appender<E>> iteratorForAppenders() {
        return aai.iteratorForAppenders();
    }

    public Appender<E> getAppender(String name) {
        return aai.getAppender(name);
    }

    public boolean isAttached(Appender<E> eAppender) {
        return aai.isAttached(eAppender);
    }

    public void detachAndStopAllAppenders() {
        aai.detachAndStopAllAppenders();
    }

    public boolean detachAppender(Appender<E> eAppender) {
        return aai.detachAppender(eAppender);
    }

    public boolean detachAppender(String name) {
        return aai.detachAppender(name);
    }
    /**
     * 到这里我们己经分析完了日志生产线程把日志任务放入日志队列的实现，
     * 下面一起来看消费线程是如何从队列里面消费日志任务并将其写入磁盘的。
     * 由于消费线程是一个线程，所以就从worker的run法开始。
     * @author Wesley
     *
     * 2019年7月9日下午4:02:15
     * @Version 1.0
     */
    class Worker extends Thread {

        public void run() {
            AsyncAppenderBase<E> parent = AsyncAppenderBase.this;
            AppenderAttachableImpl<E> aai = parent.aai;

            // loop while the parent is started
            //10.一直轮询直到该线程被中断
            while (parent.isStarted()) {
                try {//11.从阻塞队列里面获取元素
                    E e = parent.blockingQueue.take();
                    aai.appendLoopOnAppenders(e);
                } catch (InterruptedException ie) {
                    break;
                }
            }

            addInfo("Worker thread will flush remaining events before exiting. ");
            //12.到这里说明改线程被中断，则把队列里面的剩余日志任务刷新到磁盘
            for (E e : parent.blockingQueue) {
                aai.appendLoopOnAppenders(e);
                parent.blockingQueue.remove(e);
            }

            aai.detachAndStopAllAppenders();
        }
        /**
         * 其中代码（11）使用take方法从日志队列获取一个日志任务，
         * 如果当前队列为空则前线程会被阻塞直到队列不为空才返回。
         * 获取到日志任务后会调用AppenderAttachablelmpl的aai.appendLoopOnAppenders方法，
         * 该方法会循环调用通过addAppender注入的同步日志，appener具体实现把日志打印到磁盘。
         */
        
        
        /**
         * 本节结合logback中异步日志的实现介绍了并发组件ArrayBlockingQueue的使用，
         * 包括put、offer方法的使用场景以及它们之间的区别，take方法的使用，
         * 同时也介绍了如何使用ArrayBlockingQueue来实现一个多生产者－单消费者模型。
         * 另外使用ArrayBlockingQueue时需要注意合理设置队列的大小以免造成OOM，
         * 队列满或者剩余元素比较少时，要根据具体场景定一些抛弃策略以避免队列满时业务线程被阻塞。
         * 
         */
    }
}
