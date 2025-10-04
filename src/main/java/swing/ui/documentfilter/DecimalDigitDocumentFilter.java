package swing.ui.documentfilter;

import java.awt.Toolkit;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public class DecimalDigitDocumentFilter extends DocumentFilter {

  private final int maxDecimalDigits;
  private final double maxValue;

  public DecimalDigitDocumentFilter(int maxDecimalDigits, double maxValue) {
    this.maxDecimalDigits = maxDecimalDigits;
    this.maxValue = maxValue;
  }

  @Override
  public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
      throws BadLocationException {
    if (string == null) {
      return;
    }
    String newText = getNewText(fb, offset, 0, string);
    if (isValidInput(newText)) {
      super.insertString(fb, offset, string, attr);
    } else {
      Toolkit.getDefaultToolkit().beep();
    }
  }

  @Override
  public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
      throws BadLocationException {
    if (text == null) {
      return;
    }
    String newText = getNewText(fb, offset, length, text);
    if (isValidInput(newText)) {
      super.replace(fb, offset, length, text, attrs);
    } else {
      Toolkit.getDefaultToolkit().beep();
    }
  }

  private String getNewText(FilterBypass fb, int offset, int length, String text)
      throws BadLocationException {
    StringBuilder sb = new StringBuilder();
    javax.swing.text.Document doc = fb.getDocument();
    sb.append(doc.getText(0, doc.getLength()));
    sb.replace(offset, offset + length, text);
    return sb.toString();
  }

  private boolean isValidInput(String text) {
    if (text.isEmpty()) {
      return true;
    }
    try {
      // Test if it's a valid double
      if (text.endsWith(".")) {
        Double.parseDouble(text.substring(0, text.length() - 1));
      } else {
        Double.parseDouble(text);
      }
      // Check decimal digits
      if (text.contains(".")) {
        String decimalPart = text.substring(text.indexOf(".") + 1);
        if (decimalPart.length() > maxDecimalDigits) {
          return false;
        } else {
          return !isBiggerThanMaxValue(text);
        }
      } else {
        return !isBiggerThanMaxValue(text);
      }
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private boolean isBiggerThanMaxValue(String text) {
    return Double.parseDouble(text) > maxValue;
  }
}