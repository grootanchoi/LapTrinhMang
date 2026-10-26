package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Standalone regression tests for F01/F08/F11 and real multi-client TCP behavior. */
public final class NetworkIntegrationTests {
    private static final String TICKET_ID = "YC-2026-0001";

    private NetworkIntegrationTests() {}

    public static void main(String[] args) throws Exception {
        System.setProperty("workdesk.testMode", "true");
        System.setProperty("java.awt.headless", "true");
        System.out.println("Transport mode: " + (NetworkSecurityConfig.isTlsEnabled() ? "TLS" : "TCP"));

        testPasswordHashing();
        testBusinessHoursSla();
        DatabaseManager db = DatabaseManager.getInstance();
        testPasswordPersistence(db);
        ServerMain server = new ServerMain();
        int port = reservePort();
        server.startServer(port);
        List<TestClient> clients = new ArrayList<>();
        try {
            TestClient requester = connect(port, "user02", "user123");
            TestClient technician = connect(port, "tech01", "tech123");
            TestClient admin = connect(port, "admin", "admin123");
            clients.add(requester);
            clients.add(technician);
            clients.add(admin);

            testRoleAuthorization(requester, technician, admin);
            testAdminTicketManagement(db, requester, technician, admin);
            testSlaAlerts(db, server, requester, technician);
            testChatAttachment(requester, technician);
            testChatReadStatus(db, requester, technician);
            testHeartbeat(requester);
            testConcurrentClients(port, requester, technician);
            testDisconnectCleanup(server, clients);

            check(server.getMetrics().getProcessedCommands() >= 15, "server counts processed commands");
            check(server.getMetrics().getErrors() > 0, "server counts rejected commands as errors");
            check(server.getMetrics().getAverageResponseMillis() >= 0, "server records response time");
            testAutomaticReconnect(server, port);
            System.out.println("PASS: all network integration tests");
        } finally {
            for (TestClient client : clients) client.close();
            server.stopServer();
        }
    }

    private static void testPasswordHashing() {
        String encoded = PasswordHasher.hash("correct-horse");
        check(PasswordHasher.isEncoded(encoded), "password is stored as an adaptive hash");
        check(PasswordHasher.verify("correct-horse", encoded), "hashed password verifies");
        check(!PasswordHasher.verify("wrong-password", encoded), "wrong password is rejected");
        check(PasswordHasher.verify("legacy-demo", "legacy-demo"), "legacy plaintext remains verifiable for migration");
        check(!encoded.equals(PasswordHasher.hash("correct-horse")), "each password hash receives a unique salt");
    }

    private static void testBusinessHoursSla() {
        java.time.LocalDateTime fridayDeadline = BusinessHours.addWorkingHours(
                java.time.LocalDateTime.of(2026, 10, 23, 16, 0), 4);
        check(fridayDeadline.equals(java.time.LocalDateTime.of(2026, 10, 26, 11, 0)),
                "business-hour SLA skips weekends");
        check(BusinessHours.workingHoursBetween(
                java.time.LocalDateTime.of(2026, 10, 23, 16, 0),
                java.time.LocalDateTime.of(2026, 10, 26, 11, 0)) == 4.0,
                "business-hour SLA calculates remaining working time");
    }

    private static void testPasswordPersistence(DatabaseManager db) throws Exception {
        String username = "hash-test-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        check(db.addUser(new Models.User(username, "test-password", "Hash Test", "requester", "Đang hoạt động")),
                "new test user is stored");
        Models.User stored = db.getAllUsers().stream()
                .filter(user -> username.equals(user.username)).findFirst().orElseThrow();
        check(PasswordHasher.isEncoded(stored.password), "database never stores new plaintext passwords");
        check(db.authenticate(username, "test-password") != null, "database authenticates hashed password");
        check(db.authenticate(username, "incorrect") == null, "database rejects incorrect password");
    }

    private static void testRoleAuthorization(TestClient requester, TestClient technician,
                                               TestClient admin) throws Exception {
        Protocol.Packet denial = requester.request(new Protocol.Packet(Protocol.CMD_GET_USERS, "", "",
                new F08_UserManagement.AdminUserRequest("user02", "admin")));
        check(Protocol.STATUS_ERROR.equals(denial.status), "requester cannot forge admin role");

        F04_AssignTechnician.AssignRequest assignment =
                new F04_AssignTechnician.AssignRequest(TICKET_ID, "tech02", "admin");
        Protocol.Packet technicianDenial = technician.request(new Protocol.Packet(
                Protocol.CMD_ASSIGN_TECH, "", "", assignment));
        check(Protocol.STATUS_ERROR.equals(technicianDenial.status),
                "technician cannot assign tickets");

        Protocol.Packet adminAssignment = admin.request(new Protocol.Packet(
                Protocol.CMD_ASSIGN_TECH, "", "", assignment));
        check(Protocol.STATUS_SUCCESS.equals(adminAssignment.status),
                "admin can assign tickets to technicians");
        Protocol.Packet restoreAssignment = admin.request(new Protocol.Packet(Protocol.CMD_ASSIGN_TECH, "", "",
                new F04_AssignTechnician.AssignRequest(TICKET_ID, "tech01", "admin")));
        check(Protocol.STATUS_SUCCESS.equals(restoreAssignment.status),
                "admin can restore the assigned technician test fixture");

        F02_CreateTicket.CreateTicketRequest createRequest = new F02_CreateTicket.CreateTicketRequest(
                "Forbidden role create test", "IT", "Thấp", "test", null, "");
        check(Protocol.STATUS_ERROR.equals(technician.request(new Protocol.Packet(
                Protocol.CMD_CREATE_TICKET, "", "", createRequest)).status),
                "technician cannot create tickets");
        check(Protocol.STATUS_ERROR.equals(admin.request(new Protocol.Packet(
                Protocol.CMD_CREATE_TICKET, "", "", createRequest)).status),
                "admin cannot create requester tickets");
    }

    private static void testAdminTicketManagement(DatabaseManager db, TestClient requester,
                                                   TestClient technician, TestClient admin) throws Exception {
        String ticketId = "YC-2026-TEST-ADMIN";
        Models.Ticket fixture = new Models.Ticket(ticketId, "Admin test ticket", "IT", "Thấp",
                "2026-10-26", "Mới", "user02", "Chưa phân công", "test fixture", 0, "");
        check(db.insertTicket(fixture), "admin operation ticket fixture is stored");

        Protocol.Packet denied = requester.request(new Protocol.Packet(Protocol.CMD_UPDATE_TICKET, "", "",
                new AdminTicketManagement.UpdateRequest(ticketId, "forbidden", "IT", "Thấp", "test")));
        check(Protocol.STATUS_ERROR.equals(denied.status), "requester cannot edit tickets");
        Protocol.Packet technicianDenied = technician.request(new Protocol.Packet(Protocol.CMD_UPDATE_TICKET, "", "",
                new AdminTicketManagement.UpdateRequest(ticketId, "forbidden", "IT", "Thấp", "test")));
        check(Protocol.STATUS_ERROR.equals(technicianDenied.status), "technician cannot edit tickets");
        Protocol.Packet update = admin.request(new Protocol.Packet(Protocol.CMD_UPDATE_TICKET, "", "",
                new AdminTicketManagement.UpdateRequest(ticketId, "Edited by admin", "IT", "Cao", "updated")));
        check(Protocol.STATUS_SUCCESS.equals(update.status), "admin can edit ticket fields");
        check("Edited by admin".equals(db.getTicketById(ticketId).title), "admin changes persist");

        Protocol.Packet deniedDelete = requester.request(new Protocol.Packet(Protocol.CMD_DELETE_TICKET, "", "",
                new AdminTicketManagement.DeleteRequest(ticketId)));
        check(Protocol.STATUS_ERROR.equals(deniedDelete.status), "requester cannot delete tickets");
        Protocol.Packet delete = admin.request(new Protocol.Packet(Protocol.CMD_DELETE_TICKET, "", "",
                new AdminTicketManagement.DeleteRequest(ticketId)));
        check(Protocol.STATUS_SUCCESS.equals(delete.status), "admin can soft delete tickets");
        check(db.getTicketById(ticketId) == null, "soft-deleted tickets disappear from active lookup");

        Protocol.Packet audit = admin.request(new Protocol.Packet(Protocol.CMD_GET_AUDIT_LOG, "", "", null));
        check(Protocol.STATUS_SUCCESS.equals(audit.status) && audit.data instanceof List<?>,
                "admin can read audit history");
        check(((List<?>) audit.data).stream().filter(Models.AuditEvent.class::isInstance)
                        .map(Models.AuditEvent.class::cast)
                        .anyMatch(event -> ticketId.equals(event.entityId)
                                && "TICKET_SOFT_DELETED".equals(event.eventType)),
                "audit history records ticket soft deletion");
    }

    private static void testChatAttachment(TestClient requester, TestClient technician) throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p0cAAAAASUVORK5CYII=");
        Protocol.Packet rejected = requester.request(new Protocol.Packet(Protocol.CMD_SEND_TICKET_CHAT, "", "",
                new F11_TicketChat.ChatAttachmentRequest(TICKET_ID, "payload.exe", "", png)));
        check(Protocol.STATUS_ERROR.equals(rejected.status), "chat rejects unsupported attachment types");
        Protocol.Packet sent = requester.request(new Protocol.Packet(Protocol.CMD_SEND_TICKET_CHAT, "", "",
                new F11_TicketChat.ChatAttachmentRequest(TICKET_ID, "sample.png", "integration attachment", png)));
        check(Protocol.STATUS_SUCCESS.equals(sent.status), "authorized user sends an image through chat");
        check(requester.readNotification(TICKET_ID) && technician.readNotification(TICKET_ID),
                "chat attachment is pushed to connected participants");

        Protocol.Packet history = technician.request(new Protocol.Packet(Protocol.CMD_GET_TICKET_CHAT, "", "",
                new F11_TicketChat.ChatRequest(TICKET_ID, "")));
        Models.TicketChatMessage attachmentMessage = ((List<?>) history.data).stream()
                .filter(Models.TicketChatMessage.class::isInstance)
                .map(Models.TicketChatMessage.class::cast)
                .filter(message -> "sample.png".equals(message.attachmentName)).findFirst().orElseThrow();
        Protocol.Packet downloaded = technician.request(new Protocol.Packet(
                Protocol.CMD_GET_CHAT_ATTACHMENT, "", "",
                new F11_TicketChat.ChatAttachmentRequest(TICKET_ID, attachmentMessage.id)));
        check(Protocol.STATUS_SUCCESS.equals(downloaded.status)
                        && downloaded.data instanceof Models.ChatAttachment attachment
                        && java.util.Arrays.equals(png, attachment.bytes),
                "chat image downloads intact with matching content");
    }

    private static void testSlaAlerts(DatabaseManager db, ServerMain server,
                                      TestClient requester, TestClient technician) throws Exception {
        String ticketId = "YC-2026-SLA-TEST";
        Models.Ticket overdue = new Models.Ticket(ticketId, "SLA overdue test", "IT", "Cao",
                "2026-10-26", "Mới", "user02", "tech01", "test", 0, "");
        overdue.slaDueAt = java.time.LocalDateTime.now().minusMinutes(1).toString();
        check(db.insertTicket(overdue), "SLA ticket fixture is stored");
        server.checkSlaDeadlines();
        check(requester.readNotification(ticketId) && technician.readNotification(ticketId),
                "SLA overdue alert is pushed to requester and assigned technician");
        check(db.getAuditEvents(100).stream().anyMatch(event -> ticketId.equals(event.entityId)
                        && "SLA_OVERDUE".equals(event.eventType)),
                "SLA overdue alert is recorded in the audit log");
    }

    private static void testHeartbeat(TestClient requester) throws Exception {
        Protocol.Packet pong = requester.request(new Protocol.Packet(Protocol.CMD_PING, "", "", null));
        check(Protocol.STATUS_SUCCESS.equals(pong.status) && "PONG".equals(pong.message),
                "TCP heartbeat receives server pong");
    }

    private static void testAutomaticReconnect(ServerMain server, int port) throws Exception {
        ClientMainGUI client = new ClientMainGUI();
        F01_Authentication.ConnectionResult initial =
                F01_Authentication.performLogin("127.0.0.1", port, "user02", "user123");
        check(initial.isConnected, "reconnect test client logs in initially");
        setField(client, "serverHost", "127.0.0.1");
        setField(client, "serverPort", port);
        setField(client, "currentUser", initial.loggedInUser);
        setField(client, "clientSocket", initial.socket);
        setField(client, "out", initial.out);
        setField(client, "in", initial.in);
        setField(client, "reconnectPassword", "user123".toCharArray());
        java.lang.reflect.Method listener = ClientMainGUI.class.getDeclaredMethod("startNotificationListener");
        listener.setAccessible(true);
        listener.invoke(client);

        ServerMain replacement = new ServerMain();
        try {
            server.stopServer();
            replacement.startServer(port);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            Socket connectedSocket = initial.socket;
            while (System.nanoTime() < deadline && connectedSocket == initial.socket) {
                Thread.sleep(100);
                connectedSocket = (Socket) getField(client, "clientSocket");
            }
            check(connectedSocket != initial.socket && !connectedSocket.isClosed(),
                    "client automatically reconnects after server restart");
            Protocol.Packet pong = client.sendAndReceiveSync(
                    new Protocol.Packet(Protocol.CMD_PING, "", "", null));
            check(Protocol.STATUS_SUCCESS.equals(pong.status), "reconnected client can exchange TCP packets");
        } finally {
            setField(client, "currentUser", null);
            setField(client, "isListening", false);
            Object password = getField(client, "reconnectPassword");
            if (password instanceof char[] secret) java.util.Arrays.fill(secret, '\0');
            Socket socket = (Socket) getField(client, "clientSocket");
            if (socket != null) socket.close();
            replacement.stopServer();
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void testChatReadStatus(DatabaseManager db, TestClient requester,
                                           TestClient technician) throws Exception {
        check(db.getUnreadTicketChatCounts("tech01", "tech").getOrDefault(TICKET_ID, 0) == 0,
                "ticket starts with no unread test messages");

        Protocol.Packet chat = requester.request(new Protocol.Packet(Protocol.CMD_SEND_TICKET_CHAT, "", "",
                new F11_TicketChat.ChatRequest(TICKET_ID, "F11 integration message")));
        check(Protocol.STATUS_SUCCESS.equals(chat.status), "authorized owner sends a chat message");
        check(requester.readNotification(TICKET_ID), "sender receives its own real-time event");
        check(technician.readNotification(TICKET_ID), "assigned technician receives the TCP push");
        check(db.getUnreadTicketChatCounts("tech01", "tech").getOrDefault(TICKET_ID, 0) == 1,
                "incoming chat increments unread count");

        TestClient stranger = connect(technician.port, "user01", "user123");
        try {
            Protocol.Packet denied = stranger.request(new Protocol.Packet(Protocol.CMD_GET_TICKET_CHAT, "", "",
                    new F11_TicketChat.ChatRequest(TICKET_ID, "")));
            check(Protocol.STATUS_ERROR.equals(denied.status), "unrelated requester cannot read ticket chat");
        } finally {
            stranger.close();
        }

        Protocol.Packet history = technician.request(new Protocol.Packet(Protocol.CMD_GET_TICKET_CHAT, "", "",
                new F11_TicketChat.ChatRequest(TICKET_ID, "")));
        check(Protocol.STATUS_SUCCESS.equals(history.status), "assigned technician reads chat history");
        check(db.getUnreadTicketChatCounts("tech01", "tech").getOrDefault(TICKET_ID, 0) == 0,
                "opening history marks messages read");
        check(containsReadMessage(history, "F11 integration message"), "history exposes persisted read state");
        check(containsReadMessage(requester.request(new Protocol.Packet(Protocol.CMD_GET_TICKET_CHAT, "", "",
                new F11_TicketChat.ChatRequest(TICKET_ID, ""))), "F11 integration message"),
                "sender sees read receipt");
    }

    private static void testConcurrentClients(int port, TestClient requester,
                                              TestClient technician) throws Exception {
        int count = 5;
        ExecutorService workers = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<TestClient> concurrentClients = new ArrayList<>();
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                TestClient client = connect(port, "tech01", "tech123");
                concurrentClients.add(client);
                int sequence = i;
                results.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) return false;
                    Protocol.Packet response = client.request(new Protocol.Packet(Protocol.CMD_SEND_TICKET_CHAT, "", "",
                            new F11_TicketChat.ChatRequest(TICKET_ID, "concurrent-" + sequence)));
                    return Protocol.STATUS_SUCCESS.equals(response.status);
                }));
            }
            check(ready.await(5, TimeUnit.SECONDS), "concurrent clients are ready");
            start.countDown();
            for (Future<Boolean> result : results) {
                check(result.get(20, TimeUnit.SECONDS), "concurrent TCP client command completes");
            }
            check(requester.readNotifications(TICKET_ID, count) >= count,
                    "owner receives concurrent pushes without losing sessions");
            Protocol.Packet history = technician.request(new Protocol.Packet(Protocol.CMD_GET_TICKET_CHAT, "", "",
                    new F11_TicketChat.ChatRequest(TICKET_ID, "")));
            check(countMessages(history, "concurrent-") == count,
                    "concurrent chat messages are all persisted");
        } finally {
            start.countDown();
            for (TestClient client : concurrentClients) client.close();
            workers.shutdownNow();
        }
    }

    private static void testDisconnectCleanup(ServerMain server, List<TestClient> clients)
            throws InterruptedException {
        for (TestClient client : clients) client.close();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (server.getActiveSessionCount() != 0 && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        check(server.getActiveSessionCount() == 0, "disconnected clients release server sessions");
    }

    private static TestClient connect(int port, String username, String password) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        IOException lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                return new TestClient(port, username, password);
            } catch (IOException e) {
                lastError = e;
                Thread.sleep(50);
            }
        }
        throw new IOException("Could not connect test client to server.", lastError);
    }

    private static int reservePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean containsReadMessage(Protocol.Packet packet, String content) {
        if (!(packet.data instanceof List<?> messages)) return false;
        return messages.stream().filter(Models.TicketChatMessage.class::isInstance)
                .map(Models.TicketChatMessage.class::cast)
                .anyMatch(message -> message.content.contains(content) && message.read);
    }

    private static int countMessages(Protocol.Packet packet, String prefix) {
        if (!(packet.data instanceof List<?> messages)) return 0;
        return (int) messages.stream().filter(Models.TicketChatMessage.class::isInstance)
                .map(Models.TicketChatMessage.class::cast)
                .filter(message -> message.content.startsWith(prefix)).count();
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("FAILED: " + description);
        System.out.println("PASS: " + description);
    }

    private static final class TestClient implements Closeable {
        private final int port;
        private final Socket socket;
        private final ObjectOutputStream out;
        private final ObjectInputStream in;
        private final LinkedBlockingQueue<Models.NotificationMessage> pendingNotifications =
                new LinkedBlockingQueue<>();

        private TestClient(int port, String username, String password) throws Exception {
            this.port = port;
            socket = NetworkSecurityConfig.createClientSocket("127.0.0.1", port, 3000);
            socket.setSoTimeout(5000);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            Protocol.Packet login = request(new Protocol.Packet(Protocol.CMD_LOGIN, "", "",
                    new F01_Authentication.LoginRequest(username, password)));
            if (!Protocol.STATUS_SUCCESS.equals(login.status)) {
                throw new IOException("Test login failed: " + login.message);
            }
        }

        private synchronized Protocol.Packet request(Protocol.Packet packet) throws Exception {
            synchronized (out) {
                out.writeObject(packet);
                out.flush();
                out.reset();
            }
            while (true) {
                Object received = in.readObject();
                if (!(received instanceof Protocol.Packet response)) continue;
                if (Protocol.CMD_NOTIFICATION.equals(response.command)) {
                    if (response.data instanceof Models.NotificationMessage notification) {
                        pendingNotifications.offer(notification);
                    }
                    continue;
                }
                return response;
            }
        }

        private synchronized boolean readNotification(String ticketId) throws Exception {
            Models.NotificationMessage pending = pendingNotifications.stream()
                    .filter(notification -> ticketId.equals(notification.ticketId))
                    .findFirst().orElse(null);
            if (pending != null) {
                pendingNotifications.remove(pending);
                return true;
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
                Object received = in.readObject();
                if (received instanceof Protocol.Packet packet
                        && Protocol.CMD_NOTIFICATION.equals(packet.command)
                        && packet.data instanceof Models.NotificationMessage notification
                        && ticketId.equals(notification.ticketId)) return true;
            }
            return false;
        }

        private synchronized int readNotifications(String ticketId, int expected) throws Exception {
            int matched = 0;
            for (Models.NotificationMessage notification : pendingNotifications) {
                if (ticketId.equals(notification.ticketId) && notification.message.contains("concurrent-")
                        && pendingNotifications.remove(notification)) matched++;
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (matched < expected && System.nanoTime() < deadline) {
                socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
                Object received = in.readObject();
                if (received instanceof Protocol.Packet packet
                        && Protocol.CMD_NOTIFICATION.equals(packet.command)
                        && packet.data instanceof Models.NotificationMessage notification
                        && ticketId.equals(notification.ticketId)
                        && notification.message.contains("concurrent-")) matched++;
            }
            return matched;
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException e) {
                throw new IllegalStateException("Could not close test socket.", e);
            }
        }
    }
}
