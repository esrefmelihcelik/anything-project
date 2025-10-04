package network.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A multi-threaded UDP server that receives data from clients.
 * <p>
 * Features: - Accepts multiple concurrent client messages - Thread pool for efficient message handling - Graceful shutdown
 * with resource cleanup - Message tracking - Comprehensive error handling
 */
public class UDPServer {

  // Server configuration
  private final int port;
  private DatagramSocket serverSocket;

  // Thread management
  private final ExecutorService messageThreadPool;
  private Thread receiveThread;

  // Server state
  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final AtomicInteger activeMessages = new AtomicInteger(0);
  private final AtomicInteger totalMessagesReceived = new AtomicInteger(0);

  // Thread pool configuration
  private static final int MAX_HANDLERS = 50;
  private static final int BUFFER_SIZE = 65507; // Max UDP packet size

  /**
   * Creates a new UDP server that listens on the specified port.
   *
   * @param port The port number to listen on
   */
  public UDPServer(int port) {
    this.port = port;

    // Create a fixed thread pool for handling client messages
    this.messageThreadPool = Executors.newFixedThreadPool(MAX_HANDLERS);
  }

  /**
   * Starts the UDP server and begins receiving client messages. This method returns immediately; the server runs on
   * background threads.
   *
   * @throws SocketException If the server cannot bind to the specified port
   */
  public void start() throws SocketException {
    if (isRunning.get()) {
      System.out.println("Server is already running.");
      return;
    }

    // Create datagram socket
    serverSocket = new DatagramSocket(port);
    serverSocket.setReuseAddress(true); // Allow port reuse

    isRunning.set(true);

    System.out.println("===========================================");
    System.out.println("UDP Server started on port " + port);
    System.out.println("Waiting for client messages...");
    System.out.println("===========================================");

    // Start the receive thread
    startReceiveThread();
  }

  /**
   * Starts the thread that receives incoming client messages. Each received message is handled by a separate thread from the
   * pool.
   */
  private void startReceiveThread() {
    receiveThread = new Thread(() -> {
      while (isRunning.get()) {
        try {
          // Prepare buffer for receiving data
          byte[] buffer = new byte[BUFFER_SIZE];
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

          // Receive incoming packet (blocking call)
          serverSocket.receive(packet);

          // Increment message counters
          int messageId = totalMessagesReceived.incrementAndGet();
          activeMessages.incrementAndGet();

          // Log message info
          String clientInfo = packet.getAddress().getHostAddress() + ":" + packet.getPort();
          System.out.println("\n[Message #" + messageId + "] New message from: " + clientInfo);
          System.out.println("[Status] Active handlers: " + activeMessages.get());

          // Handle message in a separate thread from the pool
          messageThreadPool.execute(new MessageHandler(packet, messageId));

        } catch (SocketException e) {
          // Socket closed during shutdown - this is expected
          if (isRunning.get()) {
            System.err.println("Socket error: " + e.getMessage());
          }
        } catch (IOException e) {
          if (isRunning.get()) {
            System.err.println("Error receiving message: " + e.getMessage());
          }
        }
      }
    }, "UDP-Receive-Thread");

    receiveThread.setDaemon(true);
    receiveThread.start();
  }

  /**
   * Gets the number of currently active message handlers.
   *
   * @return The number of active handlers
   */
  public int getActiveMessageCount() {
    return activeMessages.get();
  }

  /**
   * Gets the total number of messages received since server started.
   *
   * @return The total message count
   */
  public int getTotalMessageCount() {
    return totalMessagesReceived.get();
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
   * Gracefully shuts down the server. Waits for active handlers to complete (up to timeout).
   *
   * @param timeoutSeconds Maximum time to wait for handlers to close
   */
  public void shutdown(int timeoutSeconds) {
    if (!isRunning.get()) {
      System.out.println("Server is not running.");
      return;
    }

    System.out.println("\n===========================================");
    System.out.println("Initiating server shutdown...");
    System.out.println("===========================================");

    // Stop receiving new messages
    isRunning.set(false);

    // Close server socket to unblock receive()
    if (serverSocket != null && !serverSocket.isClosed()) {
      serverSocket.close();
    }

    // Shutdown thread pool gracefully
    messageThreadPool.shutdown();

    try {
      // Wait for existing message handlers to complete
      if (!messageThreadPool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
        System.out.println("Timeout reached. Forcing shutdown of message handlers...");
        messageThreadPool.shutdownNow();

        // Wait a bit more for forced shutdown
        if (!messageThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
          System.err.println("Thread pool did not terminate cleanly.");
        }
      }
    } catch (InterruptedException e) {
      messageThreadPool.shutdownNow();
      Thread.currentThread().interrupt();
    }

    // Wait for receive thread to finish
    if (receiveThread != null) {
      try {
        receiveThread.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    System.out.println("===========================================");
    System.out.println("Server shutdown complete.");
    System.out.println("Total messages received: " + totalMessagesReceived.get());
    System.out.println("===========================================");
  }

  /**
   * Shuts down the server immediately without waiting.
   */
  public void shutdown() {
    shutdown(10);
  }

  /**
   * Handles a single message from a client. Processes the received data.
   */
  private class MessageHandler implements Runnable {

    private final DatagramPacket packet;
    private final int messageId;

    /**
     * Creates a new message handler.
     *
     * @param packet    The received datagram packet
     * @param messageId Unique identifier for this message
     */
    public MessageHandler(DatagramPacket packet, int messageId) {
      this.packet = packet;
      this.messageId = messageId;
    }

    @Override
    public void run() {
      String clientInfo = packet.getAddress().getHostAddress() + ":" + packet.getPort();

      try {
        // Extract data from packet
        byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

        System.out.println("[Message #" + messageId + "] Processing message from " + clientInfo);

        // Print hex representation
        printHexArray(data);

        // Convert to string
        String message = new String(data, 0, data.length);

        // Process the received message
        handleReceivedObject(message, clientInfo);

      } catch (Exception e) {
        System.err.println("[Message #" + messageId + "] Error processing message: " + e.getMessage());
      } finally {
        // Update handler count
        int remaining = activeMessages.decrementAndGet();
        System.out.println("[Message #" + messageId + "] Handler terminated: " + clientInfo);
        System.out.println("[Status] Active handlers: " + remaining);
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
      System.out.println("│ Message ID: #" + messageId);
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
   * Main method demonstrating server usage. Run this server first, then run a UDP client to test.
   */
  public static void main(String[] args) {
    // Create server on port 5000
    UDPServer server = new UDPServer(5000);

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
          //     System.out.println("[Status] Active: " + server.getActiveMessageCount() +
          //                      ", Total: " + server.getTotalMessageCount());
          // }

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }

    } catch (SocketException e) {
      System.err.println("Failed to start server: " + e.getMessage());
      e.printStackTrace();
    }
  }
}