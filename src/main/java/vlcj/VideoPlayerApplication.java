package vlcj;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

/**
 * Modern Video Player Application using VLCJ 4.7.1 and VLC 3.0.18 Supports multiple video formats with smooth playback Fixed
 * resolution: 1920x1080 with aspect ratio preservation
 */
public class VideoPlayerApplication extends JFrame {

  // Constants for fixed video dimensions
  private static final int VIDEO_WIDTH = 1920;
  private static final int VIDEO_HEIGHT = 1080;

  // UI Components
  private EmbeddedMediaPlayerComponent mediaPlayerComponent;
  private EmbeddedMediaPlayer mediaPlayer;

  // Control buttons
  private JButton playButton;
  private JButton pauseButton;
  private JButton stopButton;
  private JButton previousButton;
  private JButton nextButton;
  private JButton clearButton;

  // Speed control
  private JComboBox<String> speedComboBox;

  // Progress slider
  private JSlider progressSlider;
  private boolean isSliderBeingDragged = false;

  // File list components
  private DefaultListModel<String> fileListModel;
  private JList<String> fileList;
  private List<File> mediaFiles;
  private int currentMediaIndex = -1;

  // Timer for updating progress slider
  private Timer progressTimer;

  // Flag to track if we're in the middle of playing a playlist
  private boolean isPlaylistMode = false;

  /**
   * Constructor: Initializes the video player application
   */
  public VideoPlayerApplication() {
    super("Modern Video Player - VLCJ 4.7.1");

    // Initialize data structures
    mediaFiles = new ArrayList<>();
    fileListModel = new DefaultListModel<>();

    // Setup the main window
    setupWindow();

    // Initialize media player component
    initializeMediaPlayer();

    // Create UI components
    createUserInterface();

    // Setup event listeners
    setupEventListeners();

    // Initialize progress timer
    initializeProgressTimer();

    // Display the window
    setVisible(true);
  }

  /**
   * Setup main window properties
   */
  private void setupWindow() {
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLayout(new BorderLayout(10, 10));
    setResizable(false);

    // Add window listener for cleanup
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        cleanup();
      }
    });
  }

  /**
   * Initialize the VLCJ media player component
   */
  private void initializeMediaPlayer() {
    try {
      // Create embedded media player component
      mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
      mediaPlayer = mediaPlayerComponent.mediaPlayer();

      // Set fixed size for video panel
      mediaPlayerComponent.setPreferredSize(new Dimension(VIDEO_WIDTH, VIDEO_HEIGHT));
      mediaPlayerComponent.setMinimumSize(new Dimension(VIDEO_WIDTH, VIDEO_HEIGHT));
      mediaPlayerComponent.setMaximumSize(new Dimension(VIDEO_WIDTH, VIDEO_HEIGHT));

      // Add media player event listener for auto-play next feature
      mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
        @Override
        public void finished(MediaPlayer mediaPlayer) {
          // When current media finishes, play next if available
          handleMediaFinished();
        }

        @Override
        public void error(MediaPlayer mediaPlayer) {
          // Handle playback errors
          SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                VideoPlayerApplication.this,
                "Error playing media file. The file may be corrupted or in an unsupported format.",
                "Playback Error",
                JOptionPane.ERROR_MESSAGE
            );
            stopPlayback();
          });
        }
      });

    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          this,
          "Failed to initialize media player. Please ensure VLC is properly installed.\nError: " + e.getMessage(),
          "Initialization Error",
          JOptionPane.ERROR_MESSAGE
      );
      System.exit(1);
    }
  }

  /**
   * Create the user interface with modern design
   */
  private void createUserInterface() {
    // Main panel with padding
    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    mainPanel.setBackground(new Color(45, 45, 48));

    // Add video player in center
    JPanel videoPanel = new JPanel(new BorderLayout());
    videoPanel.setBackground(Color.BLACK);
    videoPanel.setBorder(new LineBorder(new Color(63, 63, 70), 2));
    videoPanel.add(mediaPlayerComponent, BorderLayout.CENTER);
    mainPanel.add(videoPanel, BorderLayout.CENTER);

    // Create control panel at bottom
    JPanel controlPanel = createControlPanel();
    mainPanel.add(controlPanel, BorderLayout.SOUTH);

    // Create side panel for file list
    JPanel sidePanel = createSidePanel();
    mainPanel.add(sidePanel, BorderLayout.EAST);

    add(mainPanel);
    pack();
    setLocationRelativeTo(null); // Center on screen
  }

  /**
   * Create the control panel with playback controls
   */
  private JPanel createControlPanel() {
    JPanel controlPanel = new JPanel(new BorderLayout(5, 5));
    controlPanel.setBackground(new Color(30, 30, 30));
    controlPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

    // Top section: Progress slider
    JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
    progressPanel.setBackground(new Color(30, 30, 30));

    JLabel timeLabel = new JLabel("Progress:");
    timeLabel.setForeground(Color.WHITE);
    progressPanel.add(timeLabel, BorderLayout.WEST);

    progressSlider = new JSlider(0, 100, 0);
    progressSlider.setBackground(new Color(30, 30, 30));
    progressSlider.setForeground(new Color(0, 120, 215));
    progressPanel.add(progressSlider, BorderLayout.CENTER);

    controlPanel.add(progressPanel, BorderLayout.NORTH);

    // Bottom section: Buttons and speed control
    JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
    buttonsPanel.setBackground(new Color(30, 30, 30));

    // Previous button
    previousButton = createStyledButton("⏮ Previous", new Color(63, 63, 70));
    buttonsPanel.add(previousButton);

    // Play button
    playButton = createStyledButton("▶ Play", new Color(0, 120, 215));
    buttonsPanel.add(playButton);

    // Pause button
    pauseButton = createStyledButton("⏸ Pause", new Color(255, 140, 0));
    buttonsPanel.add(pauseButton);

    // Stop button
    stopButton = createStyledButton("⏹ Stop", new Color(220, 50, 50));
    buttonsPanel.add(stopButton);

    // Next button
    nextButton = createStyledButton("⏭ Next", new Color(63, 63, 70));
    buttonsPanel.add(nextButton);

    // Speed control label and combobox
    JLabel speedLabel = new JLabel("Speed:");
    speedLabel.setForeground(Color.WHITE);
    buttonsPanel.add(speedLabel);

    String[] speeds = {"1x", "2x", "4x", "8x"};
    speedComboBox = new JComboBox<>(speeds);
    speedComboBox.setBackground(new Color(63, 63, 70));
    speedComboBox.setForeground(Color.WHITE);
    speedComboBox.setPreferredSize(new Dimension(80, 30));
    buttonsPanel.add(speedComboBox);

    controlPanel.add(buttonsPanel, BorderLayout.CENTER);

    return controlPanel;
  }

  /**
   * Create the side panel for file list management
   */
  private JPanel createSidePanel() {
    JPanel sidePanel = new JPanel(new BorderLayout(5, 5));
    sidePanel.setPreferredSize(new Dimension(300, VIDEO_HEIGHT));
    sidePanel.setBackground(new Color(37, 37, 38));
    sidePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

    // Title label
    JLabel titleLabel = new JLabel("Media Files");
    titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
    titleLabel.setForeground(Color.WHITE);
    titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
    sidePanel.add(titleLabel, BorderLayout.NORTH);

    // File list with scroll pane
    fileList = new JList<>(fileListModel);
    fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    fileList.setBackground(new Color(30, 30, 30));
    fileList.setForeground(Color.WHITE);
    fileList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    fileList.setBorder(new EmptyBorder(5, 5, 5, 5));

    JScrollPane scrollPane = new JScrollPane(fileList);
    scrollPane.setBorder(new LineBorder(new Color(63, 63, 70), 1));
    sidePanel.add(scrollPane, BorderLayout.CENTER);

    // Button panel
    JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
    buttonPanel.setBackground(new Color(37, 37, 38));
    buttonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

    JButton loadButton = createStyledButton("Load Files", new Color(0, 120, 215));
    clearButton = createStyledButton("Clear All", new Color(220, 50, 50));

    buttonPanel.add(loadButton);
    buttonPanel.add(clearButton);

    sidePanel.add(buttonPanel, BorderLayout.SOUTH);

    // Add action listener for load button
    loadButton.addActionListener(e -> loadMediaFiles());

    return sidePanel;
  }

  /**
   * Create a styled button with modern appearance
   */
  private JButton createStyledButton(String text, Color bgColor) {
    JButton button = new JButton(text);
    button.setBackground(bgColor);
    button.setForeground(Color.WHITE);
    button.setFocusPainted(false);
    button.setBorderPainted(false);
    button.setFont(new Font("Segoe UI", Font.BOLD, 12));
    button.setPreferredSize(new Dimension(120, 35));
    button.setCursor(new Cursor(Cursor.HAND_CURSOR));

    // Add hover effect
    button.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        button.setBackground(bgColor.brighter());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        button.setBackground(bgColor);
      }
    });

    return button;
  }

  /**
   * Setup event listeners for all interactive components
   */
  private void setupEventListeners() {
    // Play button
    playButton.addActionListener(e -> playMedia());

    // Pause button
    pauseButton.addActionListener(e -> pauseMedia());

    // Stop button
    stopButton.addActionListener(e -> stopPlayback());

    // Previous button
    previousButton.addActionListener(e -> playPreviousMedia());

    // Next button
    nextButton.addActionListener(e -> playNextMedia());

    // Clear button
    clearButton.addActionListener(e -> clearMediaList());

    // Speed combobox
    speedComboBox.addActionListener(e -> changePlaybackSpeed());

    // Progress slider
    progressSlider.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        isSliderBeingDragged = true;
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        isSliderBeingDragged = false;
        seekToSliderPosition();
      }
    });

    progressSlider.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        if (!isSliderBeingDragged && progressSlider.getValueIsAdjusting()) {
          isSliderBeingDragged = true;
        }
      }
    });

    // File list selection
    fileList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        int selectedIndex = fileList.getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < mediaFiles.size()) {
          currentMediaIndex = selectedIndex;
          loadSelectedMedia();
        }
      }
    });
  }

  /**
   * Initialize timer for updating progress slider
   */
  private void initializeProgressTimer() {
    progressTimer = new Timer(100, e -> updateProgressSlider());
    progressTimer.start();
  }

  /**
   * Update progress slider based on current playback position
   */
  private void updateProgressSlider() {
    if (mediaPlayer != null && mediaPlayer.status().isPlaying() && !isSliderBeingDragged) {
      try {
        float position = mediaPlayer.status().position();
        if (position >= 0 && position <= 1.0f) {
          progressSlider.setValue((int) (position * 100));
        }
      } catch (Exception e) {
        // Ignore exceptions during position retrieval
      }
    }
  }

  /**
   * Seek to the position indicated by the slider
   */
  private void seekToSliderPosition() {
    if (mediaPlayer != null && mediaPlayer.status().length() > 0) {
      try {
        float position = progressSlider.getValue() / 100.0f;
        mediaPlayer.controls().setPosition(position);
      } catch (Exception e) {
        showError("Failed to seek to position: " + e.getMessage());
      }
    }
  }

  /**
   * Load media files from file chooser
   */
  private void loadMediaFiles() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setMultiSelectionEnabled(true);
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setDialogTitle("Select Media Files");

    // Set file filter for common video formats
    fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
      @Override
      public boolean accept(File f) {
        if (f.isDirectory()) {
          return true;
        }
        String name = f.getName().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".avi") ||
            name.endsWith(".mkv") || name.endsWith(".mov") ||
            name.endsWith(".wmv") || name.endsWith(".flv") ||
            name.endsWith(".webm") || name.endsWith(".m4v") ||
            name.endsWith(".mpg") || name.endsWith(".mpeg");
      }

      @Override
      public String getDescription() {
        return "Video Files (*.mp4, *.avi, *.mkv, *.mov, etc.)";
      }
    });

    int result = fileChooser.showOpenDialog(this);
    if (result == JFileChooser.APPROVE_OPTION) {
      File[] selectedFiles = fileChooser.getSelectedFiles();
      if (selectedFiles != null && selectedFiles.length > 0) {
        addMediaFiles(selectedFiles);
      }
    }
  }

  /**
   * Add media files to the playlist
   */
  private void addMediaFiles(File[] files) {
    for (File file : files) {
      if (file.exists() && file.isFile()) {
        mediaFiles.add(file);
        fileListModel.addElement(file.getName());
      }
    }

    // If this is the first file added, select it
    if (mediaFiles.size() == files.length && mediaFiles.size() > 0) {
      fileList.setSelectedIndex(0);
      currentMediaIndex = 0;
    }
  }

  /**
   * Load the currently selected media file
   */
  private void loadSelectedMedia() {
    if (currentMediaIndex >= 0 && currentMediaIndex < mediaFiles.size()) {
      try {
        File mediaFile = mediaFiles.get(currentMediaIndex);

        // Stop current playback
        if (mediaPlayer.status().isPlaying()) {
          mediaPlayer.controls().stop();
        }

        // Prepare the media with aspect ratio options
        String[] options = {
            ":aspect-ratio=16:9",  // Try to maintain aspect ratio
            ":no-video-title-show"  // Don't show filename on video
        };

        // Start playback of new media
        boolean started = mediaPlayer.media().play(mediaFile.getAbsolutePath(), options);

        if (!started) {
          showError("Failed to load media file: " + mediaFile.getName());
        }

        // Reset progress slider
        progressSlider.setValue(0);

        // Update list selection
        fileList.setSelectedIndex(currentMediaIndex);

      } catch (Exception e) {
        showError("Error loading media: " + e.getMessage());
      }
    }
  }

  /**
   * Play the current media or resume from pause
   */
  private void playMedia() {
    if (mediaPlayer == null) {
      return;
    }

    try {
      if (currentMediaIndex >= 0 && currentMediaIndex < mediaFiles.size()) {
        if (mediaPlayer.status().isPlaying()) {
          // Already playing, do nothing
          return;
        }

        // Check if media is loaded
        if (mediaPlayer.status().length() > 0) {
          // Resume paused media
          mediaPlayer.controls().play();
        } else {
          // Load and play selected media
          loadSelectedMedia();
        }
      } else if (!mediaFiles.isEmpty()) {
        // No selection, play first file
        currentMediaIndex = 0;
        fileList.setSelectedIndex(0);
        loadSelectedMedia();
      } else {
        showError("No media files loaded. Please load files first.");
      }
    } catch (Exception e) {
      showError("Error playing media: " + e.getMessage());
    }
  }

  /**
   * Pause the current playback
   */
  private void pauseMedia() {
    if (mediaPlayer != null && mediaPlayer.status().isPlaying()) {
      try {
        mediaPlayer.controls().pause();
      } catch (Exception e) {
        showError("Error pausing media: " + e.getMessage());
      }
    }
  }

  /**
   * Stop the current playback
   */
  private void stopPlayback() {
    if (mediaPlayer != null) {
      try {
        mediaPlayer.controls().stop();
        progressSlider.setValue(0);
        isPlaylistMode = false;
      } catch (Exception e) {
        showError("Error stopping media: " + e.getMessage());
      }
    }
  }

  /**
   * Play the previous media file in the list
   */
  private void playPreviousMedia() {
    if (mediaFiles.isEmpty()) {
      showError("No media files loaded.");
      return;
    }

    if (currentMediaIndex > 0) {
      currentMediaIndex--;
      fileList.setSelectedIndex(currentMediaIndex);
      loadSelectedMedia();
    } else {
      showInfo("Already at the first media file.");
    }
  }

  /**
   * Play the next media file in the list
   */
  private void playNextMedia() {
    if (mediaFiles.isEmpty()) {
      showError("No media files loaded.");
      return;
    }

    if (currentMediaIndex < mediaFiles.size() - 1) {
      currentMediaIndex++;
      fileList.setSelectedIndex(currentMediaIndex);
      loadSelectedMedia();
      isPlaylistMode = true;
    } else {
      showInfo("Already at the last media file.");
      isPlaylistMode = false;
    }
  }

  /**
   * Handle media finished event - auto-play next file
   */
  private void handleMediaFinished() {
    SwingUtilities.invokeLater(() -> {
      if (currentMediaIndex < mediaFiles.size() - 1) {
        // Play next file automatically
        currentMediaIndex++;
        fileList.setSelectedIndex(currentMediaIndex);
        loadSelectedMedia();
      } else {
        // Reached end of playlist
        isPlaylistMode = false;
        progressSlider.setValue(0);
      }
    });
  }

  /**
   * Clear all media files from the list
   */
  private void clearMediaList() {
    if (mediaFiles.isEmpty()) {
      return;
    }

    int confirm = JOptionPane.showConfirmDialog(
        this,
        "Are you sure you want to clear all media files?",
        "Clear Confirmation",
        JOptionPane.YES_NO_OPTION
    );

    if (confirm == JOptionPane.YES_OPTION) {
      // Stop current playback
      stopPlayback();

      // Clear data structures
      mediaFiles.clear();
      fileListModel.clear();
      currentMediaIndex = -1;

      // Reset UI
      progressSlider.setValue(0);
    }
  }

  /**
   * Change playback speed based on combobox selection
   */
  private void changePlaybackSpeed() {
    if (mediaPlayer == null) {
      return;
    }

    try {
      String selectedSpeed = (String) speedComboBox.getSelectedItem();
      float speed = 1.0f;

      switch (selectedSpeed) {
        case "1x":
          speed = 1.0f;
          break;
        case "2x":
          speed = 2.0f;
          break;
        case "4x":
          speed = 4.0f;
          break;
        case "8x":
          speed = 8.0f;
          break;
        default:
          speed = 1.0f;
      }

      mediaPlayer.controls().setRate(speed);
    } catch (Exception e) {
      showError("Error changing playback speed: " + e.getMessage());
    }
  }

  /**
   * Show error message dialog
   */
  private void showError(String message) {
    JOptionPane.showMessageDialog(
        this,
        message,
        "Error",
        JOptionPane.ERROR_MESSAGE
    );
  }

  /**
   * Show information message dialog
   */
  private void showInfo(String message) {
    JOptionPane.showMessageDialog(
        this,
        message,
        "Information",
        JOptionPane.INFORMATION_MESSAGE
    );
  }

  /**
   * Cleanup resources before closing
   */
  private void cleanup() {
    try {
      // Stop timer
      if (progressTimer != null) {
        progressTimer.stop();
      }

      // Stop and release media player
      if (mediaPlayer != null) {
        mediaPlayer.controls().stop();
        mediaPlayer.release();
      }

      // Release media player component
      if (mediaPlayerComponent != null) {
        mediaPlayerComponent.release();
      }
    } catch (Exception e) {
      System.err.println("Error during cleanup: " + e.getMessage());
    }
  }

  /**
   * Main method to launch the application
   */
  public static void main(String[] args) {
    // Set system look and feel for better appearance
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Run application on Event Dispatch Thread
    SwingUtilities.invokeLater(() -> {
      try {
        new VideoPlayerApplication();
      } catch (Exception e) {
        JOptionPane.showMessageDialog(
            null,
            "Failed to start video player:\n" + e.getMessage() +
                "\n\nPlease ensure:\n" +
                "1. VLC 3.0.18 is installed\n" +
                "2. VLCJ 4.7.1 library is in classpath\n" +
                "3. All dependencies are properly configured",
            "Startup Error",
            JOptionPane.ERROR_MESSAGE
        );
        e.printStackTrace();
      }
    });
  }
}