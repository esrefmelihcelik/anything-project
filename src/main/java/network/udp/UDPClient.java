package network.udp;

import java.io.IOException;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A robust UDP client that can send data to a remote server.
 * <p>
 * Features: - Thread-safe message sending - Message queuing - Graceful shutdown
 * Note: UDP is connectionless, so there's no connection state management or reconnection logic.
 */
public class UDPClient {

  // Connection parameters
  private final String host;
  private final int port;
  private InetAddress address;

  // Network components
  private DatagramSocket socket;

  // State management
  private final AtomicBoolean isRunning = new AtomicBoolean(true);
  private final ReentrantLock socketLock = new ReentrantLock();

  // Message queue for data to be sent
  private final BlockingQueue<byte[]> messageQueue = new LinkedBlockingQueue<>();

  // Worker thread
  private Thread senderThread;

  /**
   * Creates a new UDP client with the specified host and port.
   *
   * @param host The hostname or IP address to send to
   * @param port The port number to send to
   */
  public UDPClient(String host, int port) {
    this.host = host;
    this.port = port;

    // Initialize socket and address
    initializeSocket();

    // Start the message sender thread
    startSenderThread();
  }

  /**
   * Initializes the UDP socket and resolves the host address.
   */
  private void initializeSocket() {
    socketLock.lock();
    try {
      System.out.println("Initializing UDP client for " + host + ":" + port + "...");

      try {
        // Resolve host address
        address = InetAddress.getByName(host);

        // Create UDP socket
        socket = new DatagramSocket();

        System.out.println("UDP client initialized successfully");

      } catch (IOException e) {
        System.err.println("Failed to initialize UDP client: " + e.getMessage());
        throw new RuntimeException("Failed to initialize UDP client", e);
      }
    } finally {
      socketLock.unlock();
    }
  }

  /**
   * Starts the thread responsible for sending queued messages.
   */
  private void startSenderThread() {
    senderThread = new Thread(() -> {
      while (isRunning.get()) {
        try {
          byte[] message = messageQueue.take();

          if (message != null) {
            sendMessageInternal(message);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }, "UDP-Sender-Thread");

    senderThread.setDaemon(true);
    senderThread.start();
  }

  /**
   * Internal method to actually send a message over the network.
   *
   * @param message The byte array to send
   */
  private void sendMessageInternal(byte[] message) {
    socketLock.lock();
    try {
      if (socket == null || socket.isClosed()) {
        System.err.println("Socket is closed. Cannot send message.");
        return;
      }

      // Create datagram packet
      DatagramPacket packet = new DatagramPacket(message, message.length, address, port);

      // Send the packet
      socket.send(packet);

      System.out.println("Message sent successfully (" + message.length + " bytes)");

    } catch (IOException e) {
      System.err.println("Error sending message: " + e.getMessage());
    } finally {
      socketLock.unlock();
    }
  }

  /**
   * Public method to send a byte array. The message is queued and will be sent asynchronously.
   *
   * @param message The byte array to send
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
      System.out.println("Message queued for sending (" + message.length + " bytes)");
    } else {
      System.err.println("Failed to queue message. Queue may be full.");
    }

    return queued;
  }

  /**
   * Checks if the client is running.
   *
   * @return true if running, false otherwise
   */
  public boolean isConnected() {
    return isRunning.get() && socket != null && !socket.isClosed();
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
   * Gracefully shuts down the UDP client. Waits for queued messages to be sent before closing.
   *
   * @param timeoutSeconds Maximum time to wait for queued messages to be sent
   */
  public void shutdown(int timeoutSeconds) {
    System.out.println("Initiating shutdown...");

    // Stop accepting new messages
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

    // Interrupt worker thread
    if (senderThread != null) {
      senderThread.interrupt();
    }

    // Close socket
    socketLock.lock();
    try {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
    } finally {
      socketLock.unlock();
    }

    System.out.println("Shutdown complete.");
  }

  /**
   * Gracefully shuts down the UDP client immediately.
   */
  public void shutdown() {
    shutdown(0);
  }

  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);

    System.out.print("Enter server IP: ");
    String ip = scanner.nextLine();

    System.out.print("Enter server port: ");
    int port = scanner.nextInt();
    scanner.nextLine(); // Consume newline

    UDPClient client = new UDPClient(ip, port);

    while (true) {
      System.out.print("Enter message (type 'exit' to quit): ");
      String message = scanner.nextLine();

      if ("exit".equalsIgnoreCase(message)) {
        break;
      }

      client.sendObject(message.getBytes(StandardCharsets.UTF_8));
    }

    scanner.close();
    client.shutdown(2);
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