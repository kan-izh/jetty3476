package org.jetty.issue3476;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
        final URI uri = new URI("ws://127.1.2.3:12712/");

        Server server = new Server(new InetSocketAddress(uri.getHost(), uri.getPort()));
        server.setHandler(new WsHandler());
        server.start();

        final WebSocketClient client = new WebSocketClient();
        client.start();
        final int messages = 100;
        final int threads = 2;
        final CountDownLatch latch = new CountDownLatch(threads * messages);
        printProgress(latch);
        for (int i = 0; i < threads; ++i) {
            new Thread(() ->
            {
                for (int msgNo = 0; msgNo < messages; ++msgNo) {
                    try {
                        client.connect(new WsListener(latch), uri);//.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();

        }
        latch.await(3, TimeUnit.MINUTES);
        client.stop();
        server.stop();
    }

    private static void printProgress(final CountDownLatch latch) {
        final Thread thread = new Thread(() ->
        {
            while (true) {
                System.out.println("To go: " + latch.getCount());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static class WsHandler extends WebSocketHandler {
        @Override
        public void configure(final WebSocketServletFactory factory) {
            factory.setCreator((req, resp) -> new WebSocketListener(){
                private Session session;

                @Override
                public void onWebSocketBinary(final byte[] payload, final int offset, final int len) {

                }

                @Override
                public void onWebSocketText(final String message) {
                    try {
                        session.getRemote().sendString(message + "-pong");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onWebSocketClose(final int statusCode, final String reason) {
                    this.session = null;
                }

                @Override
                public void onWebSocketConnect(final Session session) {
                    this.session = session;
                }

                @Override
                public void onWebSocketError(final Throwable cause) {
                    cause.printStackTrace();
                }
            });
        }
    }

    private static class WsListener implements WebSocketListener {
        private final CountDownLatch latch;
        private Session session;

        public WsListener(final CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onWebSocketClose(final int statusCode, final String reason) {

        }

        @Override
        public void onWebSocketConnect(final Session session) {
            this.session = session;
            try {
                this.session.getRemote().sendString("Ping");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onWebSocketError(final Throwable cause) {
            cause.printStackTrace();
        }

        @Override
        public void onWebSocketBinary(final byte[] payload, final int offset, final int len) {

        }

        @Override
        public void onWebSocketText(final String message) {
            if (message.endsWith("-pong")) {
                session.close();
                latch.countDown();
                return;
            }
            throw new RuntimeException("Hmm?");
        }
    }
}
