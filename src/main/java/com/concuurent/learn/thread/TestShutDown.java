package com.concuurent.learn.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 使用线程池时候当程序结束时候记得调用shutdown关闭线程池
*日常开发中为了便于线程的有效复用，线程池是经常会被用的工具，
*然而线程池使用完后如果不调用shutdown会导致线程池资源一直不会被释放。
*下面通过简单例子来说明该问题。
*下面通过一个例子说明当不调用线程池对象的shutdown方法后，
*当线程池里面的任务执行完毕后主线程这个JVM不会退出。
*/
public class TestShutDown {
	
//	 static void asynExecuteOne() {
//	        ExecutorService executor = Executors.newSingleThreadExecutor();
//	        executor.execute(new  Runnable() {
//	            public void run() {
//	                System.out.println("--async execute one ---");
//	            }
//	        });
//	    }
//	    
//	    static void asynExecuteTwo() {
//	        ExecutorService executor = Executors.newSingleThreadExecutor();
//	        executor.execute(new  Runnable() {
//	            public void run() {
//	                System.out.println("--async execute two ---");
//	            }
//	        });
//	    }
	    
	    public static void main(String[] args) {
	    	//(1)同步执行
	        System.out.println("---sync execute---");
	       //(2)异步执行操作one
	        asynExecuteOne();
	       //(3)异步执行操作two
	        asynExecuteTwo();
	       //(4)执行完毕
	        System.out.println("---execute over---");
		}
	    
//	    如上代码主线程里面首先同步执行了操作（1）然后执行操作（2）（3），
//	    操作（2）（3）使用线程池的一个线程执行异步操作，我们期望当主线程和操操作（2）（3）
//	    执行完线程池里面的任务后整个JVM就会退出，但是执行结果却如下：
//	    ---sync execute---
//	    --async execute one ---
//	    ---execute over---
//	    --async execute two ---
//	    右上角红色方块说明JVM进程还没有退出，
//	    Mac上执行ps -eaf|grep java后发现Java进程还是存在的，
//	    这是什么情况那？修改操作（2）（3）在方法里面添加调用线程池的shutdown方法如下代码：
	    static void asynExecuteOne() {
	        ExecutorService executor = Executors.newSingleThreadExecutor();
	        executor.execute(new  Runnable() {
	            public void run() {
	                System.out.println("--async execute one ---");
	            }
	        });
	        
	        executor.shutdown();
	    }
	    
	    static void asynExecuteTwo() {
	        ExecutorService executor = Executors.newSingleThreadExecutor();
	        executor.execute(new  Runnable() {
	            public void run() {
	                System.out.println("--async execute two ---");
	            }
	        });
	        
	        executor.shutdown();
	    }

//	    在执行就会发现JVM已经退出了，
//	    使用ps -eaf|grep java后发现Java进程以及不存在了，
//	    这说明只有调用了线程池的shutdown方法后当线程池任务执行完毕后线程池资源才会释放。
	    
//	    下面看下为何如此那？大家或许还记得基础篇讲解的守护线程与用户线程吧，
//	    JVM退出的条件是当前不存在用户线程，而线程池默认的ThreadFactory创建的线程是用户线程，
	    
//	    static class DefaultThreadFactory implements ThreadFactory {
//	        ...
//	        public Thread newThread(Runnable r) {
//	            Thread t = new Thread(group, r,
//	                                  namePrefix + threadNumber.getAndIncrement(),
//	                                  0);
//	            if (t.isDaemon())
//	                t.setDaemon(false);
//	            if (t.getPriority() != Thread.NORM_PRIORITY)
//	                t.setPriority(Thread.NORM_PRIORITY);
//	            return t;
//	        }
//	    }
//	    
//	    如上代码可知线程池默认的线程工厂创建创建的都是用户线程。
//	    而线程池里面的核心线程是一直会存在的，如果没有任务则会阻塞，
//	    所以线程池里面的用户线程一直会存在.而shutdown方法的作用就是让这些核心线程终止，
//	    下面在简单看下shutdown重要代码：
	    
//	    public void shutdown() {
//	        final ReentrantLock mainLock = this.mainLock;
//	        mainLock.lock();
//	        try {
//	            ...
//	            //设置线程池状态为SHUTDOWN
//	            advanceRunState(SHUTDOWN);
//	            //中断所有的工作线程
//	            interruptIdleWorkers();
//	            ...
//	        } finally {
//	            mainLock.unlock();
//	        }
//	           ...
//	        }
//	    可知shutdown里面设置了线程池状态为SHUTDOWN，
//	    并且设置了所有工作线程的中断标志，
//	    那么下面在简单看下工作线程Worker里面是不是发现中断标志被设置了就会退出了。
	    
//	    final void runWorker(Worker w) {
//            ...
//            try {
//            while (task != null || (task = getTask()) != null) {
//               ...            
//            }
//            ...
//          } finally {
//            ...
//        }
//    }
//
//private Runnable getTask() {
//        boolean timedOut = false; 
//
//        for (;;) {
//            ...
//            //(1)
//            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
//                decrementWorkerCount();
//                return null;
//            }
//            
//            try {
//                //(2)
//                Runnable r = timed ?
//                    workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
//                    workQueue.take();
//                if (r != null)
//                    return r;
//                timedOut = true;
//            } catch (InterruptedException retry) {
//                timedOut = false;
//            }
//        }
//    }
	    
//	    如上代码正常情况下如果队列里面没有任务了，
//	    工作线程阻塞到代码（2）等待从工工作队列里面获取一个任务，
//	    这时候如果调用了线程池的shutdown命令而shutdown命令会中断所有工作线程，
//	    所以代码（2）会抛出处抛出InterruptedException异常而返回，而这个异常被catch了，
//	    所以继续执行代码（1），而shutdown时候设置了线程池的状态为SHUTDOWN所以getTask方法返回了null，
//	    所以runWorker方法退出循环，该工作线程就退出了。
	    
	    
	    
	    
	    /**
		     * 本节通过一个简单的使用线程池异步执行任务案例介绍了线程池使用完后
		     * 要如果不调用shutdown会导致线程池的线程资源一直不会被释放，
		     * 然后通过源码分析了没有被释放的原因。
		     * 所以日常开发中使用线程池的场景一定不要忘记了调用shutdown方法设置线程池状态和中断工作线程池
	     */



	    
}
