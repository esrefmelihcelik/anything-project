package swing.ui.textfield;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JTextField;

public class TextFieldMaxLength extends JTextField {

  private final int maxLength;

  public TextFieldMaxLength(String name, int maxLength) {
    this.maxLength = maxLength;
    this.setName(name);
    this.setColumns(20);
    this.setToolTipText("Must be at most " + this.maxLength + " character long.");
    attachListeners();
  }

  private void attachListeners() {
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        String currentText = getText();
        // Prevent typing beyond required length
        if (currentText != null && currentText.length() >= maxLength) {
          e.consume();
        }
      }
    });
  }

  public int getMaxLength() {
    return maxLength;
  }
}
