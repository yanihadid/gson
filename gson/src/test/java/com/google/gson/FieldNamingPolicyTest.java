package com.google.gson;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.lang.reflect.Field;
import java.util.Locale;

import org.junit.Test;
import com.google.gson.functional.FieldNamingTest;

/**
 * Performs tests directly against {@link GsonBuilder.FieldNamingPolicy}; for integration tests
 * see {@link FieldNamingTest}.
 */
public class FieldNamingPolicyTest {
  @Test
  public void testSeparateCamelCase() {
    // Map from original -> expected
    String[][] argumentPairs =  {
      { "a", "a" },
      { "ab", "ab" },
      { "Ab", "Ab" },
      { "aB", "a_B" },
      { "AB", "A_B" },
      { "A_B", "A__B" },
      { "firstSecondThird", "first_Second_Third" },
      { "__", "__" },
      { "_123", "_123" }
    };

    for (String[] pair : argumentPairs) {
      assertThat(GsonBuilder.FieldNamingPolicy.separateCamelCase(pair[0], '_')).isEqualTo(pair[1]);
    }
  }

  @Test
  public void testUpperCaseFirstLetter() {
    // Map from original -> expected
    String[][] argumentPairs =  {
      { "a", "A" },
      { "ab", "Ab" },
      { "AB", "AB" },
      { "_a", "_A" },
      { "_ab", "_Ab" },
      { "__", "__" },
      { "_1", "_1" },
      // Not a letter, but has uppercase variant (should not be uppercased)
      // See https://github.com/google/gson/issues/1965
      { "\u2170", "\u2170" },
      { "_\u2170", "_\u2170" },
      { "\u2170a", "\u2170A" },
    };

    for (String[] pair : argumentPairs) {
      assertThat(GsonBuilder.FieldNamingPolicy.upperCaseFirstLetter(pair[0])).isEqualTo(pair[1]);
    }
  }

  /**
   * Upper-casing policies should be unaffected by default Locale.
   */
  @Test
  public void testUpperCasingLocaleIndependent() throws Exception {
    class Dummy {
      @SuppressWarnings("unused")
      int i;
    }

    GsonBuilder.FieldNamingPolicy[] policies = {
      GsonBuilder.FieldNamingPolicy.UPPER_CAMEL_CASE,
      GsonBuilder.FieldNamingPolicy.UPPER_CAMEL_CASE_WITH_SPACES,
      GsonBuilder.FieldNamingPolicy.UPPER_CASE_WITH_UNDERSCORES,
    };

    Field field = Dummy.class.getDeclaredField("i");
    String name = field.getName();
    String expected = name.toUpperCase(Locale.ROOT);

    Locale oldLocale = Locale.getDefault();
    // Set Turkish as Locale which has special case conversion rules
    Locale.setDefault(new Locale("tr"));

    try {
      // Verify that default Locale has different case conversion rules
      assertWithMessage("Test setup is broken")
          .that(name.toUpperCase()).doesNotMatch(expected);

      for (GsonBuilder.FieldNamingPolicy policy : policies) {
        // Should ignore default Locale
        assertWithMessage("Unexpected conversion for %s", policy)
            .that(policy.translateName(field)).matches(expected);
      }
    } finally {
        Locale.setDefault(oldLocale);
    }
  }

  /**
   * Lower casing policies should be unaffected by default Locale.
   */
  @Test
  public void testLowerCasingLocaleIndependent() throws Exception {
    class Dummy {
      @SuppressWarnings("unused")
      int I;
    }

    GsonBuilder.FieldNamingPolicy[] policies = {
      GsonBuilder.FieldNamingPolicy.LOWER_CASE_WITH_DASHES,
      GsonBuilder.FieldNamingPolicy.LOWER_CASE_WITH_DOTS,
      GsonBuilder.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES,
    };

    Field field = Dummy.class.getDeclaredField("I");
    String name = field.getName();
    String expected = name.toLowerCase(Locale.ROOT);

    Locale oldLocale = Locale.getDefault();
    // Set Turkish as Locale which has special case conversion rules
    Locale.setDefault(new Locale("tr"));

    try {
      // Verify that default Locale has different case conversion rules
      assertWithMessage("Test setup is broken")
          .that(name.toLowerCase()).doesNotMatch(expected);

      for (GsonBuilder.FieldNamingPolicy policy : policies) {
        // Should ignore default Locale
        assertWithMessage("Unexpected conversion for %s", policy)
            .that(policy.translateName(field)).matches(expected);
      }
    } finally {
        Locale.setDefault(oldLocale);
    }
  }
}
