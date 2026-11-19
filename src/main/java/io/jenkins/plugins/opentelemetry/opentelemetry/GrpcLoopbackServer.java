/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal OTLP/gRPC endpoint on the loopback interface, for {@link AgentExportPathWarmUp}. The JDK has no HTTP/2
 * server, so this one only speaks what the OkHttp gRPC sender of the OTLP exporter sends to a {@code http://}
 * endpoint: HTTP/2 with prior knowledge (h2c), one connection at a time. Every request is answered like a collector
 * does: {@code :status 200}, no body, and the trailer {@code grpc-status 0}, so the sender runs its real response
 * and trailer handling.
 */
final class GrpcLoopbackServer implements Closeable {

    private static final byte[] PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private static final int DATA = 0x0;
    private static final int HEADERS = 0x1;
    private static final int SETTINGS = 0x4;
    private static final int PING = 0x6;
    private static final int GOAWAY = 0x7;

    private static final int FLAG_ACK = 0x1;
    private static final int FLAG_END_STREAM = 0x1;
    private static final int FLAG_END_HEADERS = 0x4;
    private static final int FLAG_PADDED = 0x8;

    /** Length of the gRPC message prefix (compressed flag and message length) */
    private static final int GRPC_MESSAGE_PREFIX_LENGTH = 5;

    /**
     * HPACK: {@code :status 200} (static table index 8), then {@code content-type application/grpc} (literal without
     * indexing, name from static table index 31).
     */
    private static final byte[] RESPONSE_HEADERS =
            concat(new byte[] {(byte) 0x88, 0x0f, 0x10, 16}, "application/grpc".getBytes(StandardCharsets.US_ASCII));

    /** HPACK: {@code grpc-status 0} (literal without indexing, new name) */
    private static final byte[] RESPONSE_TRAILERS =
            concat(new byte[] {0x00, 11}, "grpc-status".getBytes(StandardCharsets.US_ASCII), new byte[] {1, '0'});

    private final ServerSocket serverSocket;
    private final int timeoutMillis;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile Socket socket;

    GrpcLoopbackServer(int timeoutMillis) throws IOException {
        this.timeoutMillis = timeoutMillis;
        this.serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Thread thread = new Thread(this::serve, "OpenTelemetry export path warm-up (gRPC)");
        thread.setDaemon(true);
        thread.start();
    }

    InetSocketAddress getAddress() {
        return new InetSocketAddress(serverSocket.getInetAddress(), serverSocket.getLocalPort());
    }

    /**
     * @return the number of requests received with a non-empty gRPC message
     */
    int getRequests() {
        return requests.get();
    }

    private void serve() {
        while (!serverSocket.isClosed()) {
            try (Socket accepted = serverSocket.accept()) {
                socket = accepted;
                accepted.setSoTimeout(timeoutMillis);
                serve(
                        new DataInputStream(new BufferedInputStream(accepted.getInputStream())),
                        new BufferedOutputStream(accepted.getOutputStream()));
            } catch (IOException e) {
                // the server or the client closed the connection
            }
        }
    }

    private void serve(DataInputStream in, OutputStream out) throws IOException {
        byte[] preface = new byte[PREFACE.length];
        in.readFully(preface);
        if (!Arrays.equals(preface, PREFACE)) {
            throw new IOException("Not an HTTP/2 connection preface");
        }
        writeFrame(out, SETTINGS, 0, 0, new byte[0]);
        out.flush();
        Map<Integer, Integer> messageLengths = new HashMap<>();
        for (; ; ) {
            int length = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
            int type = in.readUnsignedByte();
            int flags = in.readUnsignedByte();
            int stream = in.readInt() & 0x7fffffff;
            byte[] payload = new byte[length];
            in.readFully(payload);
            switch (type) {
                case SETTINGS -> {
                    if ((flags & FLAG_ACK) == 0) {
                        writeFrame(out, SETTINGS, FLAG_ACK, 0, new byte[0]);
                    }
                }
                case PING -> {
                    if ((flags & FLAG_ACK) == 0) {
                        writeFrame(out, PING, FLAG_ACK, 0, payload);
                    }
                }
                case GOAWAY -> {
                    return;
                }
                case DATA -> {
                    int padding = (flags & FLAG_PADDED) != 0 && length > 0 ? 1 + (payload[0] & 0xff) : 0;
                    messageLengths.merge(stream, length - padding, Integer::sum);
                }
                default -> {
                    // HEADERS, CONTINUATION, WINDOW_UPDATE, PRIORITY, RST_STREAM: nothing to answer
                }
            }
            if ((type == DATA || type == HEADERS) && (flags & FLAG_END_STREAM) != 0) {
                Integer messageLength = messageLengths.remove(stream);
                if (messageLength != null && messageLength > GRPC_MESSAGE_PREFIX_LENGTH) {
                    requests.incrementAndGet();
                }
                writeFrame(out, HEADERS, FLAG_END_HEADERS, stream, RESPONSE_HEADERS);
                writeFrame(out, HEADERS, FLAG_END_HEADERS | FLAG_END_STREAM, stream, RESPONSE_TRAILERS);
            }
            out.flush();
        }
    }

    private static void writeFrame(OutputStream out, int type, int flags, int stream, byte[] payload)
            throws IOException {
        out.write(payload.length >>> 16);
        out.write(payload.length >>> 8);
        out.write(payload.length);
        out.write(type);
        out.write(flags);
        out.write(stream >>> 24);
        out.write(stream >>> 16);
        out.write(stream >>> 8);
        out.write(stream);
        out.write(payload);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            // ignore
        }
        Socket current = socket;
        if (current != null) {
            try {
                current.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
