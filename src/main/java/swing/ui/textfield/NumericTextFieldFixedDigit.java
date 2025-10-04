package swing.ui.textfield;

import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class NumericTextFieldFixedDigit extends JTextField {

  private final int requiredLength;
  private final Color backgroundColor;

  public NumericTextFieldFixedDigit(String name, int requiredLength) {
    this.requiredLength = requiredLength;
    this.setName(name);
    this.setColumns(20);
    this.setToolTipText("Must be a " + this.requiredLength + " digit number.");
    attachListeners();
    this.backgroundColor = new Color(getBackground().getRGB());
  }

  private void attachListeners() {
    getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        updateTextFieldColor();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        updateTextFieldColor();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        updateTextFieldColor();
      }
    });

    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();
        // Allow only numeric characters
        if (!Character.isDigit(c)) {
          e.consume();
          return;
        }
        String currentText = getText();
        // Prevent starting with 0
        if ((currentText == null || currentText.isEmpty()) && c == '0') {
          e.consume();
          return;
        }
        // Prevent typing beyond required length digits
        if (currentText != null && currentText.length() >= requiredLength) {
          e.consume();
        }
      }
    });
  }

  private void updateTextFieldColor() {
    String text = getText();
    if (text.isEmpty()) {
      setBackground(backgroundColor);
      return;
    }
    if (text.length() == requiredLength) {
      setBackground(new Color(21, 41, 25));
    } else {
      setBackground(new Color(69, 16, 16));
    }
  }

  public int getRequiredLength() {
    return requiredLength;
  }
}
