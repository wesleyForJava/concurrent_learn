package com.concuurent.learn.tomcat;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint.PollerEvent;

/**
 * Poller线程的作用是从事件队列里面获取事件并进行处理。
 * 首先我们从时序图来全局了解下Poller线程的处理逻辑
 * @author Wesley
 * 2019年7月10日下午1:46:55
 * @Version 1.0
 */
public class Poller implements Runnable {

    private Selector selector;
//    private final SynchronizedQueue<PollerEvent> events =
//            new SynchronizedQueue<>();

    private volatile boolean close = false;
    private long nextExpiration = 0;//optimize expiration handling

    private AtomicLong wakeupCounter = new AtomicLong(0);

    private volatile int keyCount = 0;

    public Poller() throws IOException {
        this.selector = Selector.open();
    }

    public int getKeyCount() { return keyCount; }

    public Selector getSelector() { return selector;}

    /**
     * Destroy the poller.
     */
    protected void destroy() {
        // Wait for polltime before doing anything, so that the poller threads
        // exit, otherwise parallel closure of sockets which are still
        // in the poller can cause problems
        close = true;
        selector.wakeup();
    }

    private void addEvent(PollerEvent event) {
//        events.offer(event);
        if ( wakeupCounter.incrementAndGet() == 0 ) selector.wakeup();
    }

    /**
     * Add specified socket and associated pool to the poller. The socket will
     * be added to a temporary array, and polled first after a maximum amount
     * of time equal to pollTime (in most cases, latency will be much lower,
     * however).
     *
     * @param socket to add to the poller
     * @param interestOps Operations for which to register this socket with
     *                    the Poller
     */
    public void add(final NioChannel socket, final int interestOps) {
//        PollerEvent r = eventCache.pop();
//        if ( r==null) r = new PollerEvent(socket,null,interestOps);
//        else r.reset(socket,null,interestOps);
//        addEvent(r);
//        if (close) {
//            NioEndpoint.NioSocketWrapper ka = (NioEndpoint.NioSocketWrapper)socket.getAttachment();
//            processSocket(ka, SocketEvent.STOP, false);
//        }
    }

    /**
     * Processes events in the event queue of the Poller.
     *
     * @return <code>true</code> if some events were processed,
     *   <code>false</code> if queue was empty
     */
    public boolean events() {
        boolean result = false;
        
        //从队列获取任务并执行
        PollerEvent pe = null;
//        for (int i = 0, size = events.size(); i < size && (pe = events.poll()) != null; i++ ) {
//            result = true;
//            try {
//                pe.run();
//                pe.reset();
//                if (running && !paused) {
//                    eventCache.push(pe);
//                }
//            } catch ( Throwable x ) {
//                log.error("",x);
//            }
//        }

        return result;
    }

    /**
     * Registers a newly created socket with the poller.
     *
     * @param socket    The newly created socket
     */
    public void register(final NioChannel socket) {
//        socket.setPoller(this);
//        NioSocketWrapper ka = new NioSocketWrapper(socket, NioEndpoint.this);
//        socket.setSocketWrapper(ka);
//        ka.setPoller(this);
//        ka.setReadTimeout(getSocketProperties().getSoTimeout());
//        ka.setWriteTimeout(getSocketProperties().getSoTimeout());
//        ka.setKeepAliveLeft(NioEndpoint.this.getMaxKeepAliveRequests());
//        ka.setSecure(isSSLEnabled());
//        ka.setReadTimeout(getConnectionTimeout());
//        ka.setWriteTimeout(getConnectionTimeout());
//        PollerEvent r = eventCache.pop();
//        ka.interestOps(SelectionKey.OP_READ);//this is what OP_REGISTER turns into.
//        if ( r==null) r = new PollerEvent(socket,ka,OP_REGISTER);
//        else r.reset(socket,ka,OP_REGISTER);
//        addEvent(r);
    }

//    public NioSocketWrapper cancelledKey(SelectionKey key) {
//        NioSocketWrapper ka = null;
//        try {
//            if ( key == null ) return null;//nothing to do
//            ka = (NioSocketWrapper) key.attach(null);
//            if (ka != null) {
//                // If attachment is non-null then there may be a current
//                // connection with an associated processor.
//                getHandler().release(ka);
//            }
//            if (key.isValid()) key.cancel();
//            // If it is available, close the NioChannel first which should
//            // in turn close the underlying SocketChannel. The NioChannel
//            // needs to be closed first, if available, to ensure that TLS
//            // connections are shut down cleanly.
//            if (ka != null) {
//                try {
//                    ka.getSocket().close(true);
//                } catch (Exception e){
//                    if (log.isDebugEnabled()) {
//                        log.debug(sm.getString(
//                                "endpoint.debug.socketCloseFail"), e);
//                    }
//                }
//            }
//            // The SocketChannel is also available via the SelectionKey. If
//            // it hasn't been closed in the block above, close it now.
//            if (key.channel().isOpen()) {
//                try {
//                    key.channel().close();
//                } catch (Exception e) {
//                    if (log.isDebugEnabled()) {
//                        log.debug(sm.getString(
//                                "endpoint.debug.channelCloseFail"), e);
//                    }
//                }
//            }
//            try {
//                if (ka != null && ka.getSendfileData() != null
//                        && ka.getSendfileData().fchannel != null
//                        && ka.getSendfileData().fchannel.isOpen()) {
//                    ka.getSendfileData().fchannel.close();
//                }
//            } catch (Exception ignore) {
//            }
//            if (ka != null) {
//                countDownConnection();
//                ka.closed = true;
//            }
//        } catch (Throwable e) {
//            ExceptionUtils.handleThrowable(e);
//            if (log.isDebugEnabled()) log.error("",e);
//        }
//        return ka;
//    }

    /**
     * The background thread that adds sockets to the Poller, checks the
     * poller for triggered events and hands the associated socket off to an
     * appropriate processor as events occur.
     */
    @Override
    public void run() {
        // Loop until destroy() is called
        while (true) {

            boolean hasEvents = false;

            try {
                if (!close) {
                	//6.从事件队列获取事件 详见方法内
                    hasEvents = events();
                    if (wakeupCounter.getAndSet(-1) > 0) {
                        //if we are here, means we have other stuff to do
                        //do a non blocking select
                        keyCount = selector.selectNow();
                    } else {
//                        keyCount = selector.select(selectorTimeout);
                    }
                    wakeupCounter.set(0);
                }
                if (close) {
                    events();
//                    timeout(0, false);
                    try {
                        selector.close();
                    } catch (IOException ioe) {
//                        log.error(sm.getString("endpoint.nio.selectorCloseFail"), ioe);
                    }
                    break;
                }
            } catch (Throwable x) {
                ExceptionUtils.handleThrowable(x);
//                log.error("",x);
                continue;
            }
            //either we timed out or we woke up, process events first
            if ( keyCount == 0 ) hasEvents = (hasEvents | events());

            Iterator<SelectionKey> iterator =
                keyCount > 0 ? selector.selectedKeys().iterator() : null;
            // Walk through the collection of ready keys and dispatch
            // any active event.
            //7.遍历所有注册的Channel,并对感兴趣的事件进行处理。                
            while (iterator != null && iterator.hasNext()) {
                SelectionKey sk = iterator.next();
//                NioSocketWrapper attachment = (NioSocketWrapper)sk.attachment();
                // Attachment may be null if another thread has called
                // cancelledKey()
//                if (attachment == null) {
//                    iterator.remove();
//                } else {
//                    iterator.remove();
//                 //8.具体调用SocketProcessor进行处理  详见 processSocket方法
//                    processKey(sk, attachment);
//                }
            }//while

            //process timeouts
//            timeout(keyCount,hasEvents);
        }//while

//        getStopLatch().countDown();
    }
}

/**
 * Process the given SocketWrapper with the given status. Used to trigger
 * processing as if the Poller (for those endpoints that have one)
 * selected the socket.
 *
 * @param socketWrapper The socket wrapper to process
 * @param event         The socket event to be processed
 * @param dispatch      Should the processing be performed on a new
 *                          container thread
 *
 * @return if processing was triggered successfully
 */
//public boolean processSocket(SocketWrapperBase<S> socketWrapper,
//        SocketEvent event, boolean dispatch) {
//    try {
//        if (socketWrapper == null) {
//            return false;
//        }
//        SocketProcessorBase<S> sc = processorCache.pop();
//        if (sc == null) {
//            sc = createSocketProcessor(socketWrapper, event);
//        } else {
//            sc.reset(socketWrapper, event);
//        }
//        Executor executor = getExecutor();
//        if (dispatch && executor != null) {
//            executor.execute(sc);
//        } else {
//            sc.run();
//        }
//    } catch (RejectedExecutionException ree) {
//        getLog().warn(sm.getString("endpoint.executor.fail", socketWrapper) , ree);
//        return false;
//    } catch (Throwable t) {
//        ExceptionUtils.handleThrowable(t);
//        // This means we got an OOM or similar creating a thread, or that
//        // the pool and its queue are full
//        getLog().error(sm.getString("endpoint.process.fail"), t);
//        return false;
//    }
//    return true;
//}

//    protected void processKey(SelectionKey sk, NioSocketWrapper attachment) {
//        try {
//            if ( close ) {
//                cancelledKey(sk);
//            } else if ( sk.isValid() && attachment != null ) {
//                if (sk.isReadable() || sk.isWritable() ) {
//                    if ( attachment.getSendfileData() != null ) {
//                        processSendfile(sk,attachment, false);
//                    } else {
//                        unreg(sk, attachment, sk.readyOps());
//                        boolean closeSocket = false;
//                        // Read goes before write
//                        if (sk.isReadable()) {
//                            if (!processSocket(attachment, SocketEvent.OPEN_READ, true)) {
//                                closeSocket = true;
//                            }
//                        }
//                        if (!closeSocket && sk.isWritable()) {
//                            if (!processSocket(attachment, SocketEvent.OPEN_WRITE, true)) {
//                                closeSocket = true;
//                            }
//                        }
//                        if (closeSocket) {
//                            cancelledKey(sk);
//                        }
//                    }
//                }
//            } else {
//                //invalid key
//                cancelledKey(sk);
//            }
//        } catch ( CancelledKeyException ckx ) {
//            cancelledKey(sk);
//        } catch (Throwable t) {
//            ExceptionUtils.handleThrowable(t);
//            log.error("",t);
//        }
//    }

//    public SendfileState processSendfile(SelectionKey sk, NioSocketWrapper socketWrapper,
//            boolean calledByProcessor) {
//        NioChannel sc = null;
//        try {
//            unreg(sk, socketWrapper, sk.readyOps());
//            SendfileData sd = socketWrapper.getSendfileData();
//
//            if (log.isTraceEnabled()) {
//                log.trace("Processing send file for: " + sd.fileName);
//            }
//
//            if (sd.fchannel == null) {
//                // Setup the file channel
//                File f = new File(sd.fileName);
//                @SuppressWarnings("resource") // Closed when channel is closed
//                FileInputStream fis = new FileInputStream(f);
//                sd.fchannel = fis.getChannel();
//            }
//
//            // Configure output channel
//            sc = socketWrapper.getSocket();
//            // TLS/SSL channel is slightly different
//            WritableByteChannel wc = ((sc instanceof SecureNioChannel)?sc:sc.getIOChannel());
//
//            // We still have data in the buffer
//            if (sc.getOutboundRemaining()>0) {
//                if (sc.flushOutbound()) {
//                    socketWrapper.updateLastWrite();
//                }
//            } else {
//                long written = sd.fchannel.transferTo(sd.pos,sd.length,wc);
//                if (written > 0) {
//                    sd.pos += written;
//                    sd.length -= written;
//                    socketWrapper.updateLastWrite();
//                } else {
//                    // Unusual not to be able to transfer any bytes
//                    // Check the length was set correctly
//                    if (sd.fchannel.size() <= sd.pos) {
//                        throw new IOException("Sendfile configured to " +
//                                "send more data than was available");
//                    }
//                }
//            }
//            if (sd.length <= 0 && sc.getOutboundRemaining()<=0) {
//                if (log.isDebugEnabled()) {
//                    log.debug("Send file complete for: "+sd.fileName);
//                }
//                socketWrapper.setSendfileData(null);
//                try {
//                    sd.fchannel.close();
//                } catch (Exception ignore) {
//                }
//                // For calls from outside the Poller, the caller is
//                // responsible for registering the socket for the
//                // appropriate event(s) if sendfile completes.
//                if (!calledByProcessor) {
//                    switch (sd.keepAliveState) {
//                    case NONE: {
//                        if (log.isDebugEnabled()) {
//                            log.debug("Send file connection is being closed");
//                        }
//                        close(sc, sk);
//                        break;
//                    }
//                    case PIPELINED: {
//                        if (log.isDebugEnabled()) {
//                            log.debug("Connection is keep alive, processing pipe-lined data");
//                        }
//                        if (!processSocket(socketWrapper, SocketEvent.OPEN_READ, true)) {
//                            close(sc, sk);
//                        }
//                        break;
//                    }
//                    case OPEN: {
//                        if (log.isDebugEnabled()) {
//                            log.debug("Connection is keep alive, registering back for OP_READ");
//                        }
//                        reg(sk,socketWrapper,SelectionKey.OP_READ);
//                        break;
//                    }
//                    }
//                }
//                return SendfileState.DONE;
//            } else {
//                if (log.isDebugEnabled()) {
//                    log.debug("OP_WRITE for sendfile: " + sd.fileName);
//                }
//                if (calledByProcessor) {
//                    add(socketWrapper.getSocket(),SelectionKey.OP_WRITE);
//                } else {
//                    reg(sk,socketWrapper,SelectionKey.OP_WRITE);
//                }
//                return SendfileState.PENDING;
//            }
//        } catch (IOException x) {
//            if (log.isDebugEnabled()) log.debug("Unable to complete sendfile request:", x);
//            if (!calledByProcessor && sc != null) {
//                close(sc, sk);
//            }
//            return SendfileState.ERROR;
//        } catch (Throwable t) {
//            log.error("", t);
//            if (!calledByProcessor && sc != null) {
//                close(sc, sk);
//            }
//            return SendfileState.ERROR;
//        }
//    }

//    protected void unreg(SelectionKey sk, NioSocketWrapper attachment, int readyOps) {
//        //this is a must, so that we don't have multiple threads messing with the socket
//        reg(sk,attachment,sk.interestOps()& (~readyOps));
//    }

//    protected void reg(SelectionKey sk, NioSocketWrapper attachment, int intops) {
//        sk.interestOps(intops);
//        attachment.interestOps(intops);
//    }

//    protected void timeout(int keyCount, boolean hasEvents) {
//        long now = System.currentTimeMillis();
//        // This method is called on every loop of the Poller. Don't process
//        // timeouts on every loop of the Poller since that would create too
//        // much load and timeouts can afford to wait a few seconds.
//        // However, do process timeouts if any of the following are true:
//        // - the selector simply timed out (suggests there isn't much load)
//        // - the nextExpiration time has passed
//        // - the server socket is being closed
//        if (nextExpiration > 0 && (keyCount > 0 || hasEvents) && (now < nextExpiration) && !close) {
//            return;
//        }
//        //timeout
//        int keycount = 0;
//        try {
//            for (SelectionKey key : selector.keys()) {
//                keycount++;
//                try {
//                    NioSocketWrapper ka = (NioSocketWrapper) key.attachment();
//                    if ( ka == null ) {
//                        cancelledKey(key); //we don't support any keys without attachments
//                    } else if (close) {
//                        key.interestOps(0);
//                        ka.interestOps(0); //avoid duplicate stop calls
//                        processKey(key,ka);
//                    } else if ((ka.interestOps()&SelectionKey.OP_READ) == SelectionKey.OP_READ ||
//                              (ka.interestOps()&SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
//                        boolean isTimedOut = false;
//                        // Check for read timeout
//                        if ((ka.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
//                            long delta = now - ka.getLastRead();
//                            long timeout = ka.getReadTimeout();
//                            isTimedOut = timeout > 0 && delta > timeout;
//                        }
//                        // Check for write timeout
//                        if (!isTimedOut && (ka.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
//                            long delta = now - ka.getLastWrite();
//                            long timeout = ka.getWriteTimeout();
//                            isTimedOut = timeout > 0 && delta > timeout;
//                        }
//                        if (isTimedOut) {
//                            key.interestOps(0);
//                            ka.interestOps(0); //avoid duplicate timeout calls
//                            ka.setError(new SocketTimeoutException());
//                            if (!processSocket(ka, SocketEvent.ERROR, true)) {
//                                cancelledKey(key);
//                            }
//                        }
//                    }
//                }catch ( CancelledKeyException ckx ) {
//                    cancelledKey(key);
//                }
//            }//for
//        } catch (ConcurrentModificationException cme) {
//            // See https://bz.apache.org/bugzilla/show_bug.cgi?id=57943
//            log.warn(sm.getString("endpoint.nio.timeoutCme"), cme);
//        }
//        long prevExp = nextExpiration; //for logging purposes only
//        nextExpiration = System.currentTimeMillis() +
//                socketProperties.getTimeoutInterval();
//        if (log.isTraceEnabled()) {
//            log.trace("timeout completed: keys processed=" + keycount +
//                    "; now=" + now + "; nextExpiration=" + prevExp +
//                    "; keyCount=" + keyCount + "; hasEvents=" + hasEvents +
//                    "; eval=" + ((now < prevExp) && (keyCount>0 || hasEvents) && (!close) ));
//        }
//
//    }
//}

