package network.tcp;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A multi-threaded TCP server that receives serializable Java objects from clients.
 * <p>
 * Features: - Accepts multiple concurrent client connections - Thread pool for efficient client handling - Graceful shutdown
 * with resource cleanup - Connection state tracking - Comprehensive error handling
 */
public class TCPServer {

  // Server configuration
  private final int port;
  private ServerSocket serverSocket;

  // Thread management
  private final ExecutorService clientThreadPool;
  private Thread acceptThread;

  // Server state
  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final AtomicInteger activeConnections = new AtomicInteger(0);
  private final AtomicInteger totalConnectionsAccepted = new AtomicInteger(0);

  // Thread pool configuration
  private static final int MAX_CLIENTS = 50;

  /**
   * Creates a new TCP server that listens on the specified port.
   *
   * @param port The port number to listen on
   */
  public TCPServer(int port) {
    this.port = port;

    // Create a fixed thread pool for handling client connections
    this.clientThreadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
  }

  /**
   * Starts the TCP server and begins accepting client connections. This method returns immediately; the server runs on
   * background threads.
   *
   * @throws IOException If the server cannot bind to the specified port
   */
  public void start() throws IOException {
    if (isRunning.get()) {
      System.out.println("Server is already running.");
      return;
    }

    // Create server socket
    serverSocket = new ServerSocket(port);
    serverSocket.setReuseAddress(true); // Allow port reuse

    isRunning.set(true);

    System.out.println("===========================================");
    System.out.println("TCP Server started on port " + port);
    System.out.println("Waiting for client connections...");
    System.out.println("===========================================");

    // Start the accept thread
    startAcceptThread();
  }

  /**
   * Starts the thread that accepts incoming client connections. Each accepted connection is handled by a separate thread
   * from the pool.
   */
  private void startAcceptThread() {
    acceptThread = new Thread(() -> {
      while (isRunning.get()) {
        try {
          // Accept incoming client connection (blocking call)
          Socket clientSocket = serverSocket.accept();

          // Increment connection counters
          int connectionId = totalConnectionsAccepted.incrementAndGet();
          activeConnections.incrementAndGet();

          // Log connection info
          String clientInfo = clientSocket.getInetAddress().getHostAddress() +
              ":" + clientSocket.getPort();
          System.out.println("\n[Connection #" + connectionId + "] New client connected: " + clientInfo);
          System.out.println("[Status] Active connections: " + activeConnections.get());

          // Handle client in a separate thread from the pool
          clientThreadPool.execute(new ClientHandler(clientSocket, connectionId));

        } catch (SocketException e) {
          // Socket closed during shutdown - this is expected
          if (isRunning.get()) {
            System.err.println("Socket error: " + e.getMessage());
          }
        } catch (IOException e) {
          if (isRunning.get()) {
            System.err.println("Error accepting client connection: " + e.getMessage());
          }
        }
      }
    }, "TCP-Accept-Thread");

    acceptThread.setDaemon(true);
    acceptThread.start();
  }

  /**
   * Gets the number of currently active client connections.
   *
   * @return The number of active connections
   */
  public int getActiveConnectionCount() {
    return activeConnections.get();
  }

  /**
   * Gets the total number of connections accepted since server started.
   *
   * @return The total connection count
   */
  public int getTotalConnectionCount() {
    return totalConnectionsAccepted.get();
  }

  /**
   * Checks if the server is currently running.
   *
   * @return true if server is running, false otherwise
   */
  public boolean isRunning() {
    return isRunning.get();
  }

  /**
   * Gracefully shuts down the server. Waits for active connections to complete (up to timeout).
   *
   * @param timeoutSeconds Maximum time to wait for connections to close
   */
  public void shutdown(int timeoutSeconds) {
    if (!isRunning.get()) {
      System.out.println("Server is not running.");
      return;
    }

    System.out.println("\n===========================================");
    System.out.println("Initiating server shutdown...");
    System.out.println("===========================================");

    // Stop accepting new connections
    isRunning.set(false);

    // Close server socket to unblock accept()
    try {
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
    } catch (IOException e) {
      System.err.println("Error closing server socket: " + e.getMessage());
    }

    // Shutdown thread pool gracefully
    clientThreadPool.shutdown();

    try {
      // Wait for existing client handlers to complete
      if (!clientThreadPool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
        System.out.println("Timeout reached. Forcing shutdown of client handlers...");
        clientThreadPool.shutdownNow();

        // Wait a bit more for forced shutdown
        if (!clientThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
          System.err.println("Thread pool did not terminate cleanly.");
        }
      }
    } catch (InterruptedException e) {
      clientThreadPool.shutdownNow();
      Thread.currentThread().interrupt();
    }

    // Wait for accept thread to finish
    if (acceptThread != null) {
      try {
        acceptThread.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    System.out.println("===========================================");
    System.out.println("Server shutdown complete.");
    System.out.println("Total connections served: " + totalConnectionsAccepted.get());
    System.out.println("===========================================");
  }

  /**
   * Shuts down the server immediately without waiting.
   */
  public void shutdown() {
    shutdown(10);
  }

  /**
   * Handles communication with a single client connection. Receives and processes serialized objects from the client.
   */
  private class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final int connectionId;
    private InputStream inputStream;

    /**
     * Creates a new client handler.
     *
     * @param clientSocket The socket connected to the client
     * @param connectionId Unique identifier for this connection
     */
    public ClientHandler(Socket clientSocket, int connectionId) {
      this.clientSocket = clientSocket;
      this.connectionId = connectionId;
    }

    @Override
    public void run() {
      String clientInfo = clientSocket.getInetAddress().getHostAddress() +
          ":" + clientSocket.getPort();

      try {
        // Configure socket options
        clientSocket.setKeepAlive(true);
        clientSocket.setTcpNoDelay(true);
        clientSocket.setSoTimeout(0); // No read timeout

        // Create object input stream for receiving serializable objects
        inputStream = clientSocket.getInputStream();

        System.out.println("[Connection #" + connectionId + "] Ready to receive objects from " + clientInfo);

        // Continuously read objects from the client
        while (isRunning.get() && !clientSocket.isClosed()) {
          try {

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int byteRead;
            // Read until no more data is immediately available
            while (inputStream.available() > 0 && (byteRead = inputStream.read()) != -1) {
              buffer.write(byteRead);
            }
            if (buffer.size() > 0) {
              printHexArray(buffer.toByteArray());
            }

//            // Read object from stream (blocking call)
//            byte[] received = new byte[1024];
//            int bytesRead = inputStream.read(received);
//            if (bytesRead > 0) {
//              String message = new String(received, 0, bytesRead);
//              // Process the received object
//              handleReceivedObject(message, clientInfo);
//            }

          } catch (EOFException e) {
            // Client closed connection gracefully
            System.out.println("[Connection #" + connectionId + "] Client disconnected: " + clientInfo);
            break;

          } catch (SocketException e) {
            // Connection reset or closed unexpectedly
            System.out.println("[Connection #" + connectionId + "] Connection lost: " + clientInfo +
                " - " + e.getMessage());
            break;

          } catch (IOException e) {
            // Other I/O error
            System.err.println("[Connection #" + connectionId + "] I/O error: " + e.getMessage());
            break;
          }
        }

      } catch (IOException e) {
        System.err.println("[Connection #" + connectionId + "] Error setting up client handler: " +
            e.getMessage());

      } finally {
        // Clean up resources
        cleanup(clientInfo);
      }
    }

    /**
     * Processes a received object from the client. This method can be customized to handle different object types.
     *
     * @param obj        The received object
     * @param clientInfo Information about the client
     */
    private void handleReceivedObject(Object obj, String clientInfo) {
      System.out.println("\n┌─────────────────────────────────────────┐");
      System.out.println("│ OBJECT RECEIVED                         │");
      System.out.println("├─────────────────────────────────────────┤");
      System.out.println("│ Connection ID: #" + connectionId);
      System.out.println("│ From: " + clientInfo);
      System.out.println("│ Class: " + obj.getClass().getName());
      System.out.println("│ Content: " + obj.toString());
      System.out.println("└─────────────────────────────────────────┘\n");

      // Custom handling based on object type
      if (obj instanceof String) {
        handleStringMessage((String) obj);
      } else if (obj instanceof Number) {
        handleNumberMessage((Number) obj);
      } else {
        // Generic handling for other objects
        handleGenericObject(obj);
      }
    }

    /**
     * Handles received String objects.
     *
     * @param message The received string message
     */
    private void handleStringMessage(String message) {
      System.out.println("[Handler] Processing String message: " + message);
      // Add custom string processing logic here
    }

    /**
     * Handles received Number objects.
     *
     * @param number The received number
     */
    private void handleNumberMessage(Number number) {
      System.out.println("[Handler] Processing Number: " + number);
      // Add custom number processing logic here
    }

    /**
     * Handles other types of objects.
     *
     * @param obj The received object
     */
    private void handleGenericObject(Object obj) {
      System.out.println("[Handler] Processing generic object of type: " +
          obj.getClass().getSimpleName());
      // Add custom object processing logic here
    }

    /**
     * Cleans up resources associated with this client connection.
     *
     * @param clientInfo Information about the client
     */
    private void cleanup(String clientInfo) {
      // Close input stream
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          // Ignore errors during cleanup
        }
      }

      // Close socket
      if (clientSocket != null && !clientSocket.isClosed()) {
        try {
          clientSocket.close();
        } catch (IOException e) {
          // Ignore errors during cleanup
        }
      }

      // Update connection count
      int remaining = activeConnections.decrementAndGet();
      System.out.println("[Connection #" + connectionId + "] Client handler terminated: " + clientInfo);
      System.out.println("[Status] Active connections: " + remaining);
    }
  }

  public static void printHexArray(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 3]; // 2 chars per byte + space
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i] & 0xFF; // Convert to unsigned
      hexChars[i * 3] = "0123456789abcdef".charAt(v >>> 4); // High nibble
      hexChars[i * 3 + 1] = "0123456789abcdef".charAt(v & 0x0F); // Low nibble
      hexChars[i * 3 + 2] = ' '; // Space separator
    }
    System.out.println("Received message: " + new String(hexChars).trim());
  }

  /**
   * Main method demonstrating server usage. Run this server first, then run the TCPClient to test.
   */
  public static void main(String[] args) {
    // Create server on port 5000
    TCPServer server = new TCPServer(5000);

    try {
      // Start the server
      server.start();

      // Add shutdown hook for graceful shutdown on Ctrl+C
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("\nShutdown signal received...");
        server.shutdown(10);
      }));

      // Keep server running
      System.out.println("\nServer is running. Press Ctrl+C to stop.\n");

      // Keep main thread alive
      while (server.isRunning()) {
        try {
          Thread.sleep(1000);

          // Optional: Print periodic status updates
          // Uncomment the following line if you want status updates every minute
          // if (System.currentTimeMillis() % 60000 < 1000) {
          //     System.out.println("[Status] Active: " + server.getActiveConnectionCount() +
          //                      ", Total: " + server.getTotalConnectionCount());
          // }

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }

    } catch (IOException e) {
      System.err.println("Failed to start server: " + e.getMessage());
      e.printStackTrace();
    }
  }
}