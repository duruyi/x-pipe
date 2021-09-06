package latte.test;

import com.ctrip.framework.xpipe.redis.ProxyRegistry;
import com.ctrip.framework.xpipe.redis.instrument.ProxyAgentTool;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

@SpringBootApplication
public class App {
    public static void main(String[] args) throws IOException {
//        ProxyRegistry.registerProxy("127.0.0.1", 6666, "PROXY ROUTE PROXYTCP://127.0.0.1:10080 TCP");
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", 6666), 1000);
    }
}
