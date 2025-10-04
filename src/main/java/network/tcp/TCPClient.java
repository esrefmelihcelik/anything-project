package network.tcp;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A robust TCP client that automatically reconnects when connection is lost and can send serializable Java objects to a
 * remote server.
 * <p>
 * Features: - Automatic reconnection with exponential backoff - Thread-safe object sending - Connection state management -
 * Message queuing when disconnected - Graceful shutdown
 */
public class TCPClient {

  // Connection parameters
  private final String host;
  private final int port;

  // Network components
  private Socket socket;
  private OutputStream outputStream;

  // Connection state management
  private final AtomicBoolean isConnected = new AtomicBoolean(false);
  private final AtomicBoolean isRunning = new AtomicBoolean(true);
  private final ReentrantLock connectionLock = new ReentrantLock();

  // Reconnection configuration
  private static final int INITIAL_RECONNECT_DELAY_MS = 1000;
  private static final int MAX_RECONNECT_DELAY_MS = 30000;
  private static final double BACKOFF_MULTIPLIER = 2.0;
  private int currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS;

  // Message queue for objects to be sent
  private final BlockingQueue<byte[]> messageQueue = new LinkedBlockingQueue<>();

  // Worker threads
  private Thread connectionThread;
  private Thread senderThread;

  /**
   * Creates a new TCP client with the specified host and port.
   *
   * @param host The hostname or IP address to connect to
   * @param port The port number to connect to
   */
  public TCPClient(String host, int port) {
    this.host = host;
    this.port = port;

    // Start the connection management thread
    startConnectionThread();

    // Start the message sender thread
    startSenderThread();
  }

  /**
   * Starts the thread responsible for establishing and maintaining connection. This thread continuously monitors the
   * connection and attempts to reconnect if the connection is lost.
   */
  private void startConnectionThread() {
    connectionThread = new Thread(() -> {
      while (isRunning.get()) {
        if (!isConnected.get()) {
          attemptConnection();
        }

        // Check connection status periodically
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }, "TCP-Connection-Thread");

    connectionThread.setDaemon(true);
    connectionThread.start();
  }

  /**
   * Attempts to establish a connection to the remote server. Uses exponential backoff for reconnection attempts.
   */
  private void attemptConnection() {
    connectionLock.lock();
    try {
      // Don't attempt if already connected or shutting down
      if (isConnected.get() || !isRunning.get()) {
        return;
      }

      System.out.println("Attempting to connect to " + host + ":" + port + "...");

      try {
        // Create socket connection
        socket = new Socket(host, port);

        // Configure socket options for better performance and reliability
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true); // Disable Nagle's algorithm for lower latency
        socket.setSoTimeout(0); // Infinite timeout for blocking operations

        // Create object output stream for sending serializable objects
        outputStream = socket.getOutputStream();
        outputStream.flush(); // Flush header information

        // Mark as connected
        isConnected.set(true);
        currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS; // Reset backoff delay

        System.out.println("Successfully connected to " + host + ":" + port);

      } catch (IOException e) {
        System.err.println("Connection failed: " + e.getMessage());

        // Clean up any partially created resources
        closeConnection();

        // Apply exponential backoff before next retry
        System.out.println("Retrying in " + currentReconnectDelay + "ms...");
        try {
          Thread.sleep(currentReconnectDelay);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        }

        // Increase delay for next attempt (exponential backoff)
        currentReconnectDelay = Math.min(
            (int) (currentReconnectDelay * BACKOFF_MULTIPLIER),
            MAX_RECONNECT_DELAY_MS
        );
      }
    } finally {
      connectionLock.unlock();
    }
  }

  /**
   * Starts the thread responsible for sending queued messages. This thread processes the message queue and sends objects
   * when connected.
   */
  private void startSenderThread() {
    senderThread = new Thread(() -> {
      while (isRunning.get()) {
        try {
          byte[] message = messageQueue.take();

          if (message != null) {
            // Only attempt to send if connected
            if (isConnected.get()) {
              sendMessageInternal(message);
            } else {
              System.out.println("Not connected. Message discarded.");
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }, "TCP-Sender-Thread");

    senderThread.setDaemon(true);
    senderThread.start();
  }

  /**
   * Internal method to actually send a message over the network. Handles IOException and triggers reconnection if needed.
   *
   * @param message The serializable object to send
   */
  private void sendMessageInternal(byte[] message) {
    connectionLock.lock();
    try {
      if (!isConnected.get() || outputStream == null) {
        return;
      }

      // Send the object
      outputStream.write(message);
      outputStream.flush();

      System.out.println("Message sent successfully: " + message.getClass().getSimpleName());

    } catch (SocketException e) {
      // Connection lost during send
      System.err.println("Connection lost during send: " + e.getMessage());
      handleConnectionLoss();

    } catch (IOException e) {
      System.err.println("Error sending message: " + e.getMessage());
      handleConnectionLoss();

    } finally {
      connectionLock.unlock();
    }
  }

  /**
   * Public method to send a serializable object. The message is queued and will be sent when connection is available.
   *
   * @param message The serializable object to send
   * @return true if message was queued successfully, false otherwise
   */
  public boolean sendObject(byte[] message) {
    if (message == null) {
      System.err.println("Cannot send null message");
      return false;
    }

    if (!isRunning.get()) {
      System.err.println("Client is shutting down. Cannot send message.");
      return false;
    }

    // Add message to queue
    boolean queued = messageQueue.add(message);

    if (queued) {
      System.out.println("Message queued for sending: " + message.getClass().getSimpleName());
    } else {
      System.err.println("Failed to queue message. Queue may be full.");
    }

    return queued;
  }

  /**
   * Handles connection loss by cleaning up resources and marking as disconnected. This will trigger the connection thread to
   * attempt reconnection.
   */
  private void handleConnectionLoss() {
    isConnected.set(false);
    closeConnection();
    System.out.println("Connection lost. Will attempt to reconnect...");
  }

  /**
   * Closes the current connection and cleans up resources. This method is safe to call multiple times.
   */
  private void closeConnection() {
    // Close output stream
    if (outputStream != null) {
      try {
        outputStream.close();
      } catch (IOException e) {
        // Ignore errors during cleanup
      }
      outputStream = null;
    }

    // Close socket
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException e) {
        // Ignore errors during cleanup
      }
      socket = null;
    }
  }

  /**
   * Checks if the client is currently connected to the server.
   *
   * @return true if connected, false otherwise
   */
  public boolean isConnected() {
    return isConnected.get();
  }

  /**
   * Gets the number of messages currently queued for sending.
   *
   * @return The number of queued messages
   */
  public int getQueuedMessageCount() {
    return messageQueue.size();
  }

  /**
   * Gracefully shuts down the TCP client. Waits for queued messages to be sent before closing.
   *
   * @param timeoutSeconds Maximum time to wait for queued messages to be sent
   */
  public void shutdown(int timeoutSeconds) {
    System.out.println("Initiating shutdown...");

    // Stop accepting new messages and connections
    isRunning.set(false);

    // Wait for queued messages to be sent
    long startTime = System.currentTimeMillis();
    while (!messageQueue.isEmpty() &&
        (System.currentTimeMillis() - startTime) < timeoutSeconds * 1000L) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    // Interrupt worker threads
    if (senderThread != null) {
      senderThread.interrupt();
    }
    if (connectionThread != null) {
      connectionThread.interrupt();
    }

    // Close connection
    connectionLock.lock();
    try {
      isConnected.set(false);
      closeConnection();
    } finally {
      connectionLock.unlock();
    }

    System.out.println("Shutdown complete.");
  }

  /**
   * Gracefully shuts down the TCP client immediately.
   */
  public void shutdown() {
    shutdown(0);
  }

//  /**
//   * Example usage demonstrating the TCP client.
//   */
//  public static void main(String[] args) {
//    // Create a TCP client connecting to localhost:5000
//    TCPClient client = new TCPClient("localhost", 5000);
//
//    // Example: Send some test objects
//    try {
//      // Wait a bit for initial connection
//      Thread.sleep(2000);
//
//      // Send a string (String implements Serializable)
//      client.sendObject("Hello, Server!");
//
//      // Send a custom serializable object
//      client.sendObject(new TestMessage("Test", 42));
//
//      // Keep the application running for demonstration
//      Thread.sleep(10000);
//
//    } catch (InterruptedException e) {
//      Thread.currentThread().interrupt();
//    } finally {
//      // Shutdown gracefully
//      client.shutdown(5);
//    }
//  }

  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);

    System.out.print("Enter server IP: ");
    String ip = scanner.nextLine();

    System.out.print("Enter server port: ");
    int port = scanner.nextInt();
    scanner.nextLine(); // Consume newline

    TCPClient client = new TCPClient(ip, port);

    while (true) {
      System.out.print("Enter message (type 'exit' to quit): ");
      String message = scanner.nextLine();

      if ("exit".equalsIgnoreCase(message)) {
        break;
      }

      client.sendObject(message.getBytes(StandardCharsets.UTF_8));

    }

    scanner.close();
    System.out.println("Client disconnected.");

  }

  /**
   * Example serializable class for testing.
   */
  private static class TestMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String message;
    private final int value;

    public TestMessage(String message, int value) {
      this.message = message;
      this.value = value;
    }

    @Override
    public String toString() {
      return "TestMessage{message='" + message + "', value=" + value + "}";
    }
  }
}