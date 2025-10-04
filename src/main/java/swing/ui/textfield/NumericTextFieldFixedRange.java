package swing.ui.textfield;

import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class NumericTextFieldFixedRange extends JTextField {

  private final int minValue;
  private final int maxValue;
  private final Color backgroundColor;

  public NumericTextFieldFixedRange(String name, int minValue, int maxValue) {
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.setName(name);
    this.setColumns(20);
    this.setToolTipText("Must be a number between [" + this.minValue + ", " + this.maxValue + "]");
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
        // Check if the resulting number would be less than equal to max value
        String potentialText = currentText + c;
        try {
          long potentialValue = Long.parseLong(potentialText);
          if (potentialValue > maxValue) {
            e.consume();
          }
        } catch (NumberFormatException ex) {
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
    try {
      long value = Long.parseLong(text);
      if (value >= minValue && value <= maxValue) {
        setBackground(new Color(21, 41, 25));
      } else {
        setBackground(new Color(69, 16, 16));
      }
    } catch (NumberFormatException e) {
      setBackground(backgroundColor);
    }
  }

  public int getMinValue() {
    return minValue;
  }

  public int getMaxValue() {
    return maxValue;
  }
}
