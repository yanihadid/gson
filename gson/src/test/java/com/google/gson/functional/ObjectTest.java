/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gson.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.elements.JsonElement;
import com.google.gson.exception.JsonIOException;
import com.google.gson.elements.JsonObject;
import com.google.gson.exception.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.common.TestTypes.ArrayOfObjects;
import com.google.gson.common.TestTypes.BagOfPrimitiveWrappers;
import com.google.gson.common.TestTypes.BagOfPrimitives;
import com.google.gson.common.TestTypes.ClassWithArray;
import com.google.gson.common.TestTypes.ClassWithNoFields;
import com.google.gson.common.TestTypes.ClassWithObjects;
import com.google.gson.common.TestTypes.ClassWithTransientFields;
import com.google.gson.common.TestTypes.Nested;
import com.google.gson.common.TestTypes.PrimitiveArray;
import com.google.gson.internal.JavaVersion;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Functional tests for Json serialization and deserialization of regular classes.
 *
 * @author Inderjeet Singh
 * @author Joel Leitch
 */
public class ObjectTest {
  private Gson gson;
  private TimeZone oldTimeZone;
  private Locale oldLocale;

  @Before
  public void setUp() throws Exception {
    gson = new Gson();

    oldTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
    oldLocale = Locale.getDefault();
    Locale.setDefault(Locale.US);
  }

  @After
  public void tearDown() throws Exception {
    TimeZone.setDefault(oldTimeZone);
    Locale.setDefault(oldLocale);
  }

  @Test
  public void testJsonInSingleQuotesDeserialization() {
    String json = "{'stringValue':'no message','intValue':10,'longValue':20}";
    BagOfPrimitives target = gson.fromJson(json, BagOfPrimitives.class);
    assertEquals("no message", target.stringValue);
    assertEquals(10, target.intValue);
    assertEquals(20, target.longValue);
  }

  @Test
  public void testJsonInMixedQuotesDeserialization() {
    String json = "{\"stringValue\":'no message','intValue':10,'longValue':20}";
    BagOfPrimitives target = gson.fromJson(json, BagOfPrimitives.class);
    assertEquals("no message", target.stringValue);
    assertEquals(10, target.intValue);
    assertEquals(20, target.longValue);
  }

  @Test
  public void testBagOfPrimitivesSerialization() throws Exception {
    BagOfPrimitives target = new BagOfPrimitives(10, 20, false, "stringValue");
    assertEquals(target.getExpectedJson(), gson.toJson(target));
  }

  @Test
  public void testBagOfPrimitivesDeserialization() throws Exception {
    BagOfPrimitives src = new BagOfPrimitives(10, 20, false, "stringValue");
    String json = src.getExpectedJson();
    BagOfPrimitives target = gson.fromJson(json, BagOfPrimitives.class);
    assertEquals(json, target.getExpectedJson());
  }

  @Test
  public void testBagOfPrimitiveWrappersSerialization() throws Exception {
    BagOfPrimitiveWrappers target = new BagOfPrimitiveWrappers(10L, 20, false);
    assertEquals(target.getExpectedJson(), gson.toJson(target));
  }

  @Test
  public void testBagOfPrimitiveWrappersDeserialization() throws Exception {
    BagOfPrimitiveWrappers target = new BagOfPrimitiveWrappers(10L, 20, false);
    String jsonString = target.getExpectedJson();
    target = gson.fromJson(jsonString, BagOfPrimitiveWrappers.class);
    assertEquals(jsonString, target.getExpectedJson());
  }

  @Test
  public void testClassWithTransientFieldsSerialization() throws Exception {
    ClassWithTransientFields<Long> target = new ClassWithTransientFields<>(1L);
    assertEquals(target.getExpectedJson(), gson.toJson(target));
  }

  @Test
  public void testClassWithTransientFieldsDeserialization() throws Exception {
    String json = "{\"longValue\":[1]}";
    ClassWithTransientFields<?> target = gson.fromJson(json, ClassWithTransientFields.class);
    assertEquals(json, target.getExpectedJson());
  }

  @Test
  public void testClassWithTransientFieldsDeserializationTransientFieldsPassedInJsonAreIgnored()
      throws Exception {
    String json = "{\"transientLongValue\":1,\"longValue\":[1]}";
    ClassWithTransientFields<?> target = gson.fromJson(json, ClassWithTransientFields.class);
    assertFalse(target.transientLongValue != 1);
  }

  @Test
  public void testClassWithNoFieldsSerialization() throws Exception {
    assertEquals("{}", gson.toJson(new ClassWithNoFields()));
  }

  @Test
  public void testClassWithNoFieldsDeserialization() throws Exception {
    String json = "{}";
    ClassWithNoFields target = gson.fromJson(json, ClassWithNoFields.class);
    ClassWithNoFields expected = new ClassWithNoFields();
    assertEquals(expected, target);
  }

  private static class Subclass extends Superclass1 {
  }
  private static class Superclass1 extends Superclass2 {
    @SuppressWarnings("unused")
    String s;
  }
  private static class Superclass2 {
    @SuppressWarnings("unused")
    String s;
  }

  @Test
  public void testClassWithDuplicateFields() {
    try {
      gson.getAdapter(Subclass.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Class com.google.gson.functional.ObjectTest$Subclass declares multiple JSON fields named 's';"
          + " conflict is caused by fields com.google.gson.functional.ObjectTest$Superclass1#s and"
          + " com.google.gson.functional.ObjectTest$Superclass2#s",
          e.getMessage()
      );
    }
  }

  @Test
  public void testNestedSerialization() throws Exception {
    Nested target = new Nested(new BagOfPrimitives(10, 20, false, "stringValue"),
       new BagOfPrimitives(30, 40, true, "stringValue"));
    assertEquals(target.getExpectedJson(), gson.toJson(target));
  }

  @Test
  public void testNestedDeserialization() throws Exception {
    String json = "{\"primitive1\":{\"longValue\":10,\"intValue\":20,\"booleanValue\":false,"
        + "\"stringValue\":\"stringValue\"},\"primitive2\":{\"longValue\":30,\"intValue\":40,"
        + "\"booleanValue\":true,\"stringValue\":\"stringValue\"}}";
    Nested target = gson.fromJson(json, Nested.class);
    assertEquals(json, target.getExpectedJson());
  }
  @Test
  public void testNullSerialization() throws Exception {
    assertEquals("null", gson.toJson(null));
  }

  @Test
  public void testEmptyStringDeserialization() throws Exception {
    Object object = gson.fromJson("", Object.class);
    assertNull(object);
  }

  @Test
  public void testTruncatedDeserialization() {
    try {
      gson.fromJson("[\"a\", \"b\",", new Gson.TypeToken<List<String>>() {}.getType());
      fail();
    } catch (JsonParseException expected) {
    }
  }

  @Test
  public void testNullDeserialization() throws Exception {
    String myNullObject = null;
    Object object = gson.fromJson(myNullObject, Object.class);
    assertNull(object);
  }

  @Test
  public void testNullFieldsSerialization() throws Exception {
    Nested target = new Nested(new BagOfPrimitives(10, 20, false, "stringValue"), null);
    assertEquals(target.getExpectedJson(), gson.toJson(target));
  }

  @Test
  public void testNullFieldsDeserialization() throws Exception {
    String json = "{\"primitive1\":{\"longValue\":10,\"intValue\":20,\"booleanValue\":false"
        + ",\"stringValue\":\"stringValue\"}}";
    Nested target = gson.fromJson(json, Nested.class);
    assertEquals(json, target.getExpectedJson());
  }

  @Test
  public void testArrayOfObjectsSerialization() throws Exception {
    ArrayOfObjects target = new ArrayOfObjects();
    assertEquals(target.getExpectedJson(), gson.toJson(target));
  }

  @Test
  public void testArrayOfObjectsDeserialization() throws Exception {
    String json = new ArrayOfObjects().getExpectedJson();
    ArrayOfObjects target = gson.fromJson(json, ArrayOfObjects.class);
    assertEquals(json, target.getExpectedJson());
  }

  @Test
  public void testArrayOfArraysSerialization() throws Exception {
    ArrayOfArrays target = new ArrayOfArrays();
    assertEquals(target.getExpectedJson(), gson.toJson(target));
  }

  @Test
  public void testArrayOfArraysDeserialization() throws Exception {
    String json = new ArrayOfArrays().getExpectedJson();
    ArrayOfArrays target = gson.fromJson(json, ArrayOfArrays.class);
    assertEquals(json, target.getExpectedJson());
  }

  @Test
  public void testArrayOfObjectsAsFields() throws Exception {
    ClassWithObjects classWithObjects = new ClassWithObjects();
    BagOfPrimitives bagOfPrimitives = new BagOfPrimitives();
    String stringValue = "someStringValueInArray";
    String classWithObjectsJson = gson.toJson(classWithObjects);
    String bagOfPrimitivesJson = gson.toJson(bagOfPrimitives);

    ClassWithArray classWithArray = new ClassWithArray(
        new Object[] { stringValue, classWithObjects, bagOfPrimitives });
    String json = gson.toJson(classWithArray);

    assertTrue(json.contains(classWithObjectsJson));
    assertTrue(json.contains(bagOfPrimitivesJson));
    assertTrue(json.contains("\"" + stringValue + "\""));
  }

  /**
   * Created in response to Issue 14: http://code.google.com/p/google-gson/issues/detail?id=14
   */
  @Test
  public void testNullArraysDeserialization() throws Exception {
    String json = "{\"array\": null}";
    ClassWithArray target = gson.fromJson(json, ClassWithArray.class);
    assertNull(target.array);
  }

  /**
   * Created in response to Issue 14: http://code.google.com/p/google-gson/issues/detail?id=14
   */
  @Test
  public void testNullObjectFieldsDeserialization() throws Exception {
    String json = "{\"bag\": null}";
    ClassWithObjects target = gson.fromJson(json, ClassWithObjects.class);
    assertNull(target.bag);
  }

  @Test
  public void testEmptyCollectionInAnObjectDeserialization() throws Exception {
    String json = "{\"children\":[]}";
    ClassWithCollectionField target = gson.fromJson(json, ClassWithCollectionField.class);
    assertNotNull(target);
    assertTrue(target.children.isEmpty());
  }

  private static class ClassWithCollectionField {
    Collection<String> children = new ArrayList<>();
  }

  @Test
  public void testPrimitiveArrayInAnObjectDeserialization() throws Exception {
    String json = "{\"longArray\":[0,1,2,3,4,5,6,7,8,9]}";
    PrimitiveArray target = gson.fromJson(json, PrimitiveArray.class);
    assertEquals(json, target.getExpectedJson());
  }

  /**
   * Created in response to Issue 14: http://code.google.com/p/google-gson/issues/detail?id=14
   */
  @Test
  public void testNullPrimitiveFieldsDeserialization() throws Exception {
    String json = "{\"longValue\":null}";
    BagOfPrimitives target = gson.fromJson(json, BagOfPrimitives.class);
    assertEquals(BagOfPrimitives.DEFAULT_VALUE, target.longValue);
  }

  @Test
  public void testEmptyCollectionInAnObjectSerialization() throws Exception {
    ClassWithCollectionField target = new ClassWithCollectionField();
    assertEquals("{\"children\":[]}", gson.toJson(target));
  }

  @Test
  public void testPrivateNoArgConstructorDeserialization() throws Exception {
    ClassWithPrivateNoArgsConstructor target =
      gson.fromJson("{\"a\":20}", ClassWithPrivateNoArgsConstructor.class);
    assertEquals(20, target.a);
  }

  @Test
  public void testAnonymousLocalClassesSerialization() throws Exception {
    assertEquals("null", gson.toJson(new ClassWithNoFields() {
      // empty anonymous class
    }));
  }

  @Test
  public void testAnonymousLocalClassesCustomSerialization() throws Exception {
    gson = new GsonBuilder()
        .registerTypeHierarchyAdapter(ClassWithNoFields.class,
            new JsonSerializer<ClassWithNoFields>() {
              @Override public JsonElement serialize(
                  ClassWithNoFields src, Type typeOfSrc, JsonSerializationContext context) {
                return new JsonObject();
              }
            }).create();

    assertEquals("null", gson.toJson(new ClassWithNoFields() {
      // empty anonymous class
    }));
  }

  @Test
  public void testPrimitiveArrayFieldSerialization() {
    PrimitiveArray target = new PrimitiveArray(new long[] { 1L, 2L, 3L });
    assertEquals(target.getExpectedJson(), gson.toJson(target));
  }

  /**
   * Tests that a class field with type Object can be serialized properly.
   * See issue 54
   */
  @Test
  public void testClassWithObjectFieldSerialization() {
    ClassWithObjectField obj = new ClassWithObjectField();
    obj.member = "abc";
    String json = gson.toJson(obj);
    assertTrue(json.contains("abc"));
  }

  private static class ClassWithObjectField {
    @SuppressWarnings("unused")
    Object member;
  }

  @Test
  public void testInnerClassSerialization() {
    Parent p = new Parent();
    Parent.Child c = p.new Child();
    String json = gson.toJson(c);
    assertTrue(json.contains("value2"));
    assertFalse(json.contains("value1"));
  }

  @Test
  public void testInnerClassDeserialization() {
    final Parent p = new Parent();
    Gson gson = new GsonBuilder().registerTypeAdapter(
        Parent.Child.class, new InstanceCreator<Parent.Child>() {
      @Override public Parent.Child createInstance(Type type) {
        return p.new Child();
      }
    }).create();
    String json = "{'value2':3}";
    Parent.Child c = gson.fromJson(json, Parent.Child.class);
    assertEquals(3, c.value2);
  }

  private static class Parent {
    @SuppressWarnings("unused")
    int value1 = 1;
    private class Child {
      int value2 = 2;
    }
  }

  private static class ArrayOfArrays {
    private final BagOfPrimitives[][] elements;
    public ArrayOfArrays() {
      elements = new BagOfPrimitives[3][2];
      for (int i = 0; i < elements.length; ++i) {
        BagOfPrimitives[] row = elements[i];
        for (int j = 0; j < row.length; ++j) {
          row[j] = new BagOfPrimitives(i+j, i*j, false, i+"_"+j);
        }
      }
    }
    public String getExpectedJson() {
      StringBuilder sb = new StringBuilder("{\"elements\":[");
      boolean first = true;
      for (BagOfPrimitives[] row : elements) {
        if (first) {
          first = false;
        } else {
          sb.append(",");
        }
        boolean firstOfRow = true;
        sb.append("[");
        for (BagOfPrimitives element : row) {
          if (firstOfRow) {
            firstOfRow = false;
          } else {
            sb.append(",");
          }
          sb.append(element.getExpectedJson());
        }
        sb.append("]");
      }
      sb.append("]}");
      return sb.toString();
    }
  }

  private static class ClassWithPrivateNoArgsConstructor {
    public int a;
    private ClassWithPrivateNoArgsConstructor() {
      a = 10;
    }
  }

  /**
   * In response to Issue 41 http://code.google.com/p/google-gson/issues/detail?id=41
   */
  @Test
  public void testObjectFieldNamesWithoutQuotesDeserialization() {
    String json = "{longValue:1,'booleanValue':true,\"stringValue\":'bar'}";
    BagOfPrimitives bag = gson.fromJson(json, BagOfPrimitives.class);
    assertEquals(1, bag.longValue);
    assertTrue(bag.booleanValue);
    assertEquals("bar", bag.stringValue);
  }

  @Test
  public void testStringFieldWithNumberValueDeserialization() {
    String json = "{\"stringValue\":1}";
    BagOfPrimitives bag = gson.fromJson(json, BagOfPrimitives.class);
    assertEquals("1", bag.stringValue);

    json = "{\"stringValue\":1.5E+6}";
    bag = gson.fromJson(json, BagOfPrimitives.class);
    assertEquals("1.5E+6", bag.stringValue);

    json = "{\"stringValue\":true}";
    bag = gson.fromJson(json, BagOfPrimitives.class);
    assertEquals("true", bag.stringValue);
  }

  /**
   * Created to reproduce issue 140
   */
  @Test
  public void testStringFieldWithEmptyValueSerialization() {
    ClassWithEmptyStringFields target = new ClassWithEmptyStringFields();
    target.a = "5794749";
    String json = gson.toJson(target);
    assertTrue(json.contains("\"a\":\"5794749\""));
    assertTrue(json.contains("\"b\":\"\""));
    assertTrue(json.contains("\"c\":\"\""));
  }

  /**
   * Created to reproduce issue 140
   */
  @Test
  public void testStringFieldWithEmptyValueDeserialization() {
    String json = "{a:\"5794749\",b:\"\",c:\"\"}";
    ClassWithEmptyStringFields target = gson.fromJson(json, ClassWithEmptyStringFields.class);
    assertEquals("5794749", target.a);
    assertEquals("", target.b);
    assertEquals("", target.c);
  }

  private static class ClassWithEmptyStringFields {
    String a = "";
    String b = "";
    String c = "";
  }

  @Test
  public void testJsonObjectSerialization() {
    Gson gson = new GsonBuilder().serializeNulls().create();
    JsonObject obj = new JsonObject();
    String json = gson.toJson(obj);
    assertEquals("{}", json);
  }

  /**
   * Test for issue 215.
   */
  @Test
  public void testSingletonLists() {
    Gson gson = new Gson();
    Product product = new Product();
    assertEquals("{\"attributes\":[],\"departments\":[]}",
        gson.toJson(product));
    gson.fromJson(gson.toJson(product), Product.class);

    product.departments.add(new Department());
    assertEquals("{\"attributes\":[],\"departments\":[{\"name\":\"abc\",\"code\":\"123\"}]}",
        gson.toJson(product));
    gson.fromJson(gson.toJson(product), Product.class);

    product.attributes.add("456");
    assertEquals("{\"attributes\":[\"456\"],\"departments\":[{\"name\":\"abc\",\"code\":\"123\"}]}",
        gson.toJson(product));
    gson.fromJson(gson.toJson(product), Product.class);
  }

  static final class Department {
    public String name = "abc";
    public String code = "123";
  }

  static final class Product {
    private List<String> attributes = new ArrayList<>();
    private List<Department> departments = new ArrayList<>();
  }

  // http://code.google.com/p/google-gson/issues/detail?id=270
  @Test
  public void testDateAsMapObjectField() {
    HasObjectMap a = new HasObjectMap();
    a.map.put("date", new Date(0));
    if (JavaVersion.isJava9OrLater()) {
      assertEquals("{\"map\":{\"date\":\"Dec 31, 1969, 4:00:00 PM\"}}", gson.toJson(a));
    } else {
      assertEquals("{\"map\":{\"date\":\"Dec 31, 1969 4:00:00 PM\"}}", gson.toJson(a));
    }
  }

  static class HasObjectMap {
    Map<String, Object> map = new HashMap<>();
  }

  /**
   * Tests serialization of a class with {@code static} field.
   *
   * <p>Important: It is not documented that this is officially supported; this
   * test just checks the current behavior.
   */
  @Test
  public void testStaticFieldSerialization() {
    // By default Gson should ignore static fields
    assertEquals("{}", gson.toJson(new ClassWithStaticField()));

    Gson gson = new GsonBuilder()
        // Include static fields
        .excludeFieldsWithModifiers(0)
        .create();

    String json = gson.toJson(new ClassWithStaticField());
    assertEquals("{\"s\":\"initial\"}", json);

    json = gson.toJson(new ClassWithStaticFinalField());
    assertEquals("{\"s\":\"initial\"}", json);
  }

  /**
   * Tests deserialization of a class with {@code static} field.
   *
   * <p>Important: It is not documented that this is officially supported; this
   * test just checks the current behavior.
   */
  @Test
  public void testStaticFieldDeserialization() {
    // By default Gson should ignore static fields
    gson.fromJson("{\"s\":\"custom\"}", ClassWithStaticField.class);
    assertEquals("initial", ClassWithStaticField.s);

    Gson gson = new GsonBuilder()
        // Include static fields
        .excludeFieldsWithModifiers(0)
        .create();

    String oldValue = ClassWithStaticField.s;
    try {
      ClassWithStaticField obj = gson.fromJson("{\"s\":\"custom\"}", ClassWithStaticField.class);
      assertNotNull(obj);
      assertEquals("custom", ClassWithStaticField.s);
    } finally {
      ClassWithStaticField.s = oldValue;
    }

    try {
      gson.fromJson("{\"s\":\"custom\"}", ClassWithStaticFinalField.class);
      fail();
    } catch (JsonIOException e) {
      assertEquals("Cannot set value of 'static final' field 'com.google.gson.functional.ObjectTest$ClassWithStaticFinalField#s'",
          e.getMessage());
    }
  }

  static class ClassWithStaticField {
    static String s = "initial";
  }

  static class ClassWithStaticFinalField {
    static final String s = "initial";
  }

  @Test
  public void testThrowingDefaultConstructor() {
    try {
      gson.fromJson("{}", ClassWithThrowingConstructor.class);
      fail();
    }
    // TODO: Adjust this once Gson throws more specific exception type
    catch (RuntimeException e) {
      assertEquals("Failed to invoke constructor 'com.google.gson.functional.ObjectTest$ClassWithThrowingConstructor()' with no args",
          e.getMessage());
      assertSame(ClassWithThrowingConstructor.thrownException, e.getCause());
    }
  }

  static class ClassWithThrowingConstructor {
    static final RuntimeException thrownException = new RuntimeException("Custom exception");

    public ClassWithThrowingConstructor() {
      throw thrownException;
    }
  }
}
