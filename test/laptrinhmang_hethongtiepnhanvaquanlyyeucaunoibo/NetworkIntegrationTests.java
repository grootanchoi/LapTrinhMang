package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

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
        testNetworkAccessPolicy();
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
            testF05ResultAttachment(db, requester, technician, admin);
            testSessionPolicies(port, requester, technician, admin);
            testCorrelatedConcurrentRequests(db, port);
            testTicketQuotaAndMetadata(db, port, admin);
            testAdminTicketManagement(db, requester, technician, admin);
            testSlaAlerts(db, server, requester, technician);
            testChatAttachment(requester, technician, admin);
            testChatReadStatus(db, requester, technician);
            testHeartbeat(requester);
            testConcurrentClients(port, requester, technician, admin);
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
        check(db.addUser(new Models.User(username, "test-password", "Hash Test", "requester",
                "Đang hoạt động", "QA")),
                "new test user is stored");
        Models.User stored = db.getAllUsers().stream()
                .filter(user -> username.equals(user.username)).findFirst().orElseThrow();
        check(PasswordHasher.isEncoded(stored.password), "database never stores new plaintext passwords");
        check(db.authenticate(username, "test-password") != null, "database authenticates hashed password");
        check(db.authenticate(username, "incorrect") == null, "database rejects incorrect password");
    }

    private static void testNetworkAccessPolicy() throws Exception {
        check(NetworkSecurityConfig.isAddressAllowed(
                java.net.InetAddress.getByName("192.168.10.42")), "private IPv4 LAN client is allowed");
        check(NetworkSecurityConfig.isAddressAllowed(
                java.net.InetAddress.getByName("10.20.1.42")), "private 10/8 client is allowed");
        check(NetworkSecurityConfig.isAddressAllowed(
                java.net.InetAddress.getByName("172.20.1.42")), "private 172.16/12 client is allowed");
        check(NetworkSecurityConfig.isAddressAllowed(
                java.net.InetAddress.getByName("127.0.0.1")), "IPv4 loopback is allowed");
        check(NetworkSecurityConfig.isAddressAllowed(
                java.net.InetAddress.getByName("::1")), "IPv6 loopback is allowed");
        check(NetworkSecurityConfig.isAddressAllowed(
                java.net.InetAddress.getByName("fc00::1")), "IPv6 unique-local client is allowed");
        check(!NetworkSecurityConfig.isAddressAllowed(
                java.net.InetAddress.getByName("8.8.8.8")), "public IPv4 client is rejected");
        check(!NetworkSecurityConfig.isAddressAllowed(
                java.net.InetAddress.getByName("2001:4860:4860::8888")), "public IPv6 client is rejected");
    }

    private static void testTicketQuotaAndMetadata(DatabaseManager db, int port, TestClient admin) throws Exception {
        String username = "quota-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String password = "quota-password";
        Protocol.Packet missingDepartment = admin.request(new Protocol.Packet(Protocol.CMD_ADD_USER, "", "",
                new Models.User(username + "-missing", password, "No Department", "requester",
                        "Đang hoạt động", "")));
        check(Protocol.STATUS_ERROR.equals(missingDepartment.status),
                "admin cannot create a requester without a department");
        Protocol.Packet addUser = admin.request(new Protocol.Packet(Protocol.CMD_ADD_USER, "", "",
                new Models.User(username, password, "Quota Test", "requester", "Đang hoạt động", "QA")));
        check(Protocol.STATUS_SUCCESS.equals(addUser.status), "admin can create a requester with a department");
        Protocol.Packet users = admin.request(new Protocol.Packet(Protocol.CMD_GET_USERS, "", "",
                new F08_UserManagement.AdminUserRequest("admin", "admin")));
        check(users.data instanceof List<?> userRows && userRows.stream()
                .filter(Models.User.class::isInstance)
                .map(Models.User.class::cast)
                .anyMatch(user -> username.equals(user.username) && "QA".equals(user.department)),
                "admin user list includes the stored department");
        Models.Ticket historicalTicket = new Models.Ticket("YC-Q-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                "Old quota fixture", "IT", "Thấp", java.time.LocalDate.now().minusDays(2).toString(),
                "Mới", username, "Chưa phân công", "Older than 24 hours", 0, "");
        historicalTicket.createdAt = java.time.LocalDateTime.now().minusHours(25).toString();
        check(db.insertTicket(historicalTicket), "historical ticket fixture is stored");
        try (TestClient client = connect(port, username, password)) {
            Models.Ticket firstNewTicket = null;
            byte[] attachmentBytes = "uploaded from an independent client".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8);
            for (int i = 1; i <= 5; i++) {
                F02_CreateTicket.CreateTicketRequest request = new F02_CreateTicket.CreateTicketRequest(
                        "Quota test " + i, "IT", "Thấp", "Test quota and metadata", "forged-owner",
                        i == 1 ? "integration-attachment.txt" : "",
                        i == 1 ? attachmentBytes : null, i == 1 ? "Máy tính xách tay (Laptop)" : "");
                Protocol.Packet response = client.request(new Protocol.Packet(
                        Protocol.CMD_CREATE_TICKET, "", "", request));
                check(Protocol.STATUS_SUCCESS.equals(response.status), "requester can create ticket " + i + " within quota");
                Models.Ticket ticket = (Models.Ticket) response.data;
                if (i == 1) firstNewTicket = ticket;
                check(ticket.createdAt != null
                                && ticket.createdAt.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                        "new F02 ticket includes a full creation timestamp");
                check(username.equals(ticket.owner), "ticket owner comes from authenticated account");
                check("QA".equals(ticket.ownerDepartment), "ticket stores the requester's department snapshot");
                check("127.0.0.1".equals(ticket.sourceIp), "ticket stores the remote socket IP");
                if (i == 1) {
                    check("Máy tính xách tay (Laptop)".equals(ticket.device),
                            "F02 response includes the selected device");
                    check("Máy tính xách tay (Laptop)".equals(db.getTicketById(ticket.id).device),
                            "selected device persists in ticket storage");
                }
            }
            Protocol.Packet downloaded = client.request(new Protocol.Packet(Protocol.CMD_DOWNLOAD_FILE, "", "",
                    new F04_AssignTechnician.FileDownloadRequest(firstNewTicket.attachmentName,
                            firstNewTicket.id)));
            check(Protocol.STATUS_SUCCESS.equals(downloaded.status)
                            && downloaded.data instanceof F04_AssignTechnician.FileDownloadResponse file
                            && java.util.Arrays.equals(attachmentBytes, file.fileBytes),
                    "F02 attachment bytes are sent to and retrievable from the server");
            check("integration-attachment.txt".equals(
                            FileTransferHelper.getOriginalTicketAttachmentName(
                                    firstNewTicket.id, firstNewTicket.attachmentName)),
                    "downloaded ticket attachment name hides the ticket storage prefix");
            FileTransferHelper.deleteStoredFile(firstNewTicket.attachmentName);

            F02_CreateTicket.CreateTicketRequest sixthRequest = new F02_CreateTicket.CreateTicketRequest(
                    "Quota test 6", "IT", "Thấp", "Should exceed quota", username, "");
            Protocol.Packet rejected = client.request(new Protocol.Packet(
                    Protocol.CMD_CREATE_TICKET, "", "", sixthRequest));
            check(Protocol.STATUS_ERROR.equals(rejected.status)
                    && rejected.message.contains("giới hạn 5"), "sixth ticket within 24 hours is rejected");
            Protocol.Packet updateUser = admin.request(new Protocol.Packet(Protocol.CMD_UPDATE_USER, "", "",
                    new Models.User(username, "", "Quota Test", "requester", "Đang hoạt động", "Support")));
            check(Protocol.STATUS_SUCCESS.equals(updateUser.status)
                    && "Support".equals(db.getUserByUsername(username).department),
                    "admin can update a requester's department");
            check("QA".equals(db.getTicketById(firstNewTicket.id).ownerDepartment),
                    "existing ticket keeps the department captured when it was created");
        }
    }

    private static void testF05ResultAttachment(DatabaseManager db, TestClient requester,
                                                TestClient technician, TestClient admin) throws Exception {
        String unassignedTicketId = "YC-F05-UNASSIGNED-" + UUID.randomUUID().toString().substring(0, 8);
        Models.Ticket unassignedTicket = new Models.Ticket(unassignedTicketId, "Unassigned F05 test", "IT", "Thấp",
                java.time.LocalDate.now().toString(), "Mới", "user02", "Chưa phân công",
                "Unassigned F05 fixture", 0, "");
        check(db.insertTicket(unassignedTicket), "unassigned F05 fixture is stored");
        Protocol.Packet unassignedUpdate = technician.request(new Protocol.Packet(
                Protocol.CMD_UPDATE_STATUS, "", "",
                new F05_UpdateProgress.UpdateProgressRequest(unassignedTicketId, "Đang xử lý", 10,
                        "", "tech01", "", null)));
        check(Protocol.STATUS_ERROR.equals(unassignedUpdate.status)
                        && "Yêu cầu chưa được phân công kỹ thuật viên. Vui lòng phân công KTV trước khi cập nhật tiến độ."
                        .equals(unassignedUpdate.message),
                "F05 rejects progress updates before technician assignment");

        String ticketId = "YC-F05-" + UUID.randomUUID().toString().substring(0, 8);
        Models.Ticket ticket = new Models.Ticket(ticketId, "F05 result attachment test", "IT", "Thấp",
                java.time.LocalDate.now().toString(), "Đang xử lý", "user02", "tech01",
                "F05 result attachment fixture", 0, "");
        check(db.insertTicket(ticket), "F05 result attachment fixture is stored");
        Protocol.Packet adminUpdate = admin.request(new Protocol.Packet(Protocol.CMD_UPDATE_STATUS, "", "",
                new F05_UpdateProgress.UpdateProgressRequest(ticketId, "Đang xử lý", 10,
                        "forbidden admin update", "admin", "", null)));
        check(Protocol.STATUS_ERROR.equals(adminUpdate.status),
                "F05 rejects progress updates from admins");
        String otherTechTicketId = "YC-F05-OTHER-TECH-" + UUID.randomUUID().toString().substring(0, 8);
        Models.Ticket otherTechTicket = new Models.Ticket(otherTechTicketId, "Other technician F05 test", "IT", "Thấp",
                java.time.LocalDate.now().toString(), "Mới", "user02", "tech02",
                "F05 assigned technician authorization fixture", 0, "");
        check(db.insertTicket(otherTechTicket), "other technician F05 fixture is stored");
        Protocol.Packet unassignedTechUpdate = technician.request(new Protocol.Packet(Protocol.CMD_UPDATE_STATUS, "", "",
                new F05_UpdateProgress.UpdateProgressRequest(otherTechTicketId, "Đang xử lý", 10,
                        "forbidden other technician update", "tech01", "", null)));
        check(Protocol.STATUS_ERROR.equals(unassignedTechUpdate.status),
                "F05 rejects progress updates from technicians not assigned to the ticket");
        byte[] fileBytes = "final acceptance result".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String storedFileName = null;
        try {
            Protocol.Packet update = technician.request(new Protocol.Packet(Protocol.CMD_UPDATE_STATUS, "", "",
                    new F05_UpdateProgress.UpdateProgressRequest(ticketId, "Đang xử lý", 75,
                            "Uploaded final result", "tech01", "acceptance.txt", fileBytes)));
            check(Protocol.STATUS_SUCCESS.equals(update.status),
                    "technician can submit an F05 result attachment");
            Models.NotificationMessage notification = requester.readNotificationMessage(ticketId);
            check(notification != null && "STATUS_CHANGED".equals(notification.eventType)
                            && notification.message.contains(" | Ghi chú: Uploaded final result"),
                    "F05 progress notification includes the technician note");
            check("Uploaded final result".equals(db.getTicketById(ticketId).latestNote),
                    "F05 latest technician note is available on ticket details");
            storedFileName = db.getTicketById(ticketId).attachmentName;
            check(storedFileName.startsWith(ticketId + "_"),
                    "F05 result attachment is stored with its ticket ID prefix");

            Protocol.Packet download = technician.request(new Protocol.Packet(Protocol.CMD_DOWNLOAD_FILE, "", "",
                    new F04_AssignTechnician.FileDownloadRequest(storedFileName, ticketId)));
            check(Protocol.STATUS_SUCCESS.equals(download.status)
                            && download.data instanceof F04_AssignTechnician.FileDownloadResponse result
                            && java.util.Arrays.equals(fileBytes, result.fileBytes),
                    "F05 result attachment can be downloaded through F04");
            check("acceptance.txt".equals(FileTransferHelper.getOriginalTicketAttachmentName(
                            ticketId, storedFileName)),
                    "F05 result download restores the original filename");
        } finally {
            if (storedFileName != null) FileTransferHelper.deleteStoredFile(storedFileName);
        }
    }

    private static void testCorrelatedConcurrentRequests(DatabaseManager db, int port) throws Exception {
        String username = "correlation-" + UUID.randomUUID().toString().substring(0, 8);
        check(db.addUser(new Models.User(username, "correlation-password", "Correlation Test",
                "requester", "Đang hoạt động", "QA")), "correlation test account is created");
        F01_Authentication.ConnectionResult login = F01_Authentication.performLogin(
                "127.0.0.1", port, username, "correlation-password");
        check(login.isConnected, "correlation test account can connect");

        ExecutorService workers = Executors.newFixedThreadPool(6);
        try (NetworkService network = new NetworkService(packet -> {},
                error -> { throw new IllegalStateException("Unexpected disconnect.", error); }, user -> {})) {
            network.connect(login.socket, login.in, login.out);
            List<Future<java.util.Map.Entry<String, Protocol.Packet>>> responses = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                responses.add(workers.submit(() -> {
                    Protocol.Packet request = new Protocol.Packet(Protocol.CMD_PING, "", "", null);
                    Protocol.Packet response = network.request(request);
                    return java.util.Map.entry(request.correlationId, response);
                }));
            }
            for (Future<java.util.Map.Entry<String, Protocol.Packet>> responseFuture : responses) {
                java.util.Map.Entry<String, Protocol.Packet> correlated = responseFuture.get(
                        10, TimeUnit.SECONDS);
                Protocol.Packet response = correlated.getValue();
                check(Protocol.STATUS_SUCCESS.equals(response.status)
                                && correlated.getKey().equals(response.correlationId),
                        "concurrent requests receive their correlated response");
            }
        } finally {
            workers.shutdownNow();
        }
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

        Protocol.Packet techTickets = technician.request(new Protocol.Packet(Protocol.CMD_GET_TICKETS, "", "",
                new F03_ViewTickets.ViewTicketsRequest("tech01", "tech")));
        check(techTickets.data instanceof List<?> tickets && tickets.stream()
                .filter(Models.Ticket.class::isInstance)
                .map(Models.Ticket.class::cast)
                .allMatch(ticket -> "tech01".equalsIgnoreCase(ticket.assignedTech)),
                "technician ticket list contains only assigned tickets");
        Protocol.Packet unassignedDetail = technician.request(new Protocol.Packet(
                Protocol.CMD_GET_TICKET_DETAIL, "", "", "YC-2026-0002"));
        check(Protocol.STATUS_ERROR.equals(unassignedDetail.status),
                "technician cannot open an unassigned ticket");
        Protocol.Packet unassignedSearch = technician.request(new Protocol.Packet(Protocol.CMD_SEARCH_TICKETS, "", "",
                new F07_SearchTickets.SearchRequest("YC-2026-0002", "", "", "")));
        check(unassignedSearch.data instanceof List<?> searchResults
                        && searchResults.stream().noneMatch(ticket -> ticket instanceof Models.Ticket result
                        && "YC-2026-0002".equals(result.id)),
                "technician search does not reveal unassigned tickets");
        Protocol.Packet unassignedHistory = technician.request(new Protocol.Packet(
                Protocol.CMD_GET_HISTORY, "", "", "YC-2026-0002"));
        check(Protocol.STATUS_ERROR.equals(unassignedHistory.status),
                "technician cannot view history for an unassigned ticket");
        Protocol.Packet unassignedChat = technician.request(new Protocol.Packet(
                Protocol.CMD_GET_TICKET_CHAT, "", "",
                new F11_TicketChat.ChatRequest("YC-2026-0002", "")));
        check(Protocol.STATUS_ERROR.equals(unassignedChat.status),
                "technician cannot open chat for an unassigned ticket");
        Protocol.Packet unassignedFile = technician.request(new Protocol.Packet(Protocol.CMD_DOWNLOAD_FILE, "", "",
                new F04_AssignTechnician.FileDownloadRequest("sample.txt", "YC-2026-0002")));
        check(Protocol.STATUS_ERROR.equals(unassignedFile.status),
                "technician cannot download files for an unassigned ticket");
        Protocol.Packet statistics = technician.request(new Protocol.Packet(Protocol.CMD_GET_STATISTICS, "", "",
                new F09_TicketStatistics.StatisticsRequest("", "")));
        check(statistics.data instanceof F09_TicketStatistics.Statistics summary
                        && summary.byStatus.stream().noneMatch(row -> "Mới".equalsIgnoreCase(row.groupName))
                        && summary.byTechnician.stream().allMatch(row -> "tech01".equalsIgnoreCase(row.groupName)),
                "technician statistics include only assigned tickets");
    }

    private static void testSessionPolicies(int port, TestClient requester, TestClient technician,
                                            TestClient admin) throws Exception {
        Protocol.Packet duplicateLogin = loginAttempt(port, "user02", "user123");
        check(Protocol.STATUS_ERROR.equals(duplicateLogin.status)
                        && duplicateLogin.message.contains("đang đăng nhập"),
                "server rejects a second active login for the same account");

        String username = "lock-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        Protocol.Packet added = admin.request(new Protocol.Packet(Protocol.CMD_ADD_USER, "", "",
                new Models.User(username, "lock-password", "Lock Test", "requester",
                        "Đang hoạt động", "QA")));
        check(Protocol.STATUS_SUCCESS.equals(added.status), "admin can create account for forced logout test");
        try (TestClient target = connect(port, username, "lock-password")) {
            Protocol.Packet locked = admin.request(new Protocol.Packet(Protocol.CMD_LOCK_USER, "", "",
                    new F08_UserManagement.LockUserRequest("admin", username, true)));
            check(Protocol.STATUS_SUCCESS.equals(locked.status), "admin can lock the active requester");
            check(target.readNotificationEvent("SESSION_REVOKED"),
                    "locked account immediately receives a session-revoked notification");
            boolean disconnected = false;
            try {
                target.request(new Protocol.Packet(Protocol.CMD_PING, "", "", null));
            } catch (IOException | ClassNotFoundException e) {
                disconnected = true;
            }
            check(disconnected, "server closes the locked account's socket immediately");
            check(Protocol.STATUS_ERROR.equals(loginAttempt(port, username, "lock-password").status),
                    "locked account cannot log in again");
        }
    }

    private static void testAdminTicketManagement(DatabaseManager db, TestClient requester,
                                                   TestClient technician, TestClient admin) throws Exception {
        String ticketId = "YC-2026-TEST-ADMIN";
        Models.Ticket fixture = new Models.Ticket(ticketId, "Admin test ticket", "IT", "Thấp",
                "2026-10-26", "Mới", "user02", "Chưa phân công", "test fixture", 0, "");
        fixture.createdAt = java.time.LocalDateTime.now().withNano(0).toString();
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
        Models.Ticket updatedTicket = db.getTicketById(ticketId);
        check("Edited by admin".equals(updatedTicket.title), "admin changes persist");
        java.time.LocalDateTime createdAt = java.time.LocalDateTime.parse(updatedTicket.createdAt);
        check("Cao".equals(updatedTicket.priority)
                        && java.time.LocalDateTime.parse(updatedTicket.slaDueAt)
                        .equals(BusinessHours.addWorkingHours(createdAt, 8)),
                "changing priority to high recalculates SLA using 8 working hours from ticket creation");
        check(requester.readNotificationEvent("TICKET_UPDATED")
                        && technician.readNotificationEvent("TICKET_UPDATED")
                        && admin.readNotificationEvent("TICKET_UPDATED"),
                "ticket priority updates notify requester, technician, and admin clients");

        for (String[] priorityAndHours : new String[][]{
                {"Bình thường", "24"}, {"Thấp", "48"}, {"Khẩn cấp", "4"}
        }) {
            Protocol.Packet priorityUpdate = admin.request(new Protocol.Packet(Protocol.CMD_UPDATE_TICKET, "", "",
                    new AdminTicketManagement.UpdateRequest(ticketId, "Edited by admin", "IT",
                            priorityAndHours[0], "updated")));
            Models.Ticket recalculated = db.getTicketById(ticketId);
            check(Protocol.STATUS_SUCCESS.equals(priorityUpdate.status)
                            && java.time.LocalDateTime.parse(recalculated.slaDueAt)
                            .equals(BusinessHours.addWorkingHours(createdAt,
                                    Double.parseDouble(priorityAndHours[1]))),
                    "admin priority change recalculates SLA for " + priorityAndHours[0]);
            requester.readNotificationEvent("TICKET_UPDATED");
            technician.readNotificationEvent("TICKET_UPDATED");
            admin.readNotificationEvent("TICKET_UPDATED");
        }

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

    private static void testChatAttachment(TestClient requester, TestClient technician,
                                           TestClient admin) throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p0cAAAAASUVORK5CYII=");
        Protocol.Packet textSent = requester.request(new Protocol.Packet(Protocol.CMD_SEND_TICKET_CHAT, "", "",
                new F11_TicketChat.ChatRequest(TICKET_ID, "realtime message for online admin")));
        check(Protocol.STATUS_SUCCESS.equals(textSent.status), "authorized user sends a chat message");
        Models.NotificationMessage adminTextNotification = admin.readNotificationMessage(
                TICKET_ID, "TICKET_CHAT_MESSAGE");
        check(adminTextNotification != null
                        && adminTextNotification.message.contains("realtime message for online admin"),
                "online admin receives real-time chat messages");
        check(requester.readNotificationMessage(TICKET_ID, "TICKET_CHAT_MESSAGE") != null
                        && technician.readNotificationMessage(TICKET_ID, "TICKET_CHAT_MESSAGE") != null,
                "chat messages are pushed to connected participants");

        Protocol.Packet rejected = requester.request(new Protocol.Packet(Protocol.CMD_SEND_TICKET_CHAT, "", "",
                new F11_TicketChat.ChatAttachmentRequest(TICKET_ID, "payload.exe", "", png)));
        check(Protocol.STATUS_ERROR.equals(rejected.status), "chat rejects unsupported attachment types");
        Protocol.Packet sent = requester.request(new Protocol.Packet(Protocol.CMD_SEND_TICKET_CHAT, "", "",
                new F11_TicketChat.ChatAttachmentRequest(TICKET_ID, "sample.png", "integration attachment", png)));
        check(Protocol.STATUS_SUCCESS.equals(sent.status), "authorized user sends an image through chat");
        Models.NotificationMessage adminAttachmentNotification = admin.readNotificationMessage(
                TICKET_ID, "TICKET_CHAT_MESSAGE");
        check(adminAttachmentNotification != null && adminAttachmentNotification.attachmentMessageId > 0
                        && "sample.png".equals(adminAttachmentNotification.attachmentName),
                "online admin receives chat attachment metadata in real time");
        Models.NotificationMessage requesterAttachmentNotification = requester.readNotificationMessage(
                TICKET_ID, "TICKET_CHAT_MESSAGE");
        Models.NotificationMessage technicianAttachmentNotification = technician.readNotificationMessage(
                TICKET_ID, "TICKET_CHAT_MESSAGE");
        check(requesterAttachmentNotification != null && requesterAttachmentNotification.attachmentMessageId > 0
                        && technicianAttachmentNotification != null
                        && technicianAttachmentNotification.attachmentMessageId > 0,
                "chat attachments are pushed to connected participants");

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
        setField(client, "currentUser", initial.loggedInUser);
        NetworkService network = new NetworkService(packet -> {}, error -> {}, user -> {});
        network.setReconnectCredentials("127.0.0.1", port, "user02", "user123");
        setField(client, "networkService", network);
        network.connect(initial.socket, initial.in, initial.out);

        ServerMain replacement = new ServerMain();
        try {
            server.stopServer();
            replacement.startServer(port);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            Socket connectedSocket = initial.socket;
            while (System.nanoTime() < deadline && connectedSocket == initial.socket) {
                Thread.sleep(100);
                connectedSocket = (Socket) getField(network, "socket");
            }
            check(connectedSocket != initial.socket && !connectedSocket.isClosed(),
                    "client automatically reconnects after server restart");
            Protocol.Packet pong = client.sendAndReceiveSync(
                    new Protocol.Packet(Protocol.CMD_PING, "", "", null));
            check(Protocol.STATUS_SUCCESS.equals(pong.status), "reconnected client can exchange TCP packets");
        } finally {
            setField(client, "currentUser", null);
            network.close();
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
                                              TestClient technician, TestClient admin) throws Exception {
        int count = 5;
        ExecutorService workers = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<TestClient> concurrentClients = new ArrayList<>();
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                String username = "parallel-" + java.util.UUID.randomUUID().toString().substring(0, 8)
                        + "-" + i;
                Protocol.Packet added = admin.request(new Protocol.Packet(Protocol.CMD_ADD_USER, "", "",
                        new Models.User(username, "parallel-password", "Parallel Operator", "admin",
                                "Đang hoạt động", "IT")));
                check(Protocol.STATUS_SUCCESS.equals(added.status),
                        "admin creates a distinct account for each concurrent client");
                TestClient client = connect(port, username, "parallel-password");
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

    private static Protocol.Packet loginAttempt(int port, String username, String password) throws Exception {
        try (Socket socket = NetworkSecurityConfig.createClientSocket("127.0.0.1", port, 3000)) {
            socket.setSoTimeout(5000);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            out.writeObject(new Protocol.Packet(Protocol.CMD_LOGIN, "", "",
                    new F01_Authentication.LoginRequest(username, password)));
            out.flush();
            Object response = in.readObject();
            if (!(response instanceof Protocol.Packet packet)) {
                throw new IOException("Server returned an invalid login response.");
            }
            return packet;
        }
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
            if (packet.correlationId == null) packet.correlationId = UUID.randomUUID().toString();
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
                if (!packet.correlationId.equals(response.correlationId)) {
                    throw new IOException("Response correlation ID does not match request.");
                }
                return response;
            }
        }

        private synchronized boolean readNotification(String ticketId) throws Exception {
            return readNotificationMessage(ticketId) != null;
        }

        private synchronized Models.NotificationMessage readNotificationMessage(String ticketId) throws Exception {
            return readNotificationMessage(ticketId, null);
        }

        private synchronized Models.NotificationMessage readNotificationMessage(String ticketId,
                                                                                 String eventType) throws Exception {
            Models.NotificationMessage pending = pendingNotifications.stream()
                    .filter(notification -> ticketId.equals(notification.ticketId)
                            && (eventType == null || eventType.equals(notification.eventType)))
                    .findFirst().orElse(null);
            if (pending != null) {
                pendingNotifications.remove(pending);
                return pending;
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
                Object received = in.readObject();
                if (received instanceof Protocol.Packet packet
                        && Protocol.CMD_NOTIFICATION.equals(packet.command)
                        && packet.data instanceof Models.NotificationMessage notification
                        && ticketId.equals(notification.ticketId)
                        && (eventType == null || eventType.equals(notification.eventType))) return notification;
                if (received instanceof Protocol.Packet packet
                        && Protocol.CMD_NOTIFICATION.equals(packet.command)
                        && packet.data instanceof Models.NotificationMessage notification) {
                    pendingNotifications.offer(notification);
                }
            }
            return null;
        }

        private synchronized boolean readNotificationEvent(String eventType) throws Exception {
            Models.NotificationMessage pending = pendingNotifications.stream()
                    .filter(notification -> eventType.equals(notification.eventType))
                    .findFirst().orElse(null);
            if (pending != null) {
                pendingNotifications.remove(pending);
                return true;
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                socket.setSoTimeout((int) Math.max(1,
                        TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
                Object received = in.readObject();
                if (received instanceof Protocol.Packet packet
                        && Protocol.CMD_NOTIFICATION.equals(packet.command)
                        && packet.data instanceof Models.NotificationMessage notification
                        && eventType.equals(notification.eventType)) return true;
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
