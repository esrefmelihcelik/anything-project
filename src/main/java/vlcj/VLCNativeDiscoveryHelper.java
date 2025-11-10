package vlcj;

import java.io.File;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;

/**
 * VLC Native Library Discovery Helper
 * <p>
 * This helper class attempts to locate VLC native libraries across different operating systems and configurations. It
 * provides fallback strategies when automatic discovery fails.
 * <p>
 * Usage: Before creating any VLCJ components, call: VLCNativeDiscoveryHelper.discoverVLC();
 *
 * @author Video Player Application
 * @version 1.0
 */
public class VLCNativeDiscoveryHelper {

  private static boolean discovered = false;
  private static String discoveredPath = null;

  /**
   * Attempts to discover VLC native libraries
   *
   * @return true if VLC libraries were found, false otherwise
   */
  public static boolean discoverVLC() {
    if (discovered) {
      System.out.println("VLC already discovered at: " + discoveredPath);
      return true;
    }

    System.out.println("Attempting to discover VLC native libraries...");

    // Try standard VLCJ discovery first
    if (tryStandardDiscovery()) {
      return true;
    }

    // Try custom discovery strategies
    if (tryCustomDiscovery()) {
      return true;
    }

    // If all fails, print helpful error message
    printDiscoveryFailureHelp();
    return false;
  }

  /**
   * Attempts standard VLCJ native discovery
   *
   * @return true if successful
   */
  private static boolean tryStandardDiscovery() {
    try {
      boolean found = new NativeDiscovery().discover();
      if (found) {
        discovered = true;
        discoveredPath = "Standard Discovery";
        System.out.println("✓ VLC libraries found using standard discovery");
        return true;
      }
    } catch (Exception e) {
      System.err.println("Standard discovery failed: " + e.getMessage());
    }
    return false;
  }

  /**
   * Attempts custom discovery using platform-specific paths
   *
   * @return true if successful
   */
  private static boolean tryCustomDiscovery() {
    String os = System.getProperty("os.name").toLowerCase();

    if (os.contains("win")) {
      return tryWindowsDiscovery();
    } else if (os.contains("mac")) {
      return tryMacDiscovery();
    } else if (os.contains("nix") || os.contains("nux")) {
      return tryLinuxDiscovery();
    }

    return false;
  }

  /**
   * Windows-specific VLC discovery
   *
   * @return true if found
   */
  private static boolean tryWindowsDiscovery() {
    System.out.println("Attempting Windows-specific discovery...");

    String[] possiblePaths = {
        "C:\\Program Files\\VideoLAN\\VLC",
        "C:\\Program Files (x86)\\VideoLAN\\VLC",
        System.getenv("VLCJ_LIBRARY_PATH"),
        System.getenv("VLC_PLUGIN_PATH")
    };

    for (String path : possiblePaths) {
      if (path != null && checkVLCPath(path)) {
        setVLCPath(path);
        discovered = true;
        discoveredPath = path;
        System.out.println("✓ VLC found at: " + path);
        return true;
      }
    }

    return false;
  }

  /**
   * macOS-specific VLC discovery
   *
   * @return true if found
   */
  private static boolean tryMacDiscovery() {
    System.out.println("Attempting macOS-specific discovery...");

    String[] possiblePaths = {
        "/Applications/VLC.app/Contents/MacOS/lib",
        "/Applications/VLC.app/Contents/MacOS",
        System.getProperty("user.home") + "/Applications/VLC.app/Contents/MacOS/lib",
        "/usr/local/lib"
    };

    for (String path : possiblePaths) {
      if (checkVLCPath(path)) {
        setVLCPath(path);
        discovered = true;
        discoveredPath = path;
        System.out.println("✓ VLC found at: " + path);
        return true;
      }
    }

    return false;
  }

  /**
   * Linux-specific VLC discovery
   *
   * @return true if found
   */
  private static boolean tryLinuxDiscovery() {
    System.out.println("Attempting Linux-specific discovery...");

    String[] possiblePaths = {
        "/usr/lib/vlc",
        "/usr/lib64/vlc",
        "/usr/local/lib/vlc",
        "/usr/lib/x86_64-linux-gnu",
        "/usr/lib/i386-linux-gnu"
    };

    for (String path : possiblePaths) {
      if (checkVLCPath(path)) {
        setVLCPath(path);
        discovered = true;
        discoveredPath = path;
        System.out.println("✓ VLC found at: " + path);
        return true;
      }
    }

    return false;
  }

  /**
   * Checks if VLC libraries exist at given path
   *
   * @param path Path to check
   * @return true if VLC libraries found
   */
  private static boolean checkVLCPath(String path) {
    if (path == null || path.trim().isEmpty()) {
      return false;
    }

    File dir = new File(path);
    if (!dir.exists() || !dir.isDirectory()) {
      return false;
    }

    // Check for libvlc library file
    String os = System.getProperty("os.name").toLowerCase();
    String[] libNames;

    if (os.contains("win")) {
      libNames = new String[]{"libvlc.dll", "libvlccore.dll"};
    } else if (os.contains("mac")) {
      libNames = new String[]{"libvlc.dylib", "libvlccore.dylib"};
    } else {
      libNames = new String[]{"libvlc.so", "libvlccore.so", "libvlc.so.5"};
    }

    // Check if at least one library file exists
    for (String libName : libNames) {
      File libFile = new File(dir, libName);
      if (libFile.exists()) {
        return true;
      }
    }

    return false;
  }

  /**
   * Sets the VLC library path in system properties
   *
   * @param path Path to VLC libraries
   */
  private static void setVLCPath(String path) {
    String os = System.getProperty("os.name").toLowerCase();

    // Set appropriate library path variable
    if (os.contains("win")) {
      // Windows uses PATH
      String currentPath = System.getenv("PATH");
      if (currentPath != null) {
        System.setProperty("jna.library.path", path + File.pathSeparator + currentPath);
      } else {
        System.setProperty("jna.library.path", path);
      }
    } else if (os.contains("mac")) {
      // macOS uses DYLD_LIBRARY_PATH
      System.setProperty("jna.library.path", path);
      System.setProperty("java.library.path", path);
    } else {
      // Linux uses LD_LIBRARY_PATH
      System.setProperty("jna.library.path", path);
      System.setProperty("java.library.path", path);
    }

    System.out.println("Set library path to: " + path);
  }

  /**
   * Prints helpful information when VLC discovery fails
   */
  private static void printDiscoveryFailureHelp() {
    System.err.println("\n" + "=".repeat(70));
    System.err.println("ERROR: Could not find VLC native libraries!");
    System.err.println("=".repeat(70));

    String os = System.getProperty("os.name").toLowerCase();

    if (os.contains("win")) {
      System.err.println("\nFor Windows:");
      System.err.println("1. Download VLC 3.0.18 from: https://www.videolan.org/vlc/");
      System.err.println("2. Install to: C:\\Program Files\\VideoLAN\\VLC");
      System.err.println("3. Add to system PATH or set environment variable:");
      System.err.println("   set VLCJ_LIBRARY_PATH=C:\\Program Files\\VideoLAN\\VLC");
    } else if (os.contains("mac")) {
      System.err.println("\nFor macOS:");
      System.err.println("1. Download VLC 3.0.18 from: https://www.videolan.org/vlc/");
      System.err.println("2. Install to: /Applications/VLC.app");
      System.err.println("3. Set environment variable:");
      System.err.println("   export DYLD_LIBRARY_PATH=/Applications/VLC.app/Contents/MacOS/lib");
    } else {
      System.err.println("\nFor Linux:");
      System.err.println("1. Install VLC using package manager:");
      System.err.println("   sudo apt-get install vlc libvlc-dev  # Debian/Ubuntu");
      System.err.println("   sudo yum install vlc vlc-devel       # RedHat/CentOS");
      System.err.println("2. Verify installation:");
      System.err.println("   which vlc");
      System.err.println("   ldconfig -p | grep libvlc");
    }

    System.err.println("\nCurrent Java library path:");
    System.err.println("  " + System.getProperty("java.library.path"));
    System.err.println("\n" + "=".repeat(70) + "\n");
  }

  /**
   * Gets the discovered VLC path
   *
   * @return Path where VLC was found, or null if not discovered
   */
  public static String getDiscoveredPath() {
    return discoveredPath;
  }

  /**
   * Checks if VLC has been successfully discovered
   *
   * @return true if discovered
   */
  public static boolean isDiscovered() {
    return discovered;
  }

  /**
   * Main method for testing VLC discovery independently
   *
   * @param args Command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("VLC Native Library Discovery Test");
    System.out.println("==================================\n");

    System.out.println("Operating System: " + System.getProperty("os.name"));
    System.out.println("Java Version: " + System.getProperty("java.version"));
    System.out.println("Architecture: " + System.getProperty("os.arch"));
    System.out.println();

    boolean found = discoverVLC();

    System.out.println("\n==================================");
    if (found) {
      System.out.println("SUCCESS: VLC libraries discovered!");
      System.out.println("Path: " + getDiscoveredPath());
    } else {
      System.out.println("FAILURE: Could not discover VLC libraries");
      System.out.println("Please follow the instructions above");
    }
    System.out.println("==================================\n");
  }
}