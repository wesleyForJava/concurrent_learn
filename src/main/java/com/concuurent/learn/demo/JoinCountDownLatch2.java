package com.concuurent.learn.demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * 其实joinCountDownLatch的代码还不够优雅在项目实践中一般都避免直接操作线程，
 * 而是使用ExecutorService线程池来管理。使用ExecutorService时传递的参数是Runable或Callable对象，
 * 这时候你没有办法直接调用这些线程的join（）方法，这就需要选择使用CountDownLatch了。将上面代码修改为如下：
 * @author Wesley
 *
 * 2019年7月5日下午4:52:30
 * @Version 1.0
 */
public class JoinCountDownLatch2 {
	
	//创建一个CountDownLatch实例
	private static CountDownLatch countDownLatch = new CountDownLatch(2);
	public static void main(String[] args) throws InterruptedException {
		ExecutorService fixedService = Executors.newFixedThreadPool(2);
		fixedService.submit(new Runnable() {
			
			@Override
			public void run() {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					countDownLatch.countDown();
				}
				
				System.out.println("child threadOne over!");
			}
		});
	fixedService.submit(new Runnable() {
		@Override
		public void run() {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				countDownLatch.countDown();
			}
			
			System.out.println("child threadTwo over!");
		}
	});
			System.out.println("wait all child thread over!");
			
			countDownLatch.await();
			
			System.out.println("all child thread over!");
			
			fixedService.shutdown();
	}
	/*
	 * 这总结下CountDownLatch与join法的区别。
	 * 一个区别是，调用一个子线程的join()方法后，该线程会一直被阻塞直到子线程运行完毕，
	 * 而CountDownLatch则使用计数器来允许子线程运行完或者在运行中递减计数，
	 * 也是CountDownLatch可以在子线程运行的任时候让await方法返回不一定必须等到线程结束。
	 * 另外，使用线程池来理线程时一般都是直接添加Runable到线程池，这时候就没有办法再调用线程的join方法了，
	 * 就是说countDownLatch相比join方法让我们对线程同步有更灵活的控制。
	 */

}
