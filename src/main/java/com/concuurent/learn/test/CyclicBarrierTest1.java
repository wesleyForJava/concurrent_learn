package com.concuurent.learn.test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.concuurent.learn.juc.CyclicBarrier;

public class CyclicBarrierTest1 {
   
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
					System.out.println(Thread.currentThread()+"task1-1");
					System.out.println(Thread.currentThread()+"enter barrier");
					cyclicBarrier.await();
					System.out.println(Thread.currentThread()+"exit barrier");
				} catch (InterruptedException | BrokenBarrierException e) {
					e.printStackTrace();
				}
			}
		},"A");
		
		executorService.submit(new Runnable() {
			
			@Override
			public void run() {
				try {
					System.out.println(Thread.currentThread()+"task1-2");
					System.out.println(Thread.currentThread()+"enter barrier");
					cyclicBarrier.await();
					System.out.println(Thread.currentThread()+"exit barrier");
				} catch (InterruptedException | BrokenBarrierException e) {
					e.printStackTrace();
				}
				
			}
		},"B");
		
		//关闭线程池
		executorService.shutdown();
		/**
		 * 如上代码创建了一个CyclicBanier对象，其第一个参数为计数器初始值，
		 * 第二个参数Runable是当计数器值为0时需要执行的任务。main函数里面首先创建了一个大小为2的线程池，
		 * 然后添加两个子任务到线程池，每个子任务在执行完自己的逻辑后会调用await方法。一开始计数器值为2，
		 * 当第一个线程调用await方法时，计数器值会递减为1。由于此时计数器值不为0，所以当前线程就到了屏障点而被阻塞。
		 * 然后第二个线程调用await时，会进入屏障，计数器值也会递减，现在计数器值为0，这时就会去执行CyclicBanier构造
		 * 函数中的任务，执行完毕后退出屏障点，并且唤醒被阻塞的第二个线程，这时候第一个线程也会退出屏障点继续向下运行。
		 * <p>
		 * 上面的例子说明了多个线程之间是相互等待的，假如计数器值为N那么随后调用await方法的N-1个线程都会
		 * 因为到达屏障点而被阻塞，当第N个线程周用await后，计数器值为0了，这时候第N个线程才会发出通知
		 * 唤醒前面的N-1个线程。也就是当全部线程都到达屏障点时才能一块继续向下执行。对于这个例子来说，
		 * 使用CountDownLatch也可以得到类似的输出结果。下面再举个例子来说明CyclicBaiTier的可复用性。
		 */
	}

}
