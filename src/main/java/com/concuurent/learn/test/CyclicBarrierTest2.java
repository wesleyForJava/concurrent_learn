package com.concuurent.learn.test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.concuurent.learn.juc.CyclicBarrier;
/**
 * 下面再举个例子来说明CyclicBaiTier的可复用性。
 * 假设一个任务由阶段1、阶段2和阶段3组成，每个线程要串行地执行阶段1阶段2和阶段3，
 * 当多个线程执行该任务时,必须要保证所有线程的阶段1全部完成后才能进入阶段2执行，
 * 当所有线程的阶段全部完后才能进入阶段3执行。下面使用CyclicBanier来完成这个需求。
 * @author Wesley
 *
 * 2019年7月8日上午10:51:54
 * @Version 1.0
 */
public class CyclicBarrierTest2 {
   
	//创建一个CyclicBarrier实例，添加一个所有子线程全部到达屏障后执行的任务
	
	private static CyclicBarrier cyclicBarrier=new CyclicBarrier(2,new Runnable() {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println(Thread.currentThread()+"task1 merge result");
		}
	});
	
	public static void main(String[] args) {
		//创建一个固定线程数为2的线程池
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		
		//将线程A添加到线程池
		executorService.submit(new Runnable() {
			
			@Override
			public void run() {
				try {
					System.out.println(Thread.currentThread()+"step-1");
					cyclicBarrier.await();
					System.out.println(Thread.currentThread()+"step-2");
					cyclicBarrier.await();
					System.out.println(Thread.currentThread()+"step-3");
				} catch (InterruptedException | BrokenBarrierException e) {
					e.printStackTrace();
				}
			}
		},"A");
		
		executorService.submit(new Runnable() {
			
			@Override
			public void run() {
				try {
					System.out.println(Thread.currentThread()+"step-1");
					cyclicBarrier.await();
					System.out.println(Thread.currentThread()+"step-2");
					cyclicBarrier.await();
					System.out.println(Thread.currentThread()+"step-3");
				} catch (InterruptedException | BrokenBarrierException e) {
					e.printStackTrace();
				}
				
			}
		},"B");
		
		//关闭线程池
		executorService.shutdown();
		
	}
/**
 * 如上代码中，每个子线程在执行完阶段l后都调用了await方法，
 * 等到所有线程都达屏障点后才会块往下执行，
 * 这就保证了所有线程都完成了阶段1后才会开始执行阶段2。
 * 然后在阶段2后面调用了await方法，这保证了所有线程都完成了阶段2后，
 * 才始阶段3的执行。这个功能使用单个CountDownLatch是无法完成的。
 */
}
