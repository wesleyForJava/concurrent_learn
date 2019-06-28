package com.concuurent.learn.juc;

/**Point类里面有两个成员变量(x,y）用来表示一个点的二维坐标，
 * 和三个操作坐标变量的方法。另外实例化了一个StampedLock对象用来保证操作的原子性。
 * <p>
 * 首先分析下move方法，该方法的作用是使用参数的增量值，改变当前point坐标的位置。
 * 代码先获取到了写锁，然后对point坐标进行修改，而后释放锁。
 * 该锁是排它锁，这保证了其他线程调用move函数时会被阻塞，
 * 也保证了其他线程不能获取读锁，来读取坐标的值，
 * 直到当前线程显式释放了写锁，保证了对变量x,y操作的原子性和数据一致性。
 * <p>
 * 然后看distanceFromOrigin方法，该方法的作用是计算当前位置到原点（坐标为0,0)的距离代码1首先尝试获取乐观读锁，
 * 如果当前没有其他线程获取到了写锁，那么代码（1）会返回一个非0的stamp用来表示版本信息，
 * 代码（2）复制坐标变量到本地方法栈里面。
 * <p>代码3检查在代码1中获取到的stamp值是否还有效，
 * 之所以还要在此校验是因为代码（1）获取读锁时并没有通过CAS操作修改锁的状态，
 * 而是简单地通过与或操作返回了一个版本信息，在这里校验是看在获取版本信息后到现在的时间段里面是否有其他线程持有了写锁，
 * 如果有则之前获取的版本信息就无效了。
 * <p>如果校验成功则执行代码7使用本地方法栈里面的值进行计算然后返回。
 * 需要注意的是，在代码3中校验成功后，在代码(7）计算期间，其他线程可能获取到了写锁并且修改了x,y的值，
 * 而当前线程执行代码(7）进行计算时采用的还是修改前的值的副本，
 * 也就是操作的值是之前值的一个副本，一个快照，并不是最新的值。</p>
 * <p>另外还有个问题，代码（2）和代码3能否互换？答案是不能。
 * 假设位置换了，那么首先执行validate，假如validate通过了，要复制x,y值到本地方法栈，
 * 而在复制的过程中很有可能其他线程已经修改了x、y中的一个值，这就造成了数据的不一致。
 * 那么你可能会问，即使不交换代码2和代码3，在复制x,y值到本地方法栈时，
 * 也会存在其他线程修改了x,y中的－个值的情况，这不也会存在问题吗？这个确实会存在，
 * 但是，别忘了复制后还有validate这一关昵，如果这时候有线程修改了x,y中的某一值，
 * 那么肯定是有线程在调用validate前，调用sI.tryOptimisticRead后获取了写锁，
 * 这样进行validate时就会失败。</p>
 * <p> * 现在你应该明白了，这也是乐观读设计的精妙之处，而且也是在使用时容易出问题的地方。
 * 下面继续分析，validate失败后会执行代码（4）获取悲观读锁，如果这时候其他线程持有写锁，
 * 则代码（4）会使当前线程阻塞直到其他线程释放了写锁。如果这时候没有其他线程获取到写锁，
 * 那么当前线程就可以获取到读锁，然后执行代码（5）重新复制新的坐标值到本地方法栈，
 * 再然后就是代码（6）释放了锁。复制时由于加了读锁，所以在复制期间如果有其他线程获取写锁会被阻塞，
 * 这保证了数据的一致性。另外，这里的x,y没有被声明为volatie的，会不会存在内存不可见性问题呢？
 * 答案是不会，因为加锁的语义保证了内存的可见性。</p>
 * <p>最后代码（7）使用方法栈里面的数据计算并返回，同理，这里在计算时使用的数据也可能不是最新的，
 * 其他写线程可能已经修改过原来的x,y值了。</p>
 * <p>最后一个方法moveltOrigin的作用是，
 * 如果当前坐标为原点则移动到指定的位置。代码(1）获取悲观读锁，
 * 保证其他线程不能获取写锁来修改x,y值。然后码（2）判断，
 * 如果当前点在原点则更新坐标，代码(3)尝试升级读锁为写锁。
 * 这里升级不一定成功，因多个线程都可以同时获取悲观读锁，
 * 当多个线程都执行到代码(3）时只有一个可以升级成功，
 * 升级成功则返非0的stamp，否则返回0。这里假设当前线程升级成功，
 * 然后执行代码（4）更新stamp值和坐标值，之后退出循环。
 * 如果升级失败则执行代码（5）首先释放读锁，然后申请写锁，
 * 获取到写锁后再循环重新设置坐标值。最后代码（6）释放锁。</p>
 * */
public class Point {
		//成员变量
	  private double x,y;
	  //锁实例
	  private StampedLock sl=new StampedLock();
	  
	  //排他锁--写锁(wirteLock)
	  void move(double deltaX,double deltaY) {
		  long stamp = sl.writeLock();
		  try {
			  x+=deltaX;
			  y+=deltaY;
		} finally {
			sl.unlock(stamp);
		}
	  }
	   //乐观读锁 OptimisticRead
	  double distanceFromOrigin() {
		  //1.尝试获取乐观锁
		  long stamp = sl.tryOptimisticRead();
		  //2.将全部变量复制到方法体栈内
		  double countX=x,countY=y;
		  //3.检查在1处获得了读锁戳记后，锁有没被其他写线程排它性给抢占
		  if(!sl.validate(stamp)) {
			  //4.如果被抢占则获取一个共享读锁(悲观锁获取)
			 stamp = sl.readLock();
			 try {
				//5.将全部变量复制到方法体栈内
				 countX=x;
				 countY=y;
			} finally {
				//6.释放共享读锁
				sl.unlockRead(stamp);
			}
		  }
		  //7.
		return Math.sqrt(countX*countX+countY*countY);
	  }
	  //使用悲观锁获取读锁，并尝试转换为写锁
   void moveIforigin(double newX,double newY) {
	   //1.这里可以使用乐观读锁替换
	   long stamp = sl.readLock();
	   
	   try {
		   //2.如果当前点在原点则移动
		   while (x==0.0 && y==0.0) {
			//3.尝试将获取的读锁升级为写锁
			   long ws = sl.tryConvertToWriteLock(stamp);
			   //4.如果成功则更新戳记并设置坐标值，然后退出循环
			   if(ws !=0L) {
				 stamp=ws;
				 x=newX;
				 y=newY;
				 break;
			   }else {
				   //5.读锁升级写锁失败则释放锁，显式获取独占写锁，然后循环重试。
				   sl.unlockRead(stamp);
				   stamp=sl.writeLock();
				
			}
		}
		
		} finally {
			// 6 释放锁
			sl.unlockRead(stamp);
		}
	   
   }
  
  
  
  
  
  
  
  
  
  
  
  
  /*<p>使用乐观读锁还是很容易犯错误的，必须要小心，且必须要保证如下的使用顺序。</p>*/
  public static void main(String[] args) {
	  StampedLock lock=new StampedLock();
	  long stamp = lock.tryOptimisticRead(); //非阻塞读获取版本信息
	  //复制变量到线程本地堆栈
	  copyVarable2ThreadMemeory();
	  //校验
	  if(!lock.validate(stamp)) {
	  //获取读锁
		 stamp = lock.readLock();
	  try {
			//复制变量到线程本地堆栈
			 copyVarable2ThreadMemeory();
		} finally {
			//释放悲观锁
			lock.unlock(stamp);
		}
	  }
	  useThreadMemeoryVarable();
	  //使用线程本地的堆栈里面的数据进行操作
}
private static void copyVarable2ThreadMemeory() {
	// TODO Auto-generated method stub
	
}
private static void useThreadMemeoryVarable() {
	// TODO Auto-generated method stub
	
}
  
  
  
  
  
  
  
  
  
  
  

}
