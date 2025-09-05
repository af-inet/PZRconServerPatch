//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package zombie.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import zombie.debug.DebugLog;

public class RCONServer {
    public static final int SERVERDATA_RESPONSE_VALUE = 0;
    public static final int SERVERDATA_AUTH_RESPONSE = 2;
    public static final int SERVERDATA_EXECCOMMAND = 2;
    public static final int SERVERDATA_AUTH = 3;
    private static final int MAX_CONNECTIONS = 10; // TODO: configurable
    private static final int SOCKET_TIMEOUT = 1000 * 30; // 30 seconds socket timeout, TODO: configurable
    private static final int MAX_PACKET_SIZE = 1024 * 8;
    private static RCONServer instance;
    private ServerSocket welcomeSocket;
    private ServerThread thread;
    private final String password;
    private final ConcurrentLinkedQueue<ExecCommand> toMain = new ConcurrentLinkedQueue();

    private RCONServer(int var1, String var2, boolean var3) {
        this.password = var2;

        try {
            this.welcomeSocket = new ServerSocket();
            if (var3) {
                this.welcomeSocket.bind(new InetSocketAddress("127.0.0.1", var1));
            } else if (GameServer.IPCommandline != null) {
                this.welcomeSocket.bind(new InetSocketAddress(GameServer.IPCommandline, var1));
            } else {
                this.welcomeSocket.bind(new InetSocketAddress(var1));
            }

            // FIX(server-socket-timeout): wake up the accept thread every 60 seconds.
            this.welcomeSocket.setSoTimeout(SOCKET_TIMEOUT);

            DebugLog.log("RCON: listening on port " + var1);
        } catch (IOException e) {
            DebugLog.log("RCON: error creating socket on port " + var1);
            e.printStackTrace();

            try {
                this.welcomeSocket.close();
                this.welcomeSocket = null;
            } catch (IOException e2) {
                e2.printStackTrace();
            }

            return;
        }

        this.thread = new ServerThread();
        this.thread.start();
    }

    private void updateMain() {
        for(ExecCommand cmd = (ExecCommand)this.toMain.poll(); cmd != null; cmd = (ExecCommand)this.toMain.poll()) {
            cmd.update();
        }

    }

    public void quit() {
        if (this.welcomeSocket != null) {
            try {
                this.welcomeSocket.close();
            } catch (IOException var2) {
            }

            this.welcomeSocket = null;
            this.thread.quit();
            this.thread = null;
        }

    }

    public static void init(int var0, String var1, boolean var2) {
        instance = new RCONServer(var0, var1, var2);
    }

    public static void update() {
        if (instance != null) {
            instance.updateMain();
        }

    }

    public static void shutdown() {
        if (instance != null) {
            instance.quit();
        }

    }

    private class ServerThread extends Thread {
        private final ArrayList<ClientThread> connections = new ArrayList();
        public boolean bQuit;

        public ServerThread() {
            this.setName("RCONServer");
        }

        public void run() {
            while(!this.bQuit) {
                this.runInner();
            }

        }

        private void runInner() {
            try {
                Socket socket = RCONServer.this.welcomeSocket.accept();

                // FIX(client-socket-timeout): don't wait forever for client sockets to do something.
                socket.setSoTimeout(SOCKET_TIMEOUT);

                for(int index = 0; index < this.connections.size(); ++index) {
                    ClientThread thread = (ClientThread)this.connections.get(index);
                    if (!thread.isAlive()) {
                        this.connections.remove(index--);
                    }
                }

                if (this.connections.size() >= RCONServer.MAX_CONNECTIONS) {
                    // FIX(server-socket-timeout): log when we have too many connections.
                    DebugLog.log("RCON: cannot accept new connections connections=" + this.connections.size());
                    socket.close();
                    return;
                }

                DebugLog.log("RCON: new connection " + socket.toString());
                ClientThread thread = new ClientThread(socket, RCONServer.this.password);
                this.connections.add(thread);
                thread.start();
            } catch (SocketTimeoutException e) {
                // FIX(server-socket-timeout): When the accept times out, let's check if we're in a bad state.
                if (this.connections.size() >= MAX_CONNECTIONS) {
                    DebugLog.log("RCON: accept timeout connections=" + this.connections.size());
                }
            } catch (IOException e) {
                if (!this.bQuit) {
                    e.printStackTrace();
                }
            }

        }

        public void quit() {
            this.bQuit = true;

            while(this.isAlive()) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            for(ClientThread thread : this.connections) {
                thread.quit();
            }

        }
    }

    private static class ExecCommand {
        public int ID;
        public String command;
        public String response;
        public ClientThread thread;

        public ExecCommand(int id, String command, ClientThread thread) {
            this.ID = id;
            this.command = command;
            this.thread = thread;
        }

        public void update() {
            this.response = GameServer.rcon(this.command);
            if (this.thread.isAlive()) {
                this.thread.toThread.add(this);
            }

        }
    }

    private static class ClientThread extends Thread {
        public Socket socket;
        public boolean bAuth;
        public boolean bQuit;
        private final String password;
        private InputStream in;
        private OutputStream out;
        private final ConcurrentLinkedQueue<ExecCommand> toThread = new ConcurrentLinkedQueue();
        private int pendingCommands;

        public ClientThread(Socket socket, String password) {
            this.socket = socket;
            this.password = password;

            try {
                this.in = socket.getInputStream();
                this.out = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.setName("RCONClient" + socket.getLocalPort());
        }

        public void run() {
            if (this.in != null && this.out != null) {
                while(!this.bQuit) {
                    try {
                        this.runInner();
                    } catch (SocketException var3) {
                        this.bQuit = true;
                    } catch (SocketTimeoutException ste) {
                        // FIX(client-socket-timeout): print a message when a client times out
                        DebugLog.log("RCON: client socket timed out, closing connection: " + this.socket.toString());
                        this.bQuit = true;
                    } catch (Exception e) {
                        // FIX(client-unexpected-error): quit client thread on unexpected errors
                        DebugLog.log("RCON: Fatal exception in client thread, closing: " + this.socket.toString());
                        this.bQuit = true;
                        e.printStackTrace();
                    }
                }
                try {
                    this.socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void runInner() throws IOException {
            byte[] bytes1 = new byte[4];
            int headerInt1 = this.in.read(bytes1, 0, 4);
            if (headerInt1 < 0) {
                this.bQuit = true;
            } else {
                ByteBuffer buffer = ByteBuffer.wrap(bytes1);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                int var4 = buffer.getInt();
                // FIX(packet-size-validation): prevent oversized or malformed packets
                if (var4 <= 0 || var4 > MAX_PACKET_SIZE) {
                    DebugLog.log("RCON: invalid packet size (" + var4 + "), closing client: " + this.socket.toString());
                    this.bQuit = true;
                    return;
                }
                int var5 = var4;
                byte[] bytes2 = new byte[var4];

                do {
                    headerInt1 = this.in.read(bytes2, var4 - var5, var5);
                    if (headerInt1 < 0) {
                        this.bQuit = true;
                        return;
                    }

                    var5 -= headerInt1;
                } while(var5 > 0);

                buffer = ByteBuffer.wrap(bytes2);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                int id = buffer.getInt();
                int type = buffer.getInt();
                String string = new String(buffer.array(), buffer.position(), buffer.limit() - buffer.position() - 2);
                this.handlePacket(id, type, string);
            }
        }

        private void handlePacket(int id, int type, String body) throws IOException {
            if (!"players".equals(body)) {
                DebugLog.log("RCON: ID=" + id + " Type=" + type + " Body='" + body + "' " + this.socket.toString());
            }

            switch (type) {
                case 0:
                    if (this.checkAuth()) {
                        ByteBuffer buffer = ByteBuffer.allocate(14);
                        buffer.order(ByteOrder.LITTLE_ENDIAN);
                        buffer.putInt(buffer.capacity() - 4);
                        buffer.putInt(id);
                        buffer.putInt(0);
                        buffer.putShort((short)0);
                        this.out.write(buffer.array());
                        // FIX(double-auth-send): don't double-write the auth packet
                        // this.out.write(buffer.array());
                    }
                    break;
                case 1:
                default:
                    DebugLog.log("RCON: unknown packet Type=" + type);
                    break;
                case 2:
                    if (this.checkAuth()) {
                        ExecCommand command = new ExecCommand(id, body, this);
                        ++this.pendingCommands;
                        RCONServer.instance.toMain.add(command);

                        // FIX(exec-timeout): don't allow ClientThreads to wait forever for a response.
                        long timeout = System.currentTimeMillis() + SOCKET_TIMEOUT;

                        while(this.pendingCommands > 0) {

                            // FIX(exec-timeout): check if the timeout has expired, if it has quit this thread.
                            // TODO: configurable timeout
                            if (System.currentTimeMillis() > timeout) {
                                this.bQuit = true;
                                DebugLog.log("RCON: command timeout, closing client: " + this.socket.toString());
                                return;
                            }

                            command = (ExecCommand)this.toThread.poll();
                            if (command != null) {
                                --this.pendingCommands;
                                this.handleResponse(command);
                            } else {
                                try {
                                    Thread.sleep(50L);
                                } catch (InterruptedException var7) {
                                    if (this.bQuit) {
                                        return;
                                    }
                                }
                            }
  
                        }

                        return;
                    }
                    break;
                case 3:
                    this.bAuth = body.equals(this.password);
                    if (!this.bAuth) {
                        DebugLog.log("RCON: password doesn't match");
                        this.bQuit = true;
                    }

                    ByteBuffer buffer = ByteBuffer.allocate(14);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.putInt(buffer.capacity() - 4);
                    buffer.putInt(id);
                    buffer.putInt(0);
                    buffer.putShort((short)0);
                    this.out.write(buffer.array());
                    buffer.clear();
                    buffer.putInt(buffer.capacity() - 4);
                    buffer.putInt(this.bAuth ? id : -1);
                    buffer.putInt(2);
                    buffer.putShort((short)0);
                    this.out.write(buffer.array());
            }

        }

        public void handleResponse(ExecCommand command) {
            String response = command.response;
            if (response == null) {
                response = "";
            }

            ByteBuffer buffer = ByteBuffer.allocate(12 + response.length() + 2);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(buffer.capacity() - 4);
            buffer.putInt(command.ID);
            buffer.putInt(0);
            buffer.put(response.getBytes());
            buffer.putShort((short)0);

            try {
                this.out.write(buffer.array());
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private boolean checkAuth() throws IOException {
            if (this.bAuth) {
                return true;
            } else {
                this.bQuit = true;
                ByteBuffer buffer = ByteBuffer.allocate(14);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putInt(buffer.capacity() - 4);
                buffer.putInt(-1);
                buffer.putInt(2);
                buffer.putShort((short)0);
                this.out.write(buffer.array());
                return false;
            }
        }

        public void quit() {
            // FIX(client-thread-quit-timeout): don't let the quit handler hang forever
            long timeout = System.currentTimeMillis() + 5000; // TODO: configurable timeout

            if (this.socket != null) {
                try {
                    this.socket.close();
                } catch (IOException var3) {
                }
            }

            this.bQuit = true;
            this.interrupt();

            // FIX(client-thread-quit-timeout): check the timeout
            while(this.isAlive() && System.currentTimeMillis() < timeout) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException var2) {
                    var2.printStackTrace();
                }
            }

        }
    }
}
