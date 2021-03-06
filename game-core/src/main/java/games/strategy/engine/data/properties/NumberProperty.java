package games.strategy.engine.data.properties;

import static com.google.common.base.Preconditions.checkArgument;

import javax.swing.JComponent;
import lombok.Getter;
import org.triplea.swing.IntTextField;

/** Implementation of {@link IEditableProperty} for an integer value. */
public class NumberProperty extends AbstractEditableProperty<Integer> {
  private static final long serialVersionUID = 6826763550643504789L;

  @Getter private final int max;
  @Getter private final int min;
  private int value;

  /**
   * Initializes a new instance of the {@link NumberProperty} class.
   *
   * @throws IllegalArgumentException If {@code max} is less than {@code min}; if {@code def} is
   *     less than {@code min}; or if {@code def} is greater than {@code max}.
   */
  public NumberProperty(
      final String name, final String description, final int max, final int min, final int def) {
    super(name, description);

    checkArgument(max >= min, "Max must be greater than min");
    checkArgument((def >= min) && (def <= max), "Default value out of range");

    this.max = max;
    this.min = min;
    value = def;
  }

  @Override
  public Integer getValue() {
    return value;
  }

  @Override
  public void setValue(final Integer value) {
    this.value = value;
  }

  @Override
  public JComponent getEditorComponent() {
    final IntTextField field = new IntTextField(min, max);
    field.setValue(value);
    field.addChangeListener(aField -> value = aField.getValue());
    return field;
  }

  @Override
  public boolean validate(final Object value) {
    if (value instanceof Integer) {
      final int i = (int) value;
      return i <= max && i >= min;
    }
    return false;
  }
}
