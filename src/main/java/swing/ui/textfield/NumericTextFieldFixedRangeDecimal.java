package swing.ui.textfield;

import java.awt.Color;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.PlainDocument;
import swing.ui.documentfilter.DecimalDigitDocumentFilter;

public class NumericTextFieldFixedRangeDecimal extends JTextField {

  private static final int MAX_LENGTH = 4;
  private final double minValue;
  private final double maxValue;
  private final Color backgroundColor;

  public NumericTextFieldFixedRangeDecimal(String name, double minValue, double maxValue) {
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.setName(name);
    this.setColumns(20);
    this.setToolTipText("Must be a number between [" + this.minValue + ", " + this.maxValue + "]");
    PlainDocument doc = (PlainDocument) this.getDocument();
    doc.setDocumentFilter(new DecimalDigitDocumentFilter(MAX_LENGTH, this.maxValue));
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

  }

  private void updateTextFieldColor() {
    String text = getText();
    if (text.isEmpty()) {
      setBackground(backgroundColor);
      return;
    }
    try {
      double value = Double.parseDouble(text);
      if (value >= minValue && value <= maxValue) {
        setBackground(new Color(21, 41, 25));
      } else {
        setBackground(new Color(69, 16, 16));
      }
    } catch (NumberFormatException e) {
      setBackground(backgroundColor);
    }
  }

  public double getMinValue() {
    return minValue;
  }

  public double getMaxValue() {
    return maxValue;
  }
}
