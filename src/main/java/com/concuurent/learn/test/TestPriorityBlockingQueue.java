package com.concuurent.learn.test;

import java.util.Random;

import java.util.concurrent.*;

public class TestPriorityBlockingQueue {
	
	static class Task implements Comparable<Task>{

		private int priority=0;
		
		private String taskName;
		
		@Override
		public int compareTo(Task task) {
			if(this.priority >= task.getPriority()) {
				return 1;
			}else {
				return -1;
			}
		}

		public int getPriority() {
			return priority;
		}

		public void setPriority(int priority) {
			this.priority = priority;
		}

		public String getTaskName() {
			return taskName;
		}

		public void setTaskName(String taskName) {
			this.taskName = taskName;
		}
		public void dosomething() {
           System.out.println(taskName+":"+priority);
		}
		/**
		 * 首先创建了一个Task类，该类继承了Comparable方法并重写了compareTo方法，
		 * 自定义了元素优先级比较规则。然后在main数里面创建了一个优先级队列，
		 * 并使用随机数生成器生成10个随机的有优先级的任务，并将它们添加到优先级队列。
		 * 最后优先级队列里面逐个获取任务并执行。运行上面代码，一个可能的输出如下所示
		 *  taskName0:0
		 *	taskName6:0
		 *	taskName5:2
		 *	taskName9:2
		 *	taskName4:3
		 *	taskName8:3
		 *	taskName2:4
		 *	taskName1:5
		 *	taskName7:5
		 *	taskName3:8
		 * @param args
		 * 从结果可知，
		 * 任务执行的先后顺序和它们被放入队列的先后顺序没有关系，
		 * 而是和它们的优先级有关系。
		 */
		public static void main(String[] args) {
			//创建任务并添加到队列
			PriorityBlockingQueue<Task> pBlockingQueue=new PriorityBlockingQueue<Task>();
			Random random=new Random();
			for (int i = 0; i < 10; i++) {
				Task task=new Task();
				task.setPriority(random.nextInt(10));
				task.setTaskName("taskName"+i);
				pBlockingQueue.offer(task);
			}
			//取出任务执行
			while (!pBlockingQueue.isEmpty()) {
				Task task = pBlockingQueue.poll();
				if (task!=null) {
					task.dosomething();
				}
			}
		}
		
	}

}
