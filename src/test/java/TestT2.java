import plus.tcord.TServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;


public class TestT2 {
    public static void main(String[] args) throws IOException {
        TServer server = new TServer(23700);
        server.setHello("en:ru", "ready");

        server.queue("Unless?", new Consumer<String>() {
            @Override
            public void accept(String s) {
                System.out.println(s);
            }
        });

        server.start(true);
    }
}