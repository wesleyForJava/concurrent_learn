package com.concuurent.learn.test;

import java.util.Random;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class TestDelay {
	 static class DelayedEle implements Delayed {

	        private final long delayTime;//延迟时间

	        private final long expire; //到期时间

	        private String taskName; //任务名称

	        public DelayedEle(long delayTime, String taskName) {
	            this.delayTime = delayTime;
	            this.taskName = taskName;
	            expire = System.currentTimeMillis() + delayTime;
	        }

	        /**
	         * 剩余时间=到期时间-当前时间
	         */
	        @Override
	        public long getDelay(TimeUnit unit) {
	            return unit.convert(this.expire - System.currentTimeMillis(), TimeUnit.MICROSECONDS);
	        }
            
	        /**
	         * 优先级队列里面的优先级规则
	         */
	        @Override
	        public int compareTo(Delayed o) {
	            return (int)(this.getDelay(TimeUnit.MICROSECONDS) - o.getDelay(TimeUnit.MICROSECONDS));
	        }

	        @Override
	        public String toString() {
	            return "DelayedEle{" +
	                    "delayTime=" + delayTime +
	                    ", expire=" + expire +
	                    ", taskName='" + taskName + '\'' +
	                    '}';
	        }
	    }
    /**
     * 首先创建延迟任务DelayedEle类，其中delayTime表示当前任务需要延迟多少ms时间过期，
     * expire则是当前时间的ms值加上delayTime的值。另外，实现了Delayed接口，
     * 实现了longgetDelay(TimeUnitunit）方法用来获取当前元素还剩下多少时间过期，
     * 实现了intcompareTo(Delayedo）方法用来决定优先级队列元素的比较规则。
     *在main函数内首先创建了一个延迟队列，
     *然后使用随机数生成器生成了10个延迟任务，
     *最后通过循环依次获取延迟任务，并打印。
     *运行上面代码，一个可能的输出如下所示。
     * DelayedEle{delayTime=93, expire=1562145371697, taskName='taskName7'}
	 * DelayedEle{delayTime=103, expire=1562145371707, taskName='taskName1'}
	 * DelayedEle{delayTime=104, expire=1562145371708, taskName='taskName9'}
	 * DelayedEle{delayTime=163, expire=1562145371767, taskName='taskName8'}
	 * DelayedEle{delayTime=204, expire=1562145371808, taskName='taskName4'}
	 * DelayedEle{delayTime=249, expire=1562145371853, taskName='taskName3'}
	 * DelayedEle{delayTime=258, expire=1562145371862, taskName='taskName0'}
	 * DelayedEle{delayTime=266, expire=1562145371870, taskName='taskName6'}
	 * DelayedEle{delayTime=306, expire=1562145371910, taskName='taskName5'}
	 * DelayedEle{delayTime=422, expire=1562145372026, taskName='taskName2'}
	 * 可见，出队的顺序和delay时间有关，而与创建任务的顺序无关。
     * @param args
     */
	public static void main(String[] args) {
		// 1创建Delay队列
		DelayQueue<DelayedEle> delayed = new DelayQueue<TestDelay.DelayedEle>();
		// 2创建延迟任务
		Random random = new Random();
		for (int i = 0; i < 10; i++) {
			DelayedEle element = new DelayedEle(random.nextInt(500), "taskName" + i);
			delayed.offer(element);
		}
		// 3依次取出任务并打印
		DelayedEle ele = null;
		// 3.1循环，如果想避免虚假唤醒，则不能把全部元素打印出来。
		try {
			for (;;) {
				// 3.2获取过期任务并打印
				while ((ele = delayed.take()) != null) {
                 System.out.println(ele.toString());
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}
}
