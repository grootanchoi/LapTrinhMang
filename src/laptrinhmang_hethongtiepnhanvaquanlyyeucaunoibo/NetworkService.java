package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Owns the client socket, packet dispatch, and heartbeat lifecycle. */
public final class NetworkService implements AutoCloseable {
    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    private final Consumer<Protocol.Packet> unsolicitedPacketHandler;
    private final Consumer<Exception> disconnectHandler;
    private final Consumer<Models.User> reconnectedHandler;
    private final ConcurrentHashMap<String, CompletableFuture<Protocol.Packet>> pendingRequests =
            new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "workdesk-heartbeat");
                thread.setDaemon(true);
                return thread;
            });
    private final AtomicLong connectionGeneration = new AtomicLong();
    private final AtomicBoolean reconnecting = new AtomicBoolean();
    private volatile Socket socket;
    private volatile ObjectOutputStream output;
    private volatile boolean closed;
    private volatile boolean connected;
    private volatile long lastHeartbeatRttMillis = -1;
    private volatile long disconnectNotifiedGeneration = -1;
    private volatile String reconnectHost;
    private volatile int reconnectPort;
    private volatile String reconnectUsername;
    private volatile char[] reconnectPassword;
    private volatile Thread reconnectThread;

    public NetworkService(Consumer<Protocol.Packet> unsolicitedPacketHandler,
                         Consumer<Exception> disconnectHandler,
                         Consumer<Models.User> reconnectedHandler) {
        this.unsolicitedPacketHandler = unsolicitedPacketHandler;
        this.disconnectHandler = disconnectHandler;
        this.reconnectedHandler = reconnectedHandler;
        heartbeatExecutor.scheduleWithFixedDelay(this::sendHeartbeat, 15, 15, TimeUnit.SECONDS);
    }

    public synchronized void setReconnectCredentials(String host, int port, String username, String password) {
        clearReconnectCredentials();
        reconnectHost = host;
        reconnectPort = port;
        reconnectUsername = username;
        reconnectPassword = password.toCharArray();
    }

    public void connect(Socket newSocket, ObjectInputStream input, ObjectOutputStream output)
            throws IOException {
        if (closed) throw new IOException("NetworkService đã được đóng.");
        if (newSocket == null || input == null || output == null) {
            throw new IOException("Kết nối Socket chưa hoàn chỉnh.");
        }

        long generation = connectionGeneration.incrementAndGet();
        Socket previousSocket = socket;
        socket = newSocket;
        this.output = output;
        connected = true;
        disconnectNotifiedGeneration = -1;
        failPendingRequests(new IOException("Kết nối đã được thay thế."));
        if (previousSocket != null && previousSocket != newSocket && !previousSocket.isClosed()) {
            previousSocket.close();
        }

        Thread reader = new Thread(() -> readPackets(generation, newSocket, input),
                "workdesk-network-reader-" + generation);
        reader.setDaemon(true);
        reader.start();
    }

    public Protocol.Packet request(Protocol.Packet packet) throws Exception {
        if (packet == null || packet.command == null || packet.command.isBlank()) {
            throw new IllegalArgumentException("Gói tin hoặc lệnh không hợp lệ.");
        }
        String correlationId = UUID.randomUUID().toString();
        packet.correlationId = correlationId;
        CompletableFuture<Protocol.Packet> responseFuture = new CompletableFuture<>();
        pendingRequests.put(correlationId, responseFuture);

        try {
            writePacket(packet);
            long startedAt = System.nanoTime();
            Protocol.Packet response = responseFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (Protocol.CMD_PING.equals(packet.command)) {
                lastHeartbeatRttMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            }
            return response;
        } catch (TimeoutException e) {
            throw new SocketTimeoutException("Server không phản hồi lệnh " + packet.command
                    + " trong " + REQUEST_TIMEOUT_SECONDS + " giây.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) throw exception;
            throw new IOException("Không thể nhận phản hồi từ máy chủ.", cause);
        } finally {
            pendingRequests.remove(correlationId, responseFuture);
        }
    }

    public boolean isConnected() {
        Socket currentSocket = socket;
        return connected && !closed && currentSocket != null && !currentSocket.isClosed();
    }

    public boolean isReconnecting() {
        return reconnecting.get();
    }

    public long getLastHeartbeatRttMillis() {
        return lastHeartbeatRttMillis;
    }

    public String getSecurityLabel() {
        Socket currentSocket = socket;
        if (currentSocket instanceof SSLSocket tlsSocket) {
            String protocol = tlsSocket.getSession().getProtocol();
            return "TLS " + protocol + " Encrypted";
        }
        return "Plaintext TCP";
    }

    private void writePacket(Protocol.Packet packet) throws IOException {
        ObjectOutputStream currentOutput = output;
        Socket currentSocket = socket;
        if (closed || currentOutput == null || currentSocket == null || currentSocket.isClosed()) {
            throw new IOException("Chưa kết nối tới máy chủ.");
        }
        synchronized (currentOutput) {
            if (closed || currentSocket.isClosed() || output != currentOutput) {
                throw new IOException("Kết nối tới máy chủ đã bị thay đổi.");
            }
            currentOutput.writeObject(packet);
            currentOutput.flush();
            currentOutput.reset();
        }
    }

    private void readPackets(long generation, Socket sourceSocket, ObjectInputStream input) {
        try {
            while (!closed && generation == connectionGeneration.get() && !sourceSocket.isClosed()) {
                Object received = input.readObject();
                if (!(received instanceof Protocol.Packet packet)) {
                    System.err.println("[Client Network] Bỏ qua đối tượng không hợp lệ từ máy chủ.");
                    continue;
                }

                if (Protocol.CMD_NOTIFICATION.equals(packet.command)) {
                    if (packet.data instanceof Models.NotificationMessage notification
                            && "SESSION_REVOKED".equals(notification.eventType)) {
                        clearReconnectCredentials();
                        reconnecting.set(false);
                        Thread currentReconnectThread = reconnectThread;
                        if (currentReconnectThread != null) currentReconnectThread.interrupt();
                    }
                    try {
                        unsolicitedPacketHandler.accept(packet);
                    } catch (RuntimeException e) {
                        System.err.println("[Client Network] Lỗi xử lý thông báo: " + e.getMessage());
                    }
                    continue;
                }

                CompletableFuture<Protocol.Packet> response = packet.correlationId == null
                        ? null : pendingRequests.remove(packet.correlationId);
                if (response == null) {
                    System.err.println("[Client Network] Bỏ qua phản hồi không có request tương ứng: "
                            + packet.command);
                    continue;
                }
                response.complete(packet);
            }
        } catch (Exception e) {
            if (!closed && generation == connectionGeneration.get()) {
                notifyDisconnected(generation, e);
            }
        }
    }

    private void sendHeartbeat() {
        if (closed || socket == null || socket.isClosed()) return;
        try {
            Protocol.Packet response = request(new Protocol.Packet(Protocol.CMD_PING, "", "", null));
            if (!Protocol.STATUS_SUCCESS.equals(response.status)) {
                throw new IOException("Máy chủ từ chối heartbeat.");
            }
        } catch (Exception e) {
            long generation = connectionGeneration.get();
            if (!closed && generation == connectionGeneration.get()) {
                notifyDisconnected(generation, e);
            }
        }
    }

    private void notifyDisconnected(long generation, Exception cause) {
        if (disconnectNotifiedGeneration == generation) return;
        disconnectNotifiedGeneration = generation;
        connected = false;
        failPendingRequests(new IOException("Mất kết nối tới máy chủ.", cause));
        try {
            disconnectHandler.accept(cause);
        } catch (RuntimeException e) {
            System.err.println("[Client Network] Lỗi xử lý sự kiện mất kết nối: " + e.getMessage());
        }
        beginReconnect();
    }

    private void beginReconnect() {
        if (closed || reconnectPassword == null || !reconnecting.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> {
            long delaySeconds = 1;
            while (!closed && reconnecting.get()) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(delaySeconds));
                    char[] savedPassword;
                    synchronized (this) {
                        if (reconnectPassword == null) break;
                        savedPassword = reconnectPassword.clone();
                    }
                    F01_Authentication.ConnectionResult result;
                    try {
                        result = F01_Authentication.performLogin(reconnectHost, reconnectPort,
                                reconnectUsername, new String(savedPassword));
                    } finally {
                        java.util.Arrays.fill(savedPassword, '\0');
                    }
                    if (!result.isConnected) {
                        System.err.println("[Client Network] Kết nối lại chưa thành công: "
                                + result.statusMessage);
                        delaySeconds = Math.min(delaySeconds * 2, 30);
                        continue;
                    }
                    connect(result.socket, result.in, result.out);
                    reconnecting.set(false);
                    try {
                        reconnectedHandler.accept(result.loggedInUser);
                    } catch (RuntimeException e) {
                        System.err.println("[Client Network] Lỗi callback sau khi kết nối lại: "
                                + e.getMessage());
                    }
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[Client Network] Lỗi khi kết nối lại: " + e.getMessage());
                    delaySeconds = Math.min(delaySeconds * 2, 30);
                }
            }
            reconnecting.set(false);
        }, "workdesk-reconnect");
        thread.setDaemon(true);
        reconnectThread = thread;
        thread.start();
    }

    private synchronized void clearReconnectCredentials() {
        if (reconnectPassword != null) {
            java.util.Arrays.fill(reconnectPassword, '\0');
            reconnectPassword = null;
        }
        reconnectHost = null;
        reconnectUsername = null;
        reconnectPort = 0;
    }

    private void failPendingRequests(Exception cause) {
        pendingRequests.forEach((id, future) -> {
            if (pendingRequests.remove(id, future)) future.completeExceptionally(cause);
        });
    }

    @Override
    public void close() {
        closed = true;
        connected = false;
        reconnecting.set(false);
        clearReconnectCredentials();
        Thread currentReconnectThread = reconnectThread;
        if (currentReconnectThread != null) currentReconnectThread.interrupt();
        connectionGeneration.incrementAndGet();
        heartbeatExecutor.shutdownNow();
        failPendingRequests(new IOException("Kết nối đã được đóng."));
        Socket currentSocket = socket;
        socket = null;
        output = null;
        if (currentSocket != null && !currentSocket.isClosed()) {
            try {
                currentSocket.close();
            } catch (IOException e) {
                System.err.println("[Client Network] Không thể đóng Socket: " + e.getMessage());
            }
        }
    }
}
