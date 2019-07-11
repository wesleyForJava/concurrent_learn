package com.concuurent.learn.thread;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

//创建线程池也需要制定线程池名称
public class ThreadPoolExecutorTest {
//  static	ThreadPoolExecutor executorOne=new ThreadPoolExecutor(5, 5, 1, TimeUnit.MINUTES, new LinkedBlockingDeque<Runnable>());
//  static	ThreadPoolExecutor executorTwo=new ThreadPoolExecutor(5, 5, 1, TimeUnit.MINUTES, new LinkedBlockingDeque<Runnable>());
  
  
  static ThreadPoolExecutor executorOne = new ThreadPoolExecutor(5, 5, 1, TimeUnit.MINUTES,
          new LinkedBlockingQueue<>(), new NamedThreadFactory("ASYN-ACCEPT-POOL"));
  static ThreadPoolExecutor executorTwo = new ThreadPoolExecutor(5, 5, 1, TimeUnit.MINUTES,
          new LinkedBlockingQueue<>(), new NamedThreadFactory("ASYN-PROCESS-POOL"));
	
	public static void main(String[] args) {
		//下面通过简单的代码来说明不指定线程池名称为何难定位问题，代码如下：
		executorOne.execute(new Runnable() {
			@Override
			public void run() {
                 System.out.println("接受用户连接线程");
                 throw new NullPointerException();
			}
		});
		executorTwo.execute(new Runnable() {
			@Override
			public void run() {
				System.out.println("具体处理业务请求线程");
			}
		});
		executorOne.shutdown();
		executorTwo.shutdown();
	}
	
//	同样，我们并不知道是哪个模块的线程池抛出了这个异常，
//	那么我们看下这个pool-1-thread-1是如何来的。
//	其实这里使用了线程默认的ThreadFactory，查看线程池创建的源码如下。
//    public ThreadPoolExecutor(int corePoolSize,
//            int maximumPoolSize,
//            long keepAliveTime,
//            TimeUnit unit,
//            BlockingQueue<Runnable> workQueue) {
//this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
//Executors.defaultThreadFactory(), defaultHandler);
//}
	
    public static ThreadFactory defaultThreadFactory() {
        return new DefaultThreadFactory();
    }
    

    /**
     * The default thread factory
     */
    static class DefaultThreadFactory implements ThreadFactory {
    	//1  poolNumber是static的原子变量，
//    	用来记录当前线程池的编号，它是应用级别的，
//    	所有程池共用一个，比如创建第一个线程池时线程池编号为1，
//    	创建第二个线程池时线程池的编号为2，所以pool-1-thread-1里面的pool-1中的1就是这个。
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        //2 threadNumber是线程池级别的，每个线程池使用该变量来记录该线程池中线程的编号，所pool-1-thread-里面的thread-1中的1就这个值。       
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        //3 namePrefix是线程池中线程名称的前缀，默认固定为pool
        private final String namePrefix;

        DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                                  Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" +
                          poolNumber.getAndIncrement() +
                         "-thread-";
        }

        public Thread newThread(Runnable r) {
        	//4 具体创建线程，线程的名称是使用namePrefix+threadNumber.getAndlncrement（）拼接的。
            Thread t = new Thread(group, r,
                                  namePrefix + threadNumber.getAndIncrement(),
                                  0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
//    由此我知道，只需对DefaultThreadFactory的代码中的namePrefix的初始化做下手脚，
//    即当需要创建线程池时传入与业务相关的namePrefix名称就可以了，代码如下。
 // 命名线程工厂
    static class NamedThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NamedThreadFactory(String name) {

            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            if (null == name || name.isEmpty()) {
                name = "pool";
            }

            namePrefix = name + "-" + poolNumber.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
    //从 ASYN-ACCEPT-POOL-1-thread-1就可以知道是接受链接线程池抛出的异常。
    
//    本节通过简单的例子介绍了为何不给线程或者线程池起名字会给问题排查带来麻烦，
//    然后通过源码原理介绍线程和线程池名称是默认名称是如何来的，
//    以及如何自定义线程池名称，以便问题追溯。
//    另外，在run方法内使用try-catch块，避免将异常抛到run方法之外，同时打印日志也是一个最佳践。
    
    
}
