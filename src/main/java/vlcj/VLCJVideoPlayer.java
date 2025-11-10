package vlcj;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import uk.co.caprica.vlcj.media.MediaRef;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaListPlayerComponent;
import uk.co.caprica.vlcj.player.list.MediaListPlayer;
import uk.co.caprica.vlcj.player.list.MediaListPlayerEventAdapter;

/**
 * Advanced Video Player Application using VLCJ 4.7.1
 * <p>
 * This application provides a modern, user-friendly interface for playing video files with support for multiple formats,
 * playlist management, and comprehensive playback controls.
 * <p>
 * Key Features: - Fixed 1920x1080 window size with aspect ratio preservation - Multiple format support (MP4, AVI, MKV, MOV,
 * etc.) - Playlist management with side panel - Speed control (1x, 2x, 4x, 8x) - Time navigation slider - Auto-play next
 * feature
 * <p>
 * Requirements: - Java 8+ - VLCJ 4.7.1 - VLC 3.0.18 (Vetinari)
 *
 * @author Video Player Application
 * @version 1.0
 */
public class VLCJVideoPlayer extends JFrame {

  // Constants
  private static final int WINDOW_WIDTH = 1920;
  private static final int WINDOW_HEIGHT = 1080;
  private static final int SIDEBAR_WIDTH = 300;
  private static final int CONTROL_PANEL_HEIGHT = 150;

  // UI Components
  private EmbeddedMediaListPlayerComponent mediaPlayerComponent;
  private JButton playButton;
  private JButton pauseButton;
  private JButton stopButton;
  private JButton nextButton;
  private JButton previousButton;
  private JButton loadFilesButton;
  private JButton clearButton;
  private JSlider timeSlider;
  private JComboBox<String> speedComboBox;
  private JLabel timeLabel;
  private JLabel statusLabel;
  private DefaultListModel<MediaFile> playlistModel;
  private JList<MediaFile> playlistJList;

  // Media management
  private List<String> mediaFiles;
  private int currentMediaIndex;
  private boolean isPlaying;
  private boolean isPaused;
  private boolean isSliderAdjusting;

  // Scheduled executor for UI updates
  private ScheduledExecutorService executorService;

  /**
   * Constructor - Initializes the video player application
   */
  public VLCJVideoPlayer() {
    super("VLCJ Video Player - Modern Media Center");

    // Initialize data structures
    mediaFiles = new ArrayList<>();
    currentMediaIndex = -1;
    isPlaying = false;
    isPaused = false;
    isSliderAdjusting = false;

    // Initialize executor service for periodic updates
    executorService = Executors.newSingleThreadScheduledExecutor();

    // Setup UI
    initializeUI();

    // Setup media player events
    setupMediaPlayerEvents();

    // Start UI update timer
    startUIUpdateTimer();

    // Window close handler
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        cleanup();
        System.exit(0);
      }
    });
  }

  /**
   * Initializes the user interface components
   */
  private void initializeUI() {
    // Set look and feel for modern appearance
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      System.err.println("Failed to set look and feel: " + e.getMessage());
    }

    // Configure main frame
    setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    setResizable(false);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);

    // Main layout
    setLayout(new BorderLayout(5, 5));

    // Create media player component
    mediaPlayerComponent = new EmbeddedMediaListPlayerComponent();

    // Create video panel with black background
    JPanel videoPanel = new JPanel(new BorderLayout());
    videoPanel.setBackground(Color.BLACK);
    videoPanel.add(mediaPlayerComponent, BorderLayout.CENTER);
    videoPanel.setPreferredSize(new Dimension(WINDOW_WIDTH - SIDEBAR_WIDTH,
        WINDOW_HEIGHT - CONTROL_PANEL_HEIGHT));

    // Create control panel
    JPanel controlPanel = createControlPanel();

    // Create sidebar panel
    JPanel sidebarPanel = createSidebarPanel();

    // Add components to main frame
    add(videoPanel, BorderLayout.CENTER);
    add(controlPanel, BorderLayout.SOUTH);
    add(sidebarPanel, BorderLayout.EAST);

    // Initial button states
    updateButtonStates();
  }

  /**
   * Creates the control panel with playback controls
   *
   * @return JPanel containing all playback controls
   */
  private JPanel createControlPanel() {
    JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
    controlPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    controlPanel.setPreferredSize(new Dimension(WINDOW_WIDTH, CONTROL_PANEL_HEIGHT));
    controlPanel.setBackground(new Color(45, 45, 45));

    // Time slider panel
    JPanel sliderPanel = new JPanel(new BorderLayout(10, 0));
    sliderPanel.setBackground(new Color(45, 45, 45));

    timeSlider = new JSlider(0, 100, 0);
    timeSlider.setBackground(new Color(45, 45, 45));
    timeSlider.setForeground(Color.WHITE);
    timeSlider.addChangeListener(e -> {
      if (timeSlider.getValueIsAdjusting()) {
        isSliderAdjusting = true;
      } else if (isSliderAdjusting) {
        isSliderAdjusting = false;
        seekToPosition();
      }
    });

    timeLabel = new JLabel("00:00:00 / 00:00:00");
    timeLabel.setForeground(Color.WHITE);
    timeLabel.setFont(new Font("Arial", Font.BOLD, 12));

    sliderPanel.add(timeLabel, BorderLayout.WEST);
    sliderPanel.add(timeSlider, BorderLayout.CENTER);

    // Button panel
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
    buttonPanel.setBackground(new Color(45, 45, 45));

    // Create buttons with modern styling
    previousButton = createStyledButton("Previous", new Color(70, 130, 180));
    playButton = createStyledButton("Play", new Color(34, 139, 34));
    pauseButton = createStyledButton("Pause", new Color(255, 165, 0));
    stopButton = createStyledButton("Stop", new Color(220, 20, 60));
    nextButton = createStyledButton("Next", new Color(70, 130, 180));

    // Speed control
    JLabel speedLabel = new JLabel("Speed:");
    speedLabel.setForeground(Color.WHITE);
    speedLabel.setFont(new Font("Arial", Font.BOLD, 12));

    speedComboBox = new JComboBox<>(new String[]{"1x", "2x", "4x", "8x"});
    speedComboBox.setPreferredSize(new Dimension(80, 30));
    speedComboBox.addActionListener(e -> changePlaybackSpeed());

    // Add action listeners
    playButton.addActionListener(e -> playMedia());
    pauseButton.addActionListener(e -> pauseMedia());
    stopButton.addActionListener(e -> stopMedia());
    nextButton.addActionListener(e -> playNext());
    previousButton.addActionListener(e -> playPrevious());

    // Add buttons to panel
    buttonPanel.add(previousButton);
    buttonPanel.add(playButton);
    buttonPanel.add(pauseButton);
    buttonPanel.add(stopButton);
    buttonPanel.add(nextButton);
    buttonPanel.add(Box.createHorizontalStrut(20));
    buttonPanel.add(speedLabel);
    buttonPanel.add(speedComboBox);

    // Status panel
    JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    statusPanel.setBackground(new Color(45, 45, 45));

    statusLabel = new JLabel("Ready");
    statusLabel.setForeground(Color.WHITE);
    statusLabel.setFont(new Font("Arial", Font.PLAIN, 11));
    statusPanel.add(statusLabel);

    // Add all sub-panels to control panel
    controlPanel.add(sliderPanel);
    controlPanel.add(Box.createVerticalStrut(5));
    controlPanel.add(buttonPanel);
    controlPanel.add(statusPanel);

    return controlPanel;
  }

  /**
   * Creates a styled button with modern appearance
   *
   * @param text  Button text
   * @param color Button background color
   * @return Styled JButton
   */
  private JButton createStyledButton(String text, Color color) {
    JButton button = new JButton(text);
    button.setPreferredSize(new Dimension(120, 35));
    button.setBackground(color);
    button.setForeground(Color.WHITE);
    button.setFocusPainted(false);
    button.setBorderPainted(false);
    button.setFont(new Font("Arial", Font.BOLD, 12));
    button.setCursor(new Cursor(Cursor.HAND_CURSOR));

    // Hover effect
    button.addMouseListener(new java.awt.event.MouseAdapter() {
      public void mouseEntered(java.awt.event.MouseEvent evt) {
        button.setBackground(color.brighter());
      }

      public void mouseExited(java.awt.event.MouseEvent evt) {
        button.setBackground(color);
      }
    });

    return button;
  }

  /**
   * Creates the sidebar panel with playlist management
   *
   * @return JPanel containing playlist and file management controls
   */
  private JPanel createSidebarPanel() {
    JPanel sidebarPanel = new JPanel(new BorderLayout(5, 5));
    sidebarPanel.setPreferredSize(new Dimension(SIDEBAR_WIDTH, WINDOW_HEIGHT));
    sidebarPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    sidebarPanel.setBackground(new Color(60, 60, 60));

    // Title
    JLabel titleLabel = new JLabel("Playlist", SwingConstants.CENTER);
    titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
    titleLabel.setForeground(Color.WHITE);
    titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

    // Playlist
    playlistModel = new DefaultListModel<>();
    playlistJList = new JList<>(playlistModel);
    playlistJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    playlistJList.setBackground(new Color(40, 40, 40));
    playlistJList.setForeground(Color.WHITE);
    playlistJList.setFont(new Font("Arial", Font.PLAIN, 12));
    playlistJList.setCellRenderer(new PlaylistCellRenderer());

    // Double-click to play
    playlistJList.addMouseListener(new java.awt.event.MouseAdapter() {
      public void mouseClicked(java.awt.event.MouseEvent evt) {
        if (evt.getClickCount() == 2) {
          int index = playlistJList.locationToIndex(evt.getPoint());
          if (index >= 0) {
            playMediaAtIndex(index);
          }
        }
      }
    });

    JScrollPane scrollPane = new JScrollPane(playlistJList);
    scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));

    // Button panel
    JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
    buttonPanel.setBackground(new Color(60, 60, 60));

    loadFilesButton = createStyledButton("Load Files", new Color(100, 100, 150));
    loadFilesButton.addActionListener(e -> loadMediaFiles());

    clearButton = createStyledButton("Clear All", new Color(150, 50, 50));
    clearButton.addActionListener(e -> clearPlaylist());

    buttonPanel.add(loadFilesButton);
    buttonPanel.add(clearButton);

    // Add components
    sidebarPanel.add(titleLabel, BorderLayout.NORTH);
    sidebarPanel.add(scrollPane, BorderLayout.CENTER);
    sidebarPanel.add(buttonPanel, BorderLayout.SOUTH);

    return sidebarPanel;
  }

  /**
   * Sets up event listeners for the media player
   */
  private void setupMediaPlayerEvents() {
    MediaListPlayer mediaListPlayer = mediaPlayerComponent.mediaListPlayer();

    // Media list player events
    mediaListPlayer.events().addMediaListPlayerEventListener(new MediaListPlayerEventAdapter() {
      @Override
      public void nextItem(MediaListPlayer mediaListPlayer, MediaRef nextItem) {
        SwingUtilities.invokeLater(() -> {
          currentMediaIndex++;
          if (currentMediaIndex < mediaFiles.size()) {
            updatePlaylistSelection();
            updateStatus("Playing: " + getFileName(mediaFiles.get(currentMediaIndex)));
          }
        });
      }
    });

    // Media player events
    MediaPlayer mediaPlayer = mediaPlayerComponent.mediaPlayer();
    mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
      @Override
      public void playing(MediaPlayer mediaPlayer) {
        SwingUtilities.invokeLater(() -> {
          isPlaying = true;
          isPaused = false;
          updateButtonStates();
          updateStatus("Playing...");
        });
      }

      @Override
      public void paused(MediaPlayer mediaPlayer) {
        SwingUtilities.invokeLater(() -> {
          isPaused = true;
          updateButtonStates();
          updateStatus("Paused");
        });
      }

      @Override
      public void stopped(MediaPlayer mediaPlayer) {
        SwingUtilities.invokeLater(() -> {
          isPlaying = false;
          isPaused = false;
          updateButtonStates();
          updateStatus("Stopped");
        });
      }

      @Override
      public void finished(MediaPlayer mediaPlayer) {
        SwingUtilities.invokeLater(() -> {
          // Auto-play next if available
          if (currentMediaIndex < mediaFiles.size() - 1) {
            playNext();
          } else {
            stopMedia();
            updateStatus("Playlist finished");
          }
        });
      }

      @Override
      public void error(MediaPlayer mediaPlayer) {
        SwingUtilities.invokeLater(() -> {
          updateStatus("Error playing media");
          JOptionPane.showMessageDialog(VLCJVideoPlayer.this,
              "Error playing media file. The file may be corrupted or in an unsupported format.",
              "Playback Error",
              JOptionPane.ERROR_MESSAGE);
        });
      }
    });
  }

  /**
   * Starts a timer to periodically update UI elements (time slider and label)
   */
  private void startUIUpdateTimer() {
    executorService.scheduleAtFixedRate(() -> {
      if (isPlaying && !isSliderAdjusting) {
        SwingUtilities.invokeLater(() -> updateTimeControls());
      }
    }, 0, 500, TimeUnit.MILLISECONDS);
  }

  /**
   * Updates time slider and time label based on current playback position
   */
  private void updateTimeControls() {
    try {
      MediaPlayer mediaPlayer = mediaPlayerComponent.mediaPlayer();
      long time = mediaPlayer.status().time();
      long length = mediaPlayer.status().length();

      if (length > 0) {
        int position = (int) ((time * 100) / length);
        timeSlider.setValue(position);

        String currentTime = formatTime(time);
        String totalTime = formatTime(length);
        timeLabel.setText(currentTime + " / " + totalTime);
      }
    } catch (Exception e) {
      // Silently handle exceptions during UI update
      System.err.println("Error updating time controls: " + e.getMessage());
    }
  }

  /**
   * Formats time in milliseconds to HH:MM:SS format
   *
   * @param milliseconds Time in milliseconds
   * @return Formatted time string
   */
  private String formatTime(long milliseconds) {
    long seconds = milliseconds / 1000;
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;
    long secs = seconds % 60;
    return String.format("%02d:%02d:%02d", hours, minutes, secs);
  }

  /**
   * Seeks to the position indicated by the time slider
   */
  private void seekToPosition() {
    try {
      MediaPlayer mediaPlayer = mediaPlayerComponent.mediaPlayer();
      float position = timeSlider.getValue() / 100.0f;
      mediaPlayer.controls().setPosition(position);
    } catch (Exception e) {
      System.err.println("Error seeking position: " + e.getMessage());
      updateStatus("Error seeking to position");
    }
  }

  /**
   * Opens file chooser dialog to load media files
   */
  private void loadMediaFiles() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setMultiSelectionEnabled(true);
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

    // Add file filter for common video formats
    FileNameExtensionFilter filter = new FileNameExtensionFilter(
        "Video Files", "mp4", "avi", "mkv", "mov", "flv", "wmv", "m4v", "mpg", "mpeg", "3gp", "webm"
    );
    fileChooser.setFileFilter(filter);

    int result = fileChooser.showOpenDialog(this);

    if (result == JFileChooser.APPROVE_OPTION) {
      File[] selectedFiles = fileChooser.getSelectedFiles();

      if (selectedFiles != null && selectedFiles.length > 0) {
        for (File file : selectedFiles) {
          // Validate file exists and is readable
          if (file.exists() && file.canRead()) {
            String filePath = file.getAbsolutePath();
            mediaFiles.add(filePath);
            playlistModel.addElement(new MediaFile(filePath, file.getName()));
          } else {
            JOptionPane.showMessageDialog(this,
                "Cannot read file: " + file.getName(),
                "File Error",
                JOptionPane.WARNING_MESSAGE);
          }
        }

        updateStatus("Loaded " + selectedFiles.length + " file(s)");
        updateButtonStates();
      }
    }
  }

  /**
   * Clears all media files from the playlist
   */
  private void clearPlaylist() {
    if (mediaFiles.isEmpty()) {
      return;
    }

    int confirm = JOptionPane.showConfirmDialog(this,
        "Are you sure you want to clear the playlist?",
        "Clear Playlist",
        JOptionPane.YES_NO_OPTION);

    if (confirm == JOptionPane.YES_OPTION) {
      stopMedia();
      mediaFiles.clear();
      playlistModel.clear();
      currentMediaIndex = -1;
      timeSlider.setValue(0);
      timeLabel.setText("00:00:00 / 00:00:00");
      updateStatus("Playlist cleared");
      updateButtonStates();
    }
  }

  /**
   * Plays the currently selected or first media in the playlist
   */
  private void playMedia() {
    if (mediaFiles.isEmpty()) {
      updateStatus("No media files loaded");
      JOptionPane.showMessageDialog(this,
          "Please load media files first.",
          "No Media",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    try {
      MediaPlayer mediaPlayer = mediaPlayerComponent.mediaPlayer();

      // If paused, resume playback
      if (isPaused) {
        mediaPlayer.controls().setPause(false);
        return;
      }

      // If no media is selected, play the first one
      if (currentMediaIndex < 0) {
        playMediaAtIndex(0);
      } else {
        // Resume or start current media
        if (!isPlaying) {
          mediaPlayer.controls().play();
        }
      }
    } catch (Exception e) {
      handlePlaybackError(e);
    }
  }

  /**
   * Plays media file at specified index
   *
   * @param index Index in the playlist
   */
  private void playMediaAtIndex(int index) {
    if (index < 0 || index >= mediaFiles.size()) {
      return;
    }

    try {
      currentMediaIndex = index;
      String mediaPath = mediaFiles.get(index);

      // Stop current playback
      MediaPlayer mediaPlayer = mediaPlayerComponent.mediaPlayer();
      mediaPlayer.controls().stop();

      // Play new media with aspect ratio preservation
      boolean started = mediaPlayer.media().play(mediaPath,
          ":aspect-ratio=16:9",
          ":video-filter=transform"
      );

      if (started) {
        updatePlaylistSelection();
        updateStatus("Playing: " + getFileName(mediaPath));
      } else {
        throw new Exception("Failed to start playback");
      }
    } catch (Exception e) {
      handlePlaybackError(e);
    }
  }

  /**
   * Pauses the currently playing media
   */
  private void pauseMedia() {
    if (!isPlaying) {
      return;
    }

    try {
      MediaPlayer mediaPlayer = mediaPlayerComponent.mediaPlayer();
      mediaPlayer.controls().setPause(true);
    } catch (Exception e) {
      System.err.println("Error pausing media: " + e.getMessage());
      updateStatus("Error pausing media");
    }
  }

  /**
   * Stops the currently playing media
   */
  private void stopMedia() {
    try {
      MediaPlayer mediaPlayer = mediaPlayerComponent.mediaPlayer();
      mediaPlayer.controls().stop();
      timeSlider.setValue(0);
      timeLabel.setText("00:00:00 / 00:00:00");
    } catch (Exception e) {
      System.err.println("Error stopping media: " + e.getMessage());
    }
  }

  /**
   * Plays the next media file in the playlist
   */
  private void playNext() {
    if (mediaFiles.isEmpty()) {
      return;
    }

    if (currentMediaIndex < mediaFiles.size() - 1) {
      playMediaAtIndex(currentMediaIndex + 1);
    } else {
      updateStatus("Already at last media");
    }
  }

  /**
   * Plays the previous media file in the playlist
   */
  private void playPrevious() {
    if (mediaFiles.isEmpty()) {
      return;
    }

    if (currentMediaIndex > 0) {
      playMediaAtIndex(currentMediaIndex - 1);
    } else {
      updateStatus("Already at first media");
    }
  }

  /**
   * Changes playback speed based on combo box selection
   */
  private void changePlaybackSpeed() {
    if (!isPlaying) {
      return;
    }

    try {
      String selectedSpeed = (String) speedComboBox.getSelectedItem();
      float rate = 1.0f;

      switch (selectedSpeed) {
        case "1x":
          rate = 1.0f;
          break;
        case "2x":
          rate = 2.0f;
          break;
        case "4x":
          rate = 4.0f;
          break;
        case "8x":
          rate = 8.0f;
          break;
      }

      MediaPlayer mediaPlayer = mediaPlayerComponent.mediaPlayer();
      mediaPlayer.controls().setRate(rate);
      updateStatus("Playback speed: " + selectedSpeed);
    } catch (Exception e) {
      System.err.println("Error changing playback speed: " + e.getMessage());
      updateStatus("Error changing speed");
    }
  }

  /**
   * Updates button enabled/disabled states based on application state
   */
  private void updateButtonStates() {
    boolean hasMedia = !mediaFiles.isEmpty();

    playButton.setEnabled(hasMedia && (!isPlaying || isPaused));
    pauseButton.setEnabled(isPlaying && !isPaused);
    stopButton.setEnabled(isPlaying || isPaused);
    nextButton.setEnabled(hasMedia && currentMediaIndex < mediaFiles.size() - 1);
    previousButton.setEnabled(hasMedia && currentMediaIndex > 0);
    clearButton.setEnabled(hasMedia);
  }

  /**
   * Updates playlist selection to highlight currently playing media
   */
  private void updatePlaylistSelection() {
    if (currentMediaIndex >= 0 && currentMediaIndex < playlistModel.size()) {
      playlistJList.setSelectedIndex(currentMediaIndex);
      playlistJList.ensureIndexIsVisible(currentMediaIndex);
    }
  }

  /**
   * Updates status label with provided message
   *
   * @param message Status message to display
   */
  private void updateStatus(String message) {
    statusLabel.setText(message);
  }

  /**
   * Extracts filename from full path
   *
   * @param path Full file path
   * @return Filename without path
   */
  private String getFileName(String path) {
    return new File(path).getName();
  }

  /**
   * Handles playback errors with user feedback
   *
   * @param e Exception that occurred
   */
  private void handlePlaybackError(Exception e) {
    System.err.println("Playback error: " + e.getMessage());
    e.printStackTrace();

    updateStatus("Error: " + e.getMessage());

    JOptionPane.showMessageDialog(this,
        "Failed to play media:\n" + e.getMessage() +
            "\n\nPlease ensure:\n" +
            "- VLC is properly installed\n" +
            "- File format is supported\n" +
            "- File is not corrupted",
        "Playback Error",
        JOptionPane.ERROR_MESSAGE);
  }

  /**
   * Cleans up resources before application exit
   */
  private void cleanup() {
    try {
      // Shutdown executor service
      if (executorService != null && !executorService.isShutdown()) {
        executorService.shutdown();
        executorService.awaitTermination(2, TimeUnit.SECONDS);
      }

      // Release media player resources
      if (mediaPlayerComponent != null) {
        mediaPlayerComponent.mediaPlayer().controls().stop();
        mediaPlayerComponent.release();
      }
    } catch (Exception e) {
      System.err.println("Error during cleanup: " + e.getMessage());
    }
  }

  /**
   * Inner class representing a media file in the playlist
   */
  private static class MediaFile {

    private final String path;
    private final String name;

    public MediaFile(String path, String name) {
      this.path = path;
      this.name = name;
    }

    public String getPath() {
      return path;
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /**
   * Custom cell renderer for playlist with modern styling
   */
  private class PlaylistCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
        int index, boolean isSelected, boolean cellHasFocus) {
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      label.setBorder(new EmptyBorder(5, 10, 5, 10));

      if (isSelected) {
        label.setBackground(new Color(70, 130, 180));
        label.setForeground(Color.WHITE);
      } else {
        label.setBackground(new Color(40, 40, 40));
        label.setForeground(Color.WHITE);
      }

      // Highlight currently playing media
      if (index == currentMediaIndex && isPlaying) {
        label.setText(value.toString());
        label.setFont(label.getFont().deriveFont(Font.BOLD));
      }

      return label;
    }
  }

  /**
   * Main method - Application entry point
   *
   * @param args Command line arguments (not used)
   */
  public static void main(String[] args) {
    // First, attempt to discover VLC native libraries
    System.out.println("VLC Video Player Application Starting...");
    System.out.println("=========================================\n");

    // Use VLCNativeDiscoveryHelper for better cross-platform support
    // If you have the helper class, uncomment the following lines:
        /*
        if (!VLCNativeDiscoveryHelper.discoverVLC()) {
            JOptionPane.showMessageDialog(null,
                "VLC Media Player not found!\n\n" +
                "Please install VLC 3.0.18 (Vetinari) and restart the application.\n" +
                "Download from: https://www.videolan.org/vlc/",
                "VLC Not Found",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        */

    // Ensure UI updates happen on Event Dispatch Thread
    SwingUtilities.invokeLater(() -> {
      try {
        VLCJVideoPlayer player = new VLCJVideoPlayer();
        player.setVisible(true);

        System.out.println("\n✓ Application started successfully!");
        System.out.println("Ready to play media files.");

      } catch (Exception e) {
        System.err.println("Failed to start application: " + e.getMessage());
        e.printStackTrace();

        JOptionPane.showMessageDialog(null,
            "Failed to start video player:\n" + e.getMessage() +
                "\n\nPlease ensure:\n" +
                "- VLC 3.0.18 (Vetinari) is installed\n" +
                "- VLC is in your system PATH\n" +
                "- All required dependencies are available\n\n" +
                "Download VLC from: https://www.videolan.org/vlc/",
            "Startup Error",
            JOptionPane.ERROR_MESSAGE);

        System.exit(1);
      }
    });
  }
}