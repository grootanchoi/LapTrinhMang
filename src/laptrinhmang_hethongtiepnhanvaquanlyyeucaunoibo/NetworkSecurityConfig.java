package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
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
    private static final String ALLOWED_CIDRS = "WORKDESK_ALLOWED_CIDRS";
    private static final String DEFAULT_ALLOWED_CIDRS = "127.0.0.0/8,::1/128";

    private NetworkSecurityConfig() {}

    public static boolean isTlsEnabled() {
        return Boolean.parseBoolean(System.getenv(TLS_ENABLED));
    }

    public static List<Cidr> getAllowedNetworks() throws IOException {
        String configuredCidrs = System.getenv(ALLOWED_CIDRS);
        if (configuredCidrs == null || configuredCidrs.isBlank()) {
            configuredCidrs = DEFAULT_ALLOWED_CIDRS;
        }
        try {
            return parseCidrs(configuredCidrs);
        } catch (IllegalArgumentException e) {
            throw new IOException("WORKDESK_ALLOWED_CIDRS không hợp lệ: " + e.getMessage(), e);
        }
    }

    static List<Cidr> parseCidrs(String cidrs) {
        if (cidrs == null || cidrs.isBlank()) throw new IllegalArgumentException("Danh sách CIDR đang trống.");
        List<Cidr> networks = new ArrayList<>();
        for (String value : cidrs.split(",")) {
            String cidr = value.trim();
            int slash = cidr.indexOf('/');
            if (slash < 1 || slash != cidr.lastIndexOf('/')) {
                throw new IllegalArgumentException("CIDR không hợp lệ: " + cidr);
            }
            String addressText = cidr.substring(0, slash);
            if (!addressText.matches("[0-9.]+") && !addressText.contains(":")) {
                throw new IllegalArgumentException("Địa chỉ phải là IPv4/IPv6 dạng số: " + addressText);
            }
            try {
                byte[] address = normalizeAddress(InetAddress.getByName(addressText).getAddress());
                int prefix = Integer.parseInt(cidr.substring(slash + 1));
                if (prefix < 0 || prefix > address.length * 8) {
                    throw new IllegalArgumentException("Độ dài prefix không hợp lệ: " + cidr);
                }
                networks.add(new Cidr(address, prefix));
            } catch (IOException | NumberFormatException e) {
                throw new IllegalArgumentException("CIDR không hợp lệ: " + cidr, e);
            }
        }
        return List.copyOf(networks);
    }

    static boolean isAddressAllowed(InetAddress address, List<Cidr> allowedNetworks) {
        byte[] candidate = normalizeAddress(address.getAddress());
        return allowedNetworks.stream().anyMatch(network -> network.matches(candidate));
    }

    private static byte[] normalizeAddress(byte[] address) {
        if (address.length == 16) {
            for (int i = 0; i < 10; i++) {
                if (address[i] != 0) return address;
            }
            if (address[10] == (byte) 0xff && address[11] == (byte) 0xff) {
                return java.util.Arrays.copyOfRange(address, 12, 16);
            }
        }
        return address;
    }

    static final class Cidr {
        private final byte[] address;
        private final int prefix;

        private Cidr(byte[] address, int prefix) {
            this.address = address;
            this.prefix = prefix;
        }

        private boolean matches(byte[] candidate) {
            if (address.length != candidate.length) return false;
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != candidate[i]) return false;
            }
            if (remainingBits == 0) return true;
            int mask = 0xff << (8 - remainingBits);
            return (address[fullBytes] & mask) == (candidate[fullBytes] & mask);
        }
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
