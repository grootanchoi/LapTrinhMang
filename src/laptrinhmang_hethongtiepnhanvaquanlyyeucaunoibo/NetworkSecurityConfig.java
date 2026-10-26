package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.KeyManagerFactory;

/** Optional TLS configuration from environment variables; plaintext is for local demos only. */
public final class NetworkSecurityConfig {
    private static final String TLS_ENABLED = "WORKDESK_TLS";
    private static final String KEYSTORE_PATH = "WORKDESK_KEYSTORE";
    private static final String KEYSTORE_PASSWORD = "WORKDESK_KEYSTORE_PASSWORD";
    private static final String TRUSTSTORE_PATH = "WORKDESK_TRUSTSTORE";
    private static final String TRUSTSTORE_PASSWORD = "WORKDESK_TRUSTSTORE_PASSWORD";

    private NetworkSecurityConfig() {}

    public static boolean isTlsEnabled() {
        return Boolean.parseBoolean(System.getenv(TLS_ENABLED));
    }

    public static ServerSocket createServerSocket(int port) throws IOException {
        if (!isTlsEnabled()) return new ServerSocket(port);
        try {
            SSLServerSocketFactory factory = createServerContext().getServerSocketFactory();
            SSLServerSocket socket = (SSLServerSocket) factory.createServerSocket(port);
            socket.setNeedClientAuth(false);
            return socket;
        } catch (GeneralSecurityException e) {
            throw new IOException("Không thể khởi tạo TLS Server; kiểm tra cấu hình keystore.", e);
        }
    }

    public static Socket createClientSocket(String host, int port, int timeoutMillis)
            throws IOException {
        if (!isTlsEnabled()) {
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMillis);
            return socket;
        }
        try {
            SSLSocketFactory factory = createClientContext().getSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket();
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMillis);
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            return socket;
        } catch (GeneralSecurityException e) {
            throw new IOException("Không thể khởi tạo TLS Client; kiểm tra cấu hình truststore.", e);
        }
    }

    private static SSLContext createServerContext() throws GeneralSecurityException, IOException {
        String path = requiredEnv(KEYSTORE_PATH);
        char[] password = requiredEnv(KEYSTORE_PASSWORD).toCharArray();
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream input = new FileInputStream(path)) {
            keyStore.load(input, password);
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        java.util.Arrays.fill(password, '\0');
        return context;
    }

    private static SSLContext createClientContext() throws GeneralSecurityException, IOException {
        String path = requiredEnv(TRUSTSTORE_PATH);
        char[] password = requiredEnv(TRUSTSTORE_PASSWORD).toCharArray();
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream input = new FileInputStream(path)) {
            trustStore.load(input, password);
        }
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers.getTrustManagers(), null);
        java.util.Arrays.fill(password, '\0');
        return context;
    }

    private static String requiredEnv(String key) throws IOException {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IOException("Thiếu biến môi trường " + key + ".");
        return value;
    }
}
