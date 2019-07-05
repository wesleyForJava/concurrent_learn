package com.concuurent.learn.demo;

import java.util.concurrent.CountDownLatch;

/**
 * 如下代码中，创建了一个CountDownLatch实例，
 * 因为有两个子线程所以构造函数的传参为2。
 * 主线程调用countDownLatch.await(）方法后会被阻塞。
 * 子线程执行完毕后调用countDownLatch.countDown（）方法让countDownLatch内部的计数器减1，
 * 所有子线程执行完毕并调用countDown(）方法后计数器会变为0，这时候主线程的await（）方法才会返回。
 * @author Wesley
 *
 * 2019年7月5日下午4:49:04
 * @Version 1.0
 */
public class JoinCountDownLatch {
	
	//创建一个CountDownLatch实例
		private static volatile CountDownLatch countdownLatch = 
				new CountDownLatch(2);
		
		
		public static void main(String[] args) throws InterruptedException {
			Thread threadOne = new Thread(new Runnable() {
				
				@Override
				public void run() {
					
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					} finally {
						countdownLatch.countDown();
						System.out.println(countdownLatch.getCount());
					}
					System.out.println("child threadOne over!");
				}
			});
			
			
			
			
			Thread threadTwo = new Thread(new Runnable() {
				
				@Override
				public void run() {
					
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					} finally {
						countdownLatch.countDown();
						System.out.println(countdownLatch.getCount());
					}
					System.out.println("child threadTwo over!");
				}
			});
			
			
			threadOne.start();
			threadTwo.start();
			
			System.out.println("wait all child thread over!");
			
			
			countdownLatch.await();
			
			System.out.println("all child thread over!");
		}
}
