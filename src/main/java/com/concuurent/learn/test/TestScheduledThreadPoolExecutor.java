package com.concuurent.learn.test;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 之所以ScheduledThreadPoolExecutor的其他任务不受抛出异常的任务的影响，
 * 是因为在ScheduledThreadPoolExecutor中的ScheduledFutureTask任务中catch掉了异常，
 * 但是在线程池任务的run方法内使用catch捕获异常并打印日志是最佳实践。
 * 
 * ScheduledThreadPoolExecutor是并发包提供的组件，
 * 其提供的功能包含但不限于Timer.
 * Timer是固定的多线程生产单线程消费，
 * 但是ScheduledThreadPoolExecutor是可以配置的，既可以是多线程生产单线程消费也可以是多线程生产多线程消费，
 * 所以在日常开发中使用定时器功能时应该优先使用ScheduledThreadPoolExecutor。
 * @author Wesley
 *
 * 2019年7月10日下午4:27:00
 * @Version 1.0
 */
public class TestScheduledThreadPoolExecutor {
	
	static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor=new ScheduledThreadPoolExecutor(1);
	
	public static void main(String[] args) {
		scheduledThreadPoolExecutor.schedule(new Runnable() {
			
			@Override
			public void run() {
				System.out.println("--- one Task ---");
				try {
					Thread.sleep(1000);
					throw new RuntimeException("error");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
		}, 500, TimeUnit.MICROSECONDS);
		
		scheduledThreadPoolExecutor.schedule(new Runnable() {
			@Override
			public void run() {
				for (int i = 0; i < 2; i++) {
					System.out.println("--- two Task ---");
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}, 1000, TimeUnit.MICROSECONDS);
		scheduledThreadPoolExecutor.shutdown();
	}

}
