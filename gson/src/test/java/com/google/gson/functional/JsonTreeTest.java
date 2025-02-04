package com.google.gson.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.elements.JsonElement;
import com.google.gson.elements.JsonObject;
import com.google.gson.elements.JsonPrimitive;
import com.google.gson.common.TestTypes.BagOfPrimitives;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/**
 * Functional tests for {@link Gson#toJsonTree(Object)} and 
 * {@link Gson#toJsonTree(Object, java.lang.reflect.Type)}
 * 
 * @author Inderjeet Singh
 * @author Joel Leitch
 */
public class JsonTreeTest {
  private Gson gson;

  @Before
  public void setUp() throws Exception {
    gson = new Gson();
  }

  @Test
  public void testToJsonTree() {
    BagOfPrimitives bag = new BagOfPrimitives(10L, 5, false, "foo");
    JsonElement json = gson.toJsonTree(bag);
    assertTrue(json.isJsonObject());
    JsonObject obj = json.getAsJsonObject();
    Set<Entry<String, JsonElement>> children = obj.entrySet();
    assertEquals(4, children.size());
    assertContains(obj, new JsonPrimitive(10L));
    assertContains(obj, new JsonPrimitive(5));
    assertContains(obj, new JsonPrimitive(false));
    assertContains(obj, new JsonPrimitive("foo"));
  }

  @Test
  public void testToJsonTreeObjectType() {
    SubTypeOfBagOfPrimitives bag = new SubTypeOfBagOfPrimitives(10L, 5, false, "foo", 1.4F);
    JsonElement json = gson.toJsonTree(bag, BagOfPrimitives.class);
    assertTrue(json.isJsonObject());
    JsonObject obj = json.getAsJsonObject();
    Set<Entry<String, JsonElement>> children = obj.entrySet();
    assertEquals(4, children.size());
    assertContains(obj, new JsonPrimitive(10L));
    assertContains(obj, new JsonPrimitive(5));
    assertContains(obj, new JsonPrimitive(false));
    assertContains(obj, new JsonPrimitive("foo"));
  }

  @Test
  public void testJsonTreeToString() {
    SubTypeOfBagOfPrimitives bag = new SubTypeOfBagOfPrimitives(10L, 5, false, "foo", 1.4F);
    String json1 = gson.toJson(bag);
    JsonElement jsonElement = gson.toJsonTree(bag, SubTypeOfBagOfPrimitives.class);
    String json2 = gson.toJson(jsonElement);
    assertEquals(json1, json2);
  }

  @Test
  public void testJsonTreeNull() {
    BagOfPrimitives bag = new BagOfPrimitives(10L, 5, false, null);
    JsonObject jsonElement = (JsonObject) gson.toJsonTree(bag, BagOfPrimitives.class);
    assertFalse(jsonElement.has("stringValue"));
  }

  private void assertContains(JsonObject json, JsonPrimitive child) {
    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
      JsonElement node = entry.getValue();
      if (node.isJsonPrimitive()) {
        if (node.getAsJsonPrimitive().equals(child)) {
          return;
        }
      }
    }
    fail();
  }
  
  private static class SubTypeOfBagOfPrimitives extends BagOfPrimitives {
    @SuppressWarnings("unused")
    float f = 1.2F;
    public SubTypeOfBagOfPrimitives(long l, int i, boolean b, String string, float f) {
      super(l, i, b, string);
      this.f = f;
    }
  }
}
