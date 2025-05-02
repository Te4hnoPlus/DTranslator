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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.*;
import static plus.tcord.TCordMain.DEBUG;


/**
 * Сервер децентрализованных переводов
 * Получает задачи из пакета и распределяет их между подключенными узлами
 */
public class TServer {
    protected final ConcurrentHashMap<String, CNode> connections = new ConcurrentHashMap<>();
    protected final CopyOnWriteArrayList<TSubTask> subprocessors = new CopyOnWriteArrayList<>();
    protected ServerSocket sSocket;
    protected final int port;
    protected Thread thread;
    protected String hello;
    protected String awaitHello;
    protected boolean active;
    private JTranslationPack pack;

    public TServer(int port) {
        this.port = port;
    }


    /**
     * Установить приветственное сообщение
     */
    public void setHello(String hello, String awaitHello) {
        this.hello = hello;
        this.awaitHello = awaitHello;
    }


    /**
     * Установить целевой пакет переводов
     */
    public void setPack(JTranslationPack pack) {
        this.pack = pack;
    }


    /**
     * Получить целевой пакет переводов
     */
    public JTranslationPack getPack() {
        return pack;
    }


    /**
     * Получить текущий статус активности сервера
     */
    public boolean isActive() {
        return active;
    }


    /**
     * Корректно остановить сервер
     */
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

        for (TSubTask proc : subprocessors) {
            try {
                proc.onStop(this);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Добавить сабпроцессор
     */
    public void sub(TSubTask proc) {
        subprocessors.add(proc);
    }


    /**
     * Запустить сервер
     */
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


    /**
     * Обновление сабпроцессоров
     */
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


    /**
     * Основной цикл
     */
    private void loop(){
        while (active){
            try {
                runLoop();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    /**
     * Получить или создать узел с которого пришло новое соединение
     */
    private CNode getOrCreateNode(Socket socket) {
        String ip = Connection.ip(socket);
        CNode node = connections.get(ip);
        if (node == null) {
            node = new CNode();
            connections.put(ip, node);
        }
        return node;
    }


    /**
     * Обработка нового соединения
     */
    private void runLoop() throws IOException {
        Socket socket = sSocket.accept();
        onNewConnection(new Connection(getOrCreateNode(socket), socket));
    }


    /**
     * Основной цикл обновления соединения
     */
    protected void onNewConnection(Connection connection){
        Thread.startVirtualThread(() -> {
            if(!processHandshake(connection)) return;
            while (active && connection.isConnected()) {
                try {
                    if (connection.isConnected()) {
                        processConnection(connection);
                    } else {
                        if(!connection.attempt())
                            connection.disconnect();
                    }
                } catch (Exception e) {
                    if(DEBUG)
                        e.printStackTrace();
                    if(!connection.attempt())
                        break;
                }
            }
            connection.disconnect();
        });
    }


    /**
     * Рукопожатие между сервером и клиентом
     * Клиент настраивается на текущий режим перевода в приветственном сообщении и
     * сообщает о готовности
     * После чего сервер начинает отправлять задачи на перевод
     */
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


    /**
     * Работа с клиентом и отправка задач на перевод
     */
    protected void processConnection(Connection connection) throws InterruptedException {
        String msg = pack.nextItem();
        if(msg == null) {
            if(connection.attempt()) Thread.sleep(1000);
            else connection.disconnect();
            return;
        } else {
            connection.resetAttempts();
        }

        connection.write(msg);
        String[] result = connection.readAnsw();

        String key = result[0];
        String value = result[1];

        if(!Objects.equals(key, msg)){
            throw new RuntimeException("Key mismatch: " + key + " != " + msg);
        }

        pack.set(key, value);
        ++connection.completedTasks;
    }


    /**
     * Узел с которого пришло соединение
     */
    public static final class CNode{
        private final HashMap<Integer, Connection> connections = new HashMap<>();

        public void add(Connection connection) {
            connections.put(connection.port(), connection);
        }


        public boolean remove(int port) {
            return connections.remove(port) != null;
        }


        /**
         * Подсчитать количество завершенных задач этим узлом
         */
        public int completedTasks() {
            int sum = 0;
            for (Connection connection : connections.values())
                sum += connection.getCompletedTasks();
            return sum;
        }
    }


    /**
     * Конкретное соединение
     */
    public static final class Connection{
        private final CNode node;
        private final Socket socket;
        private final InputStream input;
        private final OutputStream out;
        private byte[] buffer = new byte[1024*20];
        private int attempts = 60, completedTasks;
        private int totalFrom, totalTo;

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


        /**
         * Получить узел
         */
        public CNode node(){
            return node;
        }


        /**
         * Получить порт конкретного подключения
         */
        public int port(){
            return socket.getPort();
        }


        /**
         * Получить IP сокета
         */
        public static String ip(Socket socket){
            return socket.getInetAddress().getHostAddress();
        }


        /**
         * Получить IP узла
         */
        public String ip(){
            return ip(socket);
        }


        /**
         * Получить количество выполненных задач этим подключением
         */
        public int getCompletedTasks() {
            return completedTasks;
        }


        public boolean attempt() {
            return attempts-->0;
        }


        public void resetAttempts() {
            attempts = 60;
        }


        public boolean isConnected() {
            return socket.isConnected();
        }


        /**
         * Закрыть соединение
         */
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


        /**
         * Прочитать ответ с клиента
         */
        private String[] readAnsw(){
            JsonElement jsn = JsonParser.parseString(readStr());
            JsonObject obj = jsn.getAsJsonObject();
            return new String[]{
                    obj.get("k").getAsString(),
                    obj.get("v").getAsString()
            };
        }


        /**
         * Прочитать ответ с клиента как строку
         */
        public String readStr() {
            int bufLen = 0;
            try {
                bufLen = input.read(buffer);
            } catch (IOException e) {
                if(DEBUG)
                    e.printStackTrace();
                disconnect();
            }
            return new String(buffer, 0, bufLen, StandardCharsets.UTF_8);
        }


        /**
         * Отправить клиенту строку
         */
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