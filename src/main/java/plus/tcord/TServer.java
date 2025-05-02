package plus.tcord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import plus.tcord.task.TSubTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static plus.tcord.TCordMain.DEBUG;


public class TServer {
    protected final ConcurrentHashMap<String, CNode> connections = new ConcurrentHashMap<>();
    protected final Queue<Task> inQueue                          = new LinkedBlockingQueue<>();
    protected final CopyOnWriteArrayList<TSubTask> subprocessors = new CopyOnWriteArrayList<>();
    protected ServerSocket sSocket;
    protected final int port;
    protected Thread thread;
    protected String hello;
    protected String awaitHello;
    protected boolean active;


    public TServer(int port) {
        this.port = port;
    }


    public void setHello(String hello, String awaitHello) {
        this.hello = hello;
        this.awaitHello = awaitHello;
    }


    public boolean isActive() {
        return active;
    }


    public void stop() {
        active = false;
        try {
            sSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        connections.clear();
        if(thread != null) {
            thread.interrupt();
            thread = null;
        }

        Queue<Task> qw = inQueue;
        Task next = qw.poll();
        while (next != null) {
            try {
                next.callback.accept(null);
            } catch (Throwable e){
                e.printStackTrace();
            }
            next = qw.poll();
        }

        for (TSubTask proc : subprocessors) {
            try {
                proc.onStop(this);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }


    public void sub(TSubTask proc) {
        subprocessors.add(proc);
    }


    public void start(boolean curThread) throws IOException {
        active = true;
        sSocket = new ServerSocket(port);

        Thread.startVirtualThread(this::loopSubprocessors);

        for (TSubTask proc : subprocessors) {
            try {
                proc.onStart(this);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        if(curThread){
            loop();
        } else {
            thread = new Thread(() -> {
                loop();
            }, "TCord main thread");
            thread.setDaemon(true);
            thread.start();
        }
    }


    private synchronized void loopSubprocessors() {
        while (active){
            if(!subprocessors.isEmpty()){
                for (TSubTask proc : subprocessors) try {
                    proc.onTick(this);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


    private void loop(){
        while (active){
            try {
                runLoop();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    private CNode getOrCreateNode(Socket socket) {
        String ip = Connection.ip(socket);
        CNode node = connections.get(ip);
        if (node == null) {
            node = new CNode();
            connections.put(ip, node);
        }
        return node;
    }


    private void runLoop() throws IOException {
        Socket socket = sSocket.accept();
        onNevConnection(new Connection(getOrCreateNode(socket), socket));
    }


    protected void onNevConnection(Connection connection){
        Thread.startVirtualThread(() -> {
            if(!processHandshake(connection)) return;
            while (active && connection.isConnected()) {
                try {
                    if (connection.isConnected()) {
                        processConnection(connection);
                    } else {
                        connection.disconnect();
                    }
                } catch (Exception e) {
                    if(DEBUG)
                        e.printStackTrace();
                    break;
                }
            }
            connection.disconnect();
        });
    }


    protected boolean processHandshake(Connection connection) {
        try {
            connection.write(hello);
            String result = connection.readStr();
            if (!awaitHello.equals(result)){
                connection.disconnect();
                return false;
            }
            connection.node().add(connection);
            System.out.printf("New connection from %s:%s\n", connection.ip(), connection.port());
        } catch (Exception e) {
            if(DEBUG)
                e.printStackTrace();
        }
        return true;
    }


    protected void processConnection(Connection connection) throws InterruptedException {
        Task msg = inQueue.poll();
        if(msg == null) {
            if(connection.attempt()) Thread.sleep(1000);
            else connection.disconnect();
            return;
        } else {
            connection.resetAttempts();
        }

        connection.write(msg.msg);
        String[] result = connection.readAnsw();

        String key = result[0];
        String value = result[1];

        if(!key.equals(msg.msg)){
            throw new RuntimeException("CRITICAL ERROR");
        }

        msg.callback.accept(value);
        ++connection.completedTasks;
    }


    public void queue(String msg, Consumer<String> callback) {
        inQueue.add(new Task(msg, callback));
    }


    public int queueSize() {
        return inQueue.size();
    }


    protected static final class Task {
        public final String msg;
        public final Consumer<String> callback;

        Task(String msg, Consumer<String> callback) {
            this.msg = msg;
            this.callback = callback;
        }
        @Override
        public String toString() {
            return msg;
        }
    }


    public static final class CNode{
        private final HashMap<Integer, Connection> connections = new HashMap<>();

        public void add(Connection connection) {
            connections.put(connection.port(), connection);
        }

        public boolean remove(int port) {
            return connections.remove(port) != null;
        }


        public int completedTasks() {
            int sum = 0;
            for (Connection connection : connections.values())
                sum += connection.getCompletedTasks();
            return sum;
        }
    }


    public static final class Connection{
        public final CNode node;
        public final Socket socket;
        public final InputStream input;
        public final OutputStream out;
        public byte[] buffer = new byte[1024*10];
        public int attempts = 10, completedTasks, totalFrom, totalTo;

        public Connection(CNode node, Socket socket, InputStream input, OutputStream out) {
            this.node = node;
            this.socket = socket;
            this.input = input;
            this.out = out;
        }

        public Connection(CNode node, Socket socket) throws IOException {
            this.node = node;
            this.socket = socket;
            this.input = socket.getInputStream();
            this.out = socket.getOutputStream();
        }


        public CNode node(){
            return node;
        }


        public int port(){
            return socket.getPort();
        }


        public static String ip(Socket socket){
            return socket.getInetAddress().getHostAddress();
        }


        public String ip(){
            return ip(socket);
        }


        private void onCompleteTask(Task task, String result){
            ++completedTasks;
            totalFrom += task.msg.length();
            totalTo   += result.length();
        }


        public int getCompletedTasks() {
            return completedTasks;
        }


        public boolean attempt() {
            return attempts-->0;
        }


        public void resetAttempts() {
            attempts = 10;
        }


        public boolean isConnected() {
            return socket.isConnected();
        }


        public void disconnect() {
            if(node.remove(port())){
                System.out.printf("Connection from %s:%s closed\n", ip(), port());
            }
            try {
                socket.close();
                input.close();
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


        private String[] readAnsw(){
            JsonElement jsn = JsonParser.parseString(readStr());
            JsonObject obj = jsn.getAsJsonObject();
            return new String[]{
                    obj.get("k").getAsString(),
                    obj.get("v").getAsString()
            };
        }


        public String readStr() {
            int bufLen = 0;
            try {
                bufLen = input.read(buffer);
            } catch (IOException e) {
                if(DEBUG)
                    e.printStackTrace();
                disconnect();
            }
            return new String(buffer, 0, bufLen);
        }


        public void write(String msg) {
            try {
                out.write(msg.getBytes());
            } catch (IOException e) {
                if(DEBUG)
                    e.printStackTrace();
                disconnect();
            }
        }


        @Override
        public String toString() {
            return ip()+":"+port();
        }


        @Override
        public int hashCode() {
            return toString().hashCode();
        }
    }
}