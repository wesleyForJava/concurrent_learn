package com.concuurent.learn.tomcat;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.WeakHashMap;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.SSLSessionManager;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SSLSupport.CipherData;
//import org.apache.tomcat.util.net.jsse.JSSESupport.1;
//import org.apache.tomcat.util.net.jsse.JSSESupport.Listener;
import org.apache.tomcat.util.res.StringManager;

class JSSESupport implements SSLSupport, SSLSessionManager {
	private static final Log log = LogFactory.getLog(JSSESupport.class);
	private static final StringManager sm = StringManager.getManager("org.apache.tomcat.util.net.jsse.res");
	private static final Map<SSLSession, Integer> keySizeCache = new WeakHashMap();
	protected SSLSocket ssl;
	protected SSLSession session;
//	Listener listener = new Listener((1)null);

	JSSESupport(SSLSocket sock) {
		this.ssl = sock;
		this.session = sock.getSession();
//		sock.addHandshakeCompletedListener(this.listener);
	}

	JSSESupport(SSLSession session) {
		this.session = session;
	}

	public String getCipherSuite() throws IOException {
		return this.session == null ? null : this.session.getCipherSuite();
	}

	public Object[] getPeerCertificateChain() throws IOException {
		return this.getPeerCertificateChain(false);
	}

	protected X509Certificate[] getX509Certificates(SSLSession session) {
		Certificate[] certs = null;

		try {
			certs = session.getPeerCertificates();
		} catch (Throwable var9) {
			log.debug(sm.getString("jsseSupport.clientCertError"), var9);
			return null;
		}

		if (certs == null) {
			return null;
		} else {
			X509Certificate[] x509Certs = new X509Certificate[certs.length];

			for (int i = 0; i < certs.length; ++i) {
				if (certs[i] instanceof X509Certificate) {
					x509Certs[i] = (X509Certificate) certs[i];
				} else {
					try {
						byte[] buffer = certs[i].getEncoded();
						CertificateFactory cf = CertificateFactory.getInstance("X.509");
						ByteArrayInputStream stream = new ByteArrayInputStream(buffer);
						x509Certs[i] = (X509Certificate) cf.generateCertificate(stream);
					} catch (Exception var8) {
						log.info(sm.getString("jseeSupport.certTranslationError", new Object[]{certs[i]}), var8);
						return null;
					}
				}

				if (log.isTraceEnabled()) {
					log.trace("Cert #" + i + " = " + x509Certs[i]);
				}
			}

			if (x509Certs.length < 1) {
				return null;
			} else {
				return x509Certs;
			}
		}
	}

	public Object[] getPeerCertificateChain(boolean force) throws IOException {
		if (this.session == null) {
			return null;
		} else {
			javax.security.cert.X509Certificate[] jsseCerts = null;

			try {
				jsseCerts = this.session.getPeerCertificateChain();
			} catch (Exception var4) {
				;
			}

			if (jsseCerts == null) {
				jsseCerts = new javax.security.cert.X509Certificate[0];
			}

			if (jsseCerts.length <= 0 && force && this.ssl != null) {
				this.session.invalidate();
				this.handShake();
				this.session = this.ssl.getSession();
			}

			return this.getX509Certificates(this.session);
		}
	}

	protected void handShake() throws IOException {
		if (this.ssl.getWantClientAuth()) {
			log.debug(sm.getString("jsseSupport.noCertWant"));
		} else {
			this.ssl.setNeedClientAuth(true);
		}

		if (this.ssl.getEnabledCipherSuites().length == 0) {
			log.warn(sm.getString("jsseSupport.serverRenegDisabled"));
			this.session.invalidate();
			this.ssl.close();
		} else {
			InputStream in = this.ssl.getInputStream();
			int oldTimeout = this.ssl.getSoTimeout();
			this.ssl.setSoTimeout(1000);
			byte[] b = new byte[1];
//			this.listener.reset();
			this.ssl.startHandshake();
			int maxTries = 60;

			for (int i = 0; i < maxTries; ++i) {
				if (log.isTraceEnabled()) {
					log.trace("Reading for try #" + i);
				}

				try {
					int read = in.read(b);
					if (read > 0) {
						throw new SSLException(sm.getString("jsseSupport.unexpectedData"));
					}
				} catch (SSLException var7) {
					log.info(sm.getString("jsseSupport.clientCertError"), var7);
					throw var7;
				} catch (IOException var8) {
					;
				}

//				if (this.listener.completed) {
//					break;
//				}
			}

			this.ssl.setSoTimeout(oldTimeout);
//			if (!this.listener.completed) {
//				throw new SocketException("SSL Cert handshake timeout");
//			}
		}
	}

	public Integer getKeySize() throws IOException {
		CipherData[] c_aux = ciphers;
		if (this.session == null) {
			return null;
		} else {
			Integer keySize = null;
			Map var3 = keySizeCache;
			synchronized (keySizeCache) {
				keySize = (Integer) keySizeCache.get(this.session);
			}

			if (keySize == null) {
				int size = 0;
				String cipherSuite = this.session.getCipherSuite();

				for (int i = 0; i < c_aux.length; ++i) {
					if (cipherSuite.indexOf(c_aux[i].phrase) >= 0) {
						size = c_aux[i].keySize;
						break;
					}
				}

				keySize = size;
				Map var10 = keySizeCache;
				synchronized (keySizeCache) {
					keySizeCache.put(this.session, keySize);
				}
			}

			return keySize;
		}
	}

	public String getSessionId() throws IOException {
		if (this.session == null) {
			return null;
		} else {
			byte[] ssl_session = this.session.getId();
			if (ssl_session == null) {
				return null;
			} else {
				StringBuilder buf = new StringBuilder();

				for (int x = 0; x < ssl_session.length; ++x) {
					String digit = Integer.toHexString(ssl_session[x]);
					if (digit.length() < 2) {
						buf.append('0');
					}

					if (digit.length() > 2) {
						digit = digit.substring(digit.length() - 2);
					}

					buf.append(digit);
				}

				return buf.toString();
			}
		}
	}

	public void invalidateSession() {
		this.session.invalidate();
	}

	public String getProtocol() throws IOException {
		return this.session == null ? null : this.session.getProtocol();
	}
}