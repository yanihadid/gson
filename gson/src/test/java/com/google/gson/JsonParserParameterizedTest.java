package com.google.gson;

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;

import com.google.gson.elements.JsonElement;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JsonParserParameterizedTest {
  @Parameters
  public static Iterable<String> data() {
    return Arrays.asList(
      "[]",
      "{}",
      "null",
      "1.0",
      "true",
      "\"string\"",
      "[true,1.0,null,{},2.0,{\"a\":[false]},[3.0,\"test\"],4.0]",
      "{\"\":1.0,\"a\":true,\"b\":null,\"c\":[],\"d\":{\"a1\":2.0,\"b2\":[true,{\"a3\":3.0}]},\"e\":[{\"f\":4.0},\"test\"]}"
    );
  }

  private final TypeAdapter<JsonElement> adapter = new Gson().getAdapter(JsonElement.class);
  @Parameter
  public String json;

  @Test
  public void testParse() {
    JsonElement deserialized = JsonParser.parseString(json);
    String actualSerialized = adapter.toJson(deserialized);

    // Serialized JsonElement should be the same as original JSON
    assertThat(actualSerialized).isEqualTo(json);
  }
}
