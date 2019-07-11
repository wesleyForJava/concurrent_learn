package com.concuurent.learn.thread;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.lang.ThreadLocal;

//线程池中使用ThreadLocal导致的内存泄露
public class ThreadPoolTest {
	//下面先看线程池中使用ThreadLocal的例子：
	
	
	static class LocalVariable {
        @SuppressWarnings("unused")
		private Long[] a = new Long[1024*1024];
    }

    // (1)
    final static ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(5, 5, 1, TimeUnit.MINUTES,
            new LinkedBlockingQueue<>());
    // (2)
    final static ThreadLocal<LocalVariable> localVariable = new ThreadLocal<LocalVariable>();

    public static void main(String[] args) throws InterruptedException {
        // (3)
        for (int i = 0; i < 50; ++i) {
            poolExecutor.execute(new Runnable() {
                public void run() {
                    // (4)
                    localVariable.set(new LocalVariable());
                    // (5)
                    System.out.println("use local varaible");
                    localVariable.remove();

                }
            });

            Thread.sleep(1000);
        }
        // (6)
        System.out.println("pool execute over");
    }
//    代码（1）创建了一个核心线程数和最大线程数为5的线程池，这个保证了线程池里面随时都有5个线程在运行。
//    代码（2）创建了一个ThreadLocal的变量，泛型参数为LocalVariable，LocalVariable内部是一个Long数组。
//    代码（3）向线程池里面放入50个任务
//    代码（4）设置当前线程的localVariable变量，也就是把new的LocalVariable变量放入当前线程的threadLocals变量。
//    由于没有调用线程池的shutdown或者shutdownNow方法所以线程池里面的用户线程不会退出，进而JVM进程也不会退出。

//    运行结果一的代码，在设置线程的localVariable变量后没有调用localVariable.remove()
//    方法，导致线程池里面的5个线程的threadLocals变量里面的new LocalVariable()实例没有被释放，
//    虽然线程池里面的任务执行完毕了，但是线程池里面的5个线程会一直存在直到JVM退出。
//    这里需要注意的是由于localVariable被声明了static，虽然线程的ThreadLocalMap里面是对localVariable的弱引用，
//    localVariable也不会被回收。
//    运行结果二的代码由于线程在设置localVariable变量后即使调用了localVariable.remove()方法进行了清理，
//    所以不会存在内存泄露。
//    总结：线程池里面设置了ThreadLocal变量一定要记得及时清理，因为线程池里面的核心线程是一直存在的，
//    如果不清理，那么线程池的核心线程的threadLocals变量一直会持有ThreadLocal变量。


    
}
