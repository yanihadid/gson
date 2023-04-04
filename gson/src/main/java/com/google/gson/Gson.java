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

package com.google.gson;

import com.google.gson.elements.JsonElement;
import com.google.gson.elements.JsonNull;
import com.google.gson.exception.JsonIOException;
import com.google.gson.exception.JsonSyntaxException;
import com.google.gson.internal.*;
import com.google.gson.internal.GsonBuildConfig;
import com.google.gson.internal.bind.ArrayTypeAdapter;
import com.google.gson.internal.bind.CollectionTypeAdapterFactory;
import com.google.gson.internal.bind.DateTypeAdapter;
import com.google.gson.internal.bind.JsonAdapterAnnotationTypeAdapterFactory;
import com.google.gson.internal.bind.JsonTreeReader;
import com.google.gson.internal.bind.JsonTreeWriter;
import com.google.gson.internal.bind.MapTypeAdapterFactory;
import com.google.gson.internal.bind.NumberTypeAdapter;
import com.google.gson.internal.bind.ObjectTypeAdapter;
import com.google.gson.internal.bind.ReflectiveTypeAdapterFactory;
import com.google.gson.internal.bind.SerializationDelegatingTypeAdapter;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.internal.sql.SqlTypesSupport;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * This is the main class for using Gson. Gson is typically used by first constructing a
 * Gson instance and then invoking {@link #toJson(Object)} or {@link #fromJson(String, Class)}
 * methods on it. Gson instances are Thread-safe so you can reuse them freely across multiple
 * threads.
 *
 * <p>You can create a Gson instance by invoking {@code new Gson()} if the default configuration
 * is all you need. You can also use {@link GsonBuilder} to build a Gson instance with various
 * configuration options such as versioning support, pretty printing, custom
 * {@link JsonSerializer}s, {@link JsonDeserializer}s, and {@link InstanceCreator}s.</p>
 *
 * <p>Here is an example of how Gson is used for a simple Class:
 *
 * <pre>
 * Gson gson = new Gson(); // Or use new GsonBuilder().create();
 * MyType target = new MyType();
 * String json = gson.toJson(target); // serializes target to JSON
 * MyType target2 = gson.fromJson(json, MyType.class); // deserializes json into target2
 * </pre>
 *
 * <p>If the type of the object that you are converting is a {@code ParameterizedType}
 * (i.e. has at least one type argument, for example {@code List<MyType>}) then for
 * deserialization you must use a {@code fromJson} method with {@link Type} or {@link TypeToken}
 * parameter to specify the parameterized type. For serialization specifying a {@code Type}
 * or {@code TypeToken} is optional, otherwise Gson will use the runtime type of the object.
 * {@link TypeToken} is a class provided by Gson which helps creating parameterized types.
 * Here is an example showing how this can be done:
 * <pre>
 * TypeToken&lt;List&lt;MyType&gt;&gt; listType = new TypeToken&lt;List&lt;MyType&gt;&gt;() {};
 * List&lt;MyType&gt; target = new LinkedList&lt;MyType&gt;();
 * target.add(new MyType(1, "abc"));
 *
 * Gson gson = new Gson();
 * // For serialization you normally do not have to specify the type, Gson will use
 * // the runtime type of the objects, however you can also specify it explicitly
 * String json = gson.toJson(target, listType.getType());
 *
 * // But for deserialization you have to specify the type
 * List&lt;MyType&gt; target2 = gson.fromJson(json, listType);
 * </pre>
 *
 * <p>See the <a href="https://github.com/google/gson/blob/master/UserGuide.md">Gson User Guide</a>
 * for a more complete set of examples.</p>
 *
 * <h2>Lenient JSON handling</h2>
 * For legacy reasons most of the {@code Gson} methods allow JSON data which does not
 * comply with the JSON specification, regardless of whether {@link GsonBuilder#setLenient()}
 * is used or not. If this behavior is not desired, the following workarounds can be used:
 *
 * <h3>Serialization</h3>
 * <ol>
 *   <li>Use {@link #getAdapter(Class)} to obtain the adapter for the type to be serialized
 *   <li>When using an existing {@code JsonWriter}, manually apply the writer settings of this
 *       {@code Gson} instance listed by {@link #newJsonWriter(Writer)}.<br>
 *       Otherwise, when not using an existing {@code JsonWriter}, use {@link #newJsonWriter(Writer)}
 *       to construct one.
 *   <li>Call {@link TypeAdapter#write(JsonWriter, Object)}
 * </ol>
 *
 * <h3>Deserialization</h3>
 * <ol>
 *   <li>Use {@link #getAdapter(Class)} to obtain the adapter for the type to be deserialized
 *   <li>When using an existing {@code JsonReader}, manually apply the reader settings of this
 *       {@code Gson} instance listed by {@link #newJsonReader(Reader)}.<br>
 *       Otherwise, when not using an existing {@code JsonReader}, use {@link #newJsonReader(Reader)}
 *       to construct one.
 *   <li>Call {@link TypeAdapter#read(JsonReader)}
 *   <li>Call {@link JsonReader#peek()} and verify that the result is {@link JsonToken#END_DOCUMENT}
 *       to make sure there is no trailing data
 * </ol>
 *
 * @see TypeToken
 *
 * @author Inderjeet Singh
 * @author Joel Leitch
 * @author Jesse Wilson
 */
public final class Gson {
  static final boolean DEFAULT_JSON_NON_EXECUTABLE = false;
  static final boolean DEFAULT_LENIENT = false;
  static final boolean DEFAULT_PRETTY_PRINT = false;
  static final boolean DEFAULT_ESCAPE_HTML = true;
  static final boolean DEFAULT_SERIALIZE_NULLS = false;
  static final boolean DEFAULT_COMPLEX_MAP_KEYS = false;
  static final boolean DEFAULT_SPECIALIZE_FLOAT_VALUES = false;
  static final boolean DEFAULT_USE_JDK_UNSAFE = true;
  static final String DEFAULT_DATE_PATTERN = null;
  static final GsonBuilder.FieldNamingStrategy DEFAULT_FIELD_NAMING_STRATEGY = GsonBuilder.FieldNamingPolicy.IDENTITY;
  static final ToNumberStrategy DEFAULT_OBJECT_TO_NUMBER_STRATEGY = ToNumberPolicy.DOUBLE;
  static final ToNumberStrategy DEFAULT_NUMBER_TO_NUMBER_STRATEGY = ToNumberPolicy.LAZILY_PARSED_NUMBER;

  private static final String JSON_NON_EXECUTABLE_PREFIX = ")]}'\n";

  /**
   * This thread local guards against reentrant calls to {@link #getAdapter(TypeToken)}.
   * In certain object graphs, creating an adapter for a type may recursively
   * require an adapter for the same type! Without intervention, the recursive
   * lookup would stack overflow. We cheat by returning a proxy type adapter,
   * {@link FutureTypeAdapter}, which is wired up once the initial adapter has
   * been created.
   *
   * <p>The map stores the type adapters for ongoing {@code getAdapter} calls,
   * with the type token provided to {@code getAdapter} as key and either
   * {@code FutureTypeAdapter} or a regular {@code TypeAdapter} as value.
   */
  private final ThreadLocal<Map<TypeToken<?>, TypeAdapter<?>>> threadLocalAdapterResults = new ThreadLocal<>();

  private final ConcurrentMap<TypeToken<?>, TypeAdapter<?>> typeTokenCache = new ConcurrentHashMap<>();

  private final ConstructorConstructor constructorConstructor;
  private final JsonAdapterAnnotationTypeAdapterFactory jsonAdapterFactory;

  final List<TypeAdapterFactory> factories;

  final Excluder excluder;
  final GsonBuilder.FieldNamingStrategy fieldNamingStrategy;
  final Map<Type, InstanceCreator<?>> instanceCreators;
  final boolean serializeNulls;
  final boolean complexMapKeySerialization;
  final boolean generateNonExecutableJson;
  final boolean htmlSafe;
  final boolean prettyPrinting;
  final boolean lenient;
  final boolean serializeSpecialFloatingPointValues;
  final boolean useJdkUnsafe;
  final String datePattern;
  final int dateStyle;
  final int timeStyle;
  final LongSerializationPolicy longSerializationPolicy;
  final List<TypeAdapterFactory> builderFactories;
  final List<TypeAdapterFactory> builderHierarchyFactories;
  final ToNumberStrategy objectToNumberStrategy;
  final ToNumberStrategy numberToNumberStrategy;
  final List<ReflectionAccessFilter> reflectionFilters;

  /**
   * Constructs a Gson object with default configuration. The default configuration has the
   * following settings:
   * <ul>
   *   <li>The JSON generated by <code>toJson</code> methods is in compact representation. This
   *   means that all the unneeded white-space is removed. You can change this behavior with
   *   {@link GsonBuilder#setPrettyPrinting()}. </li>
   *   <li>The generated JSON omits all the fields that are null. Note that nulls in arrays are
   *   kept as is since an array is an ordered list. Moreover, if a field is not null, but its
   *   generated JSON is empty, the field is kept. You can configure Gson to serialize null values
   *   by setting {@link GsonBuilder#serializeNulls()}.</li>
   *   <li>Gson provides default serialization and deserialization for Enums, {@link Map},
   *   {@link java.net.URL}, {@link java.net.URI}, {@link java.util.Locale}, {@link java.util.Date},
   *   {@link java.math.BigDecimal}, and {@link java.math.BigInteger} classes. If you would prefer
   *   to change the default representation, you can do so by registering a type adapter through
   *   {@link GsonBuilder#registerTypeAdapter(Type, Object)}. </li>
   *   <li>The default Date format is same as {@link java.text.DateFormat#DEFAULT}. This format
   *   ignores the millisecond portion of the date during serialization. You can change
   *   this by invoking {@link GsonBuilder#setDateFormat(int)} or
   *   {@link GsonBuilder#setDateFormat(String)}. </li>
   *   <li>By default, Gson ignores the {@link com.google.gson.annotations.Expose} annotation.
   *   You can enable Gson to serialize/deserialize only those fields marked with this annotation
   *   through {@link GsonBuilder#excludeFieldsWithoutExposeAnnotation()}. </li>
   *   <li>By default, Gson ignores the {@link com.google.gson.annotations.Since} annotation. You
   *   can enable Gson to use this annotation through {@link GsonBuilder#setVersion(double)}.</li>
   *   <li>The default field naming policy for the output JSON is same as in Java. So, a Java class
   *   field <code>versionNumber</code> will be output as <code>&quot;versionNumber&quot;</code> in
   *   JSON. The same rules are applied for mapping incoming JSON to the Java classes. You can
   *   change this policy through {@link GsonBuilder#setFieldNamingPolicy(GsonBuilder.FieldNamingPolicy)}.</li>
   *   <li>By default, Gson excludes <code>transient</code> or <code>static</code> fields from
   *   consideration for serialization and deserialization. You can change this behavior through
   *   {@link GsonBuilder#excludeFieldsWithModifiers(int...)}.</li>
   * </ul>
   */
  public Gson() {
    this(Excluder.DEFAULT, DEFAULT_FIELD_NAMING_STRATEGY,
        Collections.<Type, InstanceCreator<?>>emptyMap(), DEFAULT_SERIALIZE_NULLS,
        DEFAULT_COMPLEX_MAP_KEYS, DEFAULT_JSON_NON_EXECUTABLE, DEFAULT_ESCAPE_HTML,
        DEFAULT_PRETTY_PRINT, DEFAULT_LENIENT, DEFAULT_SPECIALIZE_FLOAT_VALUES,
        DEFAULT_USE_JDK_UNSAFE,
        LongSerializationPolicy.DEFAULT, DEFAULT_DATE_PATTERN, DateFormat.DEFAULT, DateFormat.DEFAULT,
        Collections.<TypeAdapterFactory>emptyList(), Collections.<TypeAdapterFactory>emptyList(),
        Collections.<TypeAdapterFactory>emptyList(), DEFAULT_OBJECT_TO_NUMBER_STRATEGY, DEFAULT_NUMBER_TO_NUMBER_STRATEGY,
        Collections.<ReflectionAccessFilter>emptyList());
  }

  Gson(Excluder excluder, GsonBuilder.FieldNamingStrategy fieldNamingStrategy,
      Map<Type, InstanceCreator<?>> instanceCreators, boolean serializeNulls,
      boolean complexMapKeySerialization, boolean generateNonExecutableGson, boolean htmlSafe,
      boolean prettyPrinting, boolean lenient, boolean serializeSpecialFloatingPointValues,
      boolean useJdkUnsafe,
      LongSerializationPolicy longSerializationPolicy, String datePattern, int dateStyle,
      int timeStyle, List<TypeAdapterFactory> builderFactories,
      List<TypeAdapterFactory> builderHierarchyFactories,
      List<TypeAdapterFactory> factoriesToBeAdded,
      ToNumberStrategy objectToNumberStrategy, ToNumberStrategy numberToNumberStrategy,
      List<ReflectionAccessFilter> reflectionFilters) {
    this.excluder = excluder;
    this.fieldNamingStrategy = fieldNamingStrategy;
    this.instanceCreators = instanceCreators;
    this.constructorConstructor = new ConstructorConstructor(instanceCreators, useJdkUnsafe, reflectionFilters);
    this.serializeNulls = serializeNulls;
    this.complexMapKeySerialization = complexMapKeySerialization;
    this.generateNonExecutableJson = generateNonExecutableGson;
    this.htmlSafe = htmlSafe;
    this.prettyPrinting = prettyPrinting;
    this.lenient = lenient;
    this.serializeSpecialFloatingPointValues = serializeSpecialFloatingPointValues;
    this.useJdkUnsafe = useJdkUnsafe;
    this.longSerializationPolicy = longSerializationPolicy;
    this.datePattern = datePattern;
    this.dateStyle = dateStyle;
    this.timeStyle = timeStyle;
    this.builderFactories = builderFactories;
    this.builderHierarchyFactories = builderHierarchyFactories;
    this.objectToNumberStrategy = objectToNumberStrategy;
    this.numberToNumberStrategy = numberToNumberStrategy;
    this.reflectionFilters = reflectionFilters;

    List<TypeAdapterFactory> factories = new ArrayList<>();
    this.jsonAdapterFactory = new JsonAdapterAnnotationTypeAdapterFactory(constructorConstructor);

  }

  public void setFactories(){
    // built-in type adapters that cannot be overridden
    factories.add(TypeAdapters.JSON_ELEMENT_FACTORY);
    factories.add(ObjectTypeAdapter.getFactory(objectToNumberStrategy));

    // the excluder must precede all adapters that handle user-defined types
    factories.add(excluder);

    // users' type adapters
    factories.addAll(factoriesToBeAdded);

    // type adapters for basic platform types
    factories.add(TypeAdapters.STRING_FACTORY);
    factories.add(TypeAdapters.INTEGER_FACTORY);
    factories.add(TypeAdapters.BOOLEAN_FACTORY);
    factories.add(TypeAdapters.BYTE_FACTORY);
    factories.add(TypeAdapters.SHORT_FACTORY);
    TypeAdapter<Number> longAdapter = longAdapter(longSerializationPolicy);
    factories.add(TypeAdapters.newFactory(long.class, Long.class, longAdapter));
    factories.add(TypeAdapters.newFactory(double.class, Double.class,
            doubleAdapter(serializeSpecialFloatingPointValues)));
    factories.add(TypeAdapters.newFactory(float.class, Float.class,
            floatAdapter(serializeSpecialFloatingPointValues)));
    factories.add(NumberTypeAdapter.getFactory(numberToNumberStrategy));
    factories.add(TypeAdapters.ATOMIC_INTEGER_FACTORY);
    factories.add(TypeAdapters.ATOMIC_BOOLEAN_FACTORY);
    factories.add(TypeAdapters.newFactory(AtomicLong.class, atomicLongAdapter(longAdapter)));
    factories.add(TypeAdapters.newFactory(AtomicLongArray.class, atomicLongArrayAdapter(longAdapter)));
    factories.add(TypeAdapters.ATOMIC_INTEGER_ARRAY_FACTORY);
    factories.add(TypeAdapters.CHARACTER_FACTORY);
    factories.add(TypeAdapters.STRING_BUILDER_FACTORY);
    factories.add(TypeAdapters.STRING_BUFFER_FACTORY);
    factories.add(TypeAdapters.newFactory(BigDecimal.class, TypeAdapters.BIG_DECIMAL));
    factories.add(TypeAdapters.newFactory(BigInteger.class, TypeAdapters.BIG_INTEGER));
    // Add adapter for LazilyParsedNumber because user can obtain it from Gson and then try to serialize it again
    factories.add(TypeAdapters.newFactory(LazilyParsedNumber.class, TypeAdapters.LAZILY_PARSED_NUMBER));
    factories.add(TypeAdapters.URL_FACTORY);
    factories.add(TypeAdapters.URI_FACTORY);
    factories.add(TypeAdapters.UUID_FACTORY);
    factories.add(TypeAdapters.CURRENCY_FACTORY);
    factories.add(TypeAdapters.LOCALE_FACTORY);
    factories.add(TypeAdapters.INET_ADDRESS_FACTORY);
    factories.add(TypeAdapters.BIT_SET_FACTORY);
    factories.add(DateTypeAdapter.FACTORY);
    factories.add(TypeAdapters.CALENDAR_FACTORY);

    if (SqlTypesSupport.SUPPORTS_SQL_TYPES) {
      factories.add(SqlTypesSupport.TIME_FACTORY);
      factories.add(SqlTypesSupport.DATE_FACTORY);
      factories.add(SqlTypesSupport.TIMESTAMP_FACTORY);
    }

    factories.add(ArrayTypeAdapter.FACTORY);
    factories.add(TypeAdapters.CLASS_FACTORY);

    // type adapters for composite and user-defined types
    factories.add(new CollectionTypeAdapterFactory(constructorConstructor));
    factories.add(new MapTypeAdapterFactory(constructorConstructor, complexMapKeySerialization));
    factories.add(jsonAdapterFactory);
    factories.add(TypeAdapters.ENUM_FACTORY);
    factories.add(new ReflectiveTypeAdapterFactory(
            constructorConstructor, fieldNamingStrategy, excluder, jsonAdapterFactory, reflectionFilters));

    this.factories = Collections.unmodifiableList(factories);
  }

  /**
   * Returns a new GsonBuilder containing all custom factories and configuration used by the current
   * instance.
   *
   * @return a GsonBuilder instance.
   * @since 2.8.3
   */
  public GsonBuilder newBuilder() {
    return new GsonBuilder(this);
  }

  /**
   * @deprecated This method by accident exposes an internal Gson class; it might be removed in a
   * future version.
   */
  @Deprecated
  public Excluder excluder() {
    return excluder;
  }

  /**
   * Returns the field naming strategy used by this Gson instance.
   *
   * @see GsonBuilder#setFieldNamingStrategy(GsonBuilder.FieldNamingStrategy)
   */
  public GsonBuilder.FieldNamingStrategy fieldNamingStrategy() {
    return fieldNamingStrategy;
  }

  /**
   * Returns whether this Gson instance is serializing JSON object properties with
   * {@code null} values, or just omits them.
   *
   * @see GsonBuilder#serializeNulls()
   */
  public boolean serializeNulls() {
    return serializeNulls;
  }

  /**
   * Returns whether this Gson instance produces JSON output which is
   * HTML-safe, that means all HTML characters are escaped.
   *
   * @see GsonBuilder#disableHtmlEscaping()
   */
  public boolean htmlSafe() {
    return htmlSafe;
  }

  private TypeAdapter<Number> doubleAdapter(boolean serializeSpecialFloatingPointValues) {
    if (serializeSpecialFloatingPointValues) {
      return TypeAdapters.DOUBLE;
    }
    return new TypeAdapter<Number>() {
      @Override public Double read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
          in.nextNull();
          return null;
        }
        return in.nextDouble();
      }
      @Override public void write(JsonWriter out, Number value) throws IOException {
        if (value == null) {
          out.nullValue();
          return;
        }
        double doubleValue = value.doubleValue();
        checkValidFloatingPoint(doubleValue);
        out.value(doubleValue);
      }
    };
  }

  private TypeAdapter<Number> floatAdapter(boolean serializeSpecialFloatingPointValues) {
    if (serializeSpecialFloatingPointValues) {
      return TypeAdapters.FLOAT;
    }
    return new TypeAdapter<Number>() {
      @Override public Float read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
          in.nextNull();
          return null;
        }
        return (float) in.nextDouble();
      }
      @Override public void write(JsonWriter out, Number value) throws IOException {
        if (value == null) {
          out.nullValue();
          return;
        }
        float floatValue = value.floatValue();
        checkValidFloatingPoint(floatValue);
        // For backward compatibility don't call `JsonWriter.value(float)` because that method has
        // been newly added and not all custom JsonWriter implementations might override it yet
        Number floatNumber = value instanceof Float ? value : floatValue;
        out.value(floatNumber);
      }
    };
  }

  static void checkValidFloatingPoint(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(value
          + " is not a valid double value as per JSON specification. To override this"
          + " behavior, use GsonBuilder.serializeSpecialFloatingPointValues() method.");
    }
  }

  private static TypeAdapter<Number> longAdapter(LongSerializationPolicy longSerializationPolicy) {
    if (longSerializationPolicy == LongSerializationPolicy.DEFAULT) {
      return TypeAdapters.LONG;
    }
    return new TypeAdapter<Number>() {
      @Override public Number read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
          in.nextNull();
          return null;
        }
        return in.nextLong();
      }
      @Override public void write(JsonWriter out, Number value) throws IOException {
        if (value == null) {
          out.nullValue();
          return;
        }
        out.value(value.toString());
      }
    };
  }

  private static TypeAdapter<AtomicLong> atomicLongAdapter(final TypeAdapter<Number> longAdapter) {
    return new TypeAdapter<AtomicLong>() {
      @Override public void write(JsonWriter out, AtomicLong value) throws IOException {
        longAdapter.write(out, value.get());
      }
      @Override public AtomicLong read(JsonReader in) throws IOException {
        Number value = longAdapter.read(in);
        return new AtomicLong(value.longValue());
      }
    }.nullSafe();
  }

  private static TypeAdapter<AtomicLongArray> atomicLongArrayAdapter(final TypeAdapter<Number> longAdapter) {
    return new TypeAdapter<AtomicLongArray>() {
      @Override public void write(JsonWriter out, AtomicLongArray value) throws IOException {
        out.beginArray();
        for (int i = 0, length = value.length(); i < length; i++) {
          longAdapter.write(out, value.get(i));
        }
        out.endArray();
      }
      @Override public AtomicLongArray read(JsonReader in) throws IOException {
        List<Long> list = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            long value = longAdapter.read(in).longValue();
            list.add(value);
        }
        in.endArray();
        int length = list.size();
        AtomicLongArray array = new AtomicLongArray(length);
        for (int i = 0; i < length; ++i) {
          array.set(i, list.get(i));
        }
        return array;
      }
    }.nullSafe();
  }

  /**
   * Returns the type adapter for {@code type}.
   *
   * <p>When calling this method concurrently from multiple threads and requesting
   * an adapter for the same type this method may return different {@code TypeAdapter}
   * instances. However, that should normally not be an issue because {@code TypeAdapter}
   * implementations are supposed to be stateless.
   *
   * @throws IllegalArgumentException if this Gson instance cannot serialize and
   *     deserialize {@code type}.
   */
  public <T> TypeAdapter<T> getAdapter(TypeToken<T> type) {
    Objects.requireNonNull(type, "type must not be null");
    TypeAdapter<?> cached = typeTokenCache.get(type);
    if (cached != null) {
      @SuppressWarnings("unchecked")
      TypeAdapter<T> adapter = (TypeAdapter<T>) cached;
      return adapter;
    }

    Map<TypeToken<?>, TypeAdapter<?>> threadCalls = threadLocalAdapterResults.get();
    boolean isInitialAdapterRequest = false;
    if (threadCalls == null) {
      threadCalls = new HashMap<>();
      threadLocalAdapterResults.set(threadCalls);
      isInitialAdapterRequest = true;
    } else {
      // the key and value type parameters always agree
      @SuppressWarnings("unchecked")
      TypeAdapter<T> ongoingCall = (TypeAdapter<T>) threadCalls.get(type);
      if (ongoingCall != null) {
        return ongoingCall;
      }
    }

    TypeAdapter<T> candidate = null;
    try {
      FutureTypeAdapter<T> call = new FutureTypeAdapter<>();
      threadCalls.put(type, call);

      for (TypeAdapterFactory factory : factories) {
        candidate = factory.create(this, type);
        if (candidate != null) {
          call.setDelegate(candidate);
          // Replace future adapter with actual adapter
          threadCalls.put(type, candidate);
          break;
        }
      }
    } finally {
      if (isInitialAdapterRequest) {
        threadLocalAdapterResults.remove();
      }
    }

    if (candidate == null) {
      throw new IllegalArgumentException("GSON (" + GsonBuildConfig.VERSION + ") cannot handle " + type);
    }

    if (isInitialAdapterRequest) {
      /*
       * Publish resolved adapters to all threads
       * Can only do this for the initial request because cyclic dependency TypeA -> TypeB -> TypeA
       * would otherwise publish adapter for TypeB which uses not yet resolved adapter for TypeA
       * See https://github.com/google/gson/issues/625
       */
      typeTokenCache.putAll(threadCalls);
    }
    return candidate;
  }

  /**
   * This method is used to get an alternate type adapter for the specified type. This is used
   * to access a type adapter that is overridden by a {@link TypeAdapterFactory} that you
   * may have registered. This features is typically used when you want to register a type
   * adapter that does a little bit of work but then delegates further processing to the Gson
   * default type adapter. Here is an example:
   * <p>Let's say we want to write a type adapter that counts the number of objects being read
   *  from or written to JSON. We can achieve this by writing a type adapter factory that uses
   *  the <code>getDelegateAdapter</code> method:
   *  <pre> {@code
   *  class StatsTypeAdapterFactory implements TypeAdapterFactory {
   *    public int numReads = 0;
   *    public int numWrites = 0;
   *    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
   *      final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
   *      return new TypeAdapter<T>() {
   *        public void write(JsonWriter out, T value) throws IOException {
   *          ++numWrites;
   *          delegate.write(out, value);
   *        }
   *        public T read(JsonReader in) throws IOException {
   *          ++numReads;
   *          return delegate.read(in);
   *        }
   *      };
   *    }
   *  }
   *  } </pre>
   *  This factory can now be used like this:
   *  <pre> {@code
   *  StatsTypeAdapterFactory stats = new StatsTypeAdapterFactory();
   *  Gson gson = new GsonBuilder().registerTypeAdapterFactory(stats).create();
   *  // Call gson.toJson() and fromJson methods on objects
   *  System.out.println("Num JSON reads" + stats.numReads);
   *  System.out.println("Num JSON writes" + stats.numWrites);
   *  }</pre>
   *  Note that this call will skip all factories registered before {@code skipPast}. In case of
   *  multiple TypeAdapterFactories registered it is up to the caller of this function to insure
   *  that the order of registration does not prevent this method from reaching a factory they
   *  would expect to reply from this call.
   *  Note that since you can not override type adapter factories for String and Java primitive
   *  types, our stats factory will not count the number of String or primitives that will be
   *  read or written.
   * @param skipPast The type adapter factory that needs to be skipped while searching for
   *   a matching type adapter. In most cases, you should just pass <i>this</i> (the type adapter
   *   factory from where {@code getDelegateAdapter} method is being invoked).
   * @param type Type for which the delegate adapter is being searched for.
   *
   * @since 2.2
   */
  public <T> TypeAdapter<T> getDelegateAdapter(TypeAdapterFactory skipPast, TypeToken<T> type) {
    // Hack. If the skipPast factory isn't registered, assume the factory is being requested via
    // our @JsonAdapter annotation.
    if (!factories.contains(skipPast)) {
      skipPast = jsonAdapterFactory;
    }

    boolean skipPastFound = false;
    for (TypeAdapterFactory factory : factories) {
      if (!skipPastFound) {
        if (factory == skipPast) {
          skipPastFound = true;
        }
        continue;
      }

      TypeAdapter<T> candidate = factory.create(this, type);
      if (candidate != null) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("GSON cannot serialize " + type);
  }

  /**
   * Returns the type adapter for {@code type}.
   *
   * @throws IllegalArgumentException if this Gson instance cannot serialize and
   *     deserialize {@code type}.
   */
  public <T> TypeAdapter<T> getAdapter(Class<T> type) {
    return getAdapter(TypeToken.get(type));
  }

  /**
   * This method serializes the specified object into its equivalent representation as a tree of
   * {@link JsonElement}s. This method should be used when the specified object is not a generic
   * type. This method uses {@link Class#getClass()} to get the type for the specified object, but
   * the {@code getClass()} loses the generic type information because of the Type Erasure feature
   * of Java. Note that this method works fine if any of the object fields are of generic type,
   * just the object itself should not be of a generic type. If the object is of generic type, use
   * {@link #toJsonTree(Object, Type)} instead.
   *
   * @param src the object for which JSON representation is to be created
   * @return JSON representation of {@code src}.
   * @since 1.4
   *
   * @see #toJsonTree(Object, Type)
   */
  public JsonElement toJsonTree(Object src) {
    if (src == null) {
      return JsonNull.INSTANCE;
    }
    return toJsonTree(src, src.getClass());
  }

  /**
   * This method serializes the specified object, including those of generic types, into its
   * equivalent representation as a tree of {@link JsonElement}s. This method must be used if the
   * specified object is a generic type. For non-generic objects, use {@link #toJsonTree(Object)}
   * instead.
   *
   * @param src the object for which JSON representation is to be created
   * @param typeOfSrc The specific genericized type of src. You can obtain
   * this type by using the {@link TypeToken} class. For example,
   * to get the type for {@code Collection<Foo>}, you should use:
   * <pre>
   * Type typeOfSrc = new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}.getType();
   * </pre>
   * @return Json representation of {@code src}
   * @since 1.4
   *
   * @see #toJsonTree(Object)
   */
  public JsonElement toJsonTree(Object src, Type typeOfSrc) {
    JsonTreeWriter writer = new JsonTreeWriter();
    toJson(src, typeOfSrc, writer);
    return writer.get();
  }

  /**
   * This method serializes the specified object into its equivalent JSON representation.
   * This method should be used when the specified object is not a generic type. This method uses
   * {@link Class#getClass()} to get the type for the specified object, but the
   * {@code getClass()} loses the generic type information because of the Type Erasure feature
   * of Java. Note that this method works fine if any of the object fields are of generic type,
   * just the object itself should not be of a generic type. If the object is of generic type, use
   * {@link #toJson(Object, Type)} instead. If you want to write out the object to a
   * {@link Writer}, use {@link #toJson(Object, Appendable)} instead.
   *
   * @param src the object for which JSON representation is to be created
   * @return Json representation of {@code src}.
   *
   * @see #toJson(Object, Appendable)
   * @see #toJson(Object, Type)
   */
  public String toJson(Object src) {
    if (src == null) {
      return toJson(JsonNull.INSTANCE);
    }
    return toJson(src, src.getClass());
  }

  /**
   * This method serializes the specified object, including those of generic types, into its
   * equivalent JSON representation. This method must be used if the specified object is a generic
   * type. For non-generic objects, use {@link #toJson(Object)} instead. If you want to write out
   * the object to a {@link Appendable}, use {@link #toJson(Object, Type, Appendable)} instead.
   *
   * @param src the object for which JSON representation is to be created
   * @param typeOfSrc The specific genericized type of src. You can obtain
   * this type by using the {@link TypeToken} class. For example,
   * to get the type for {@code Collection<Foo>}, you should use:
   * <pre>
   * Type typeOfSrc = new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}.getType();
   * </pre>
   * @return JSON representation of {@code src}
   *
   * @see #toJson(Object, Type, Appendable)
   * @see #toJson(Object)
   */
  public String toJson(Object src, Type typeOfSrc) {
    StringWriter writer = new StringWriter();
    toJson(src, typeOfSrc, writer);
    return writer.toString();
  }

  /**
   * This method serializes the specified object into its equivalent JSON representation and
   * writes it to the writer.
   * This method should be used when the specified object is not a generic type. This method uses
   * {@link Class#getClass()} to get the type for the specified object, but the
   * {@code getClass()} loses the generic type information because of the Type Erasure feature
   * of Java. Note that this method works fine if any of the object fields are of generic type,
   * just the object itself should not be of a generic type. If the object is of generic type, use
   * {@link #toJson(Object, Type, Appendable)} instead.
   *
   * @param src the object for which JSON representation is to be created
   * @param writer Writer to which the JSON representation needs to be written
   * @throws JsonIOException if there was a problem writing to the writer
   * @since 1.2
   *
   * @see #toJson(Object)
   * @see #toJson(Object, Type, Appendable)
   */
  public void toJson(Object src, Appendable writer) throws JsonIOException {
    if (src != null) {
      toJson(src, src.getClass(), writer);
    } else {
      toJson(JsonNull.INSTANCE, writer);
    }
  }

  /**
   * This method serializes the specified object, including those of generic types, into its
   * equivalent JSON representation and writes it to the writer.
   * This method must be used if the specified object is a generic type. For non-generic objects,
   * use {@link #toJson(Object, Appendable)} instead.
   *
   * @param src the object for which JSON representation is to be created
   * @param typeOfSrc The specific genericized type of src. You can obtain
   * this type by using the {@link TypeToken} class. For example,
   * to get the type for {@code Collection<Foo>}, you should use:
   * <pre>
   * Type typeOfSrc = new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}.getType();
   * </pre>
   * @param writer Writer to which the JSON representation of src needs to be written.
   * @throws JsonIOException if there was a problem writing to the writer
   * @since 1.2
   *
   * @see #toJson(Object, Type)
   * @see #toJson(Object, Appendable)
   */
  public void toJson(Object src, Type typeOfSrc, Appendable writer) throws JsonIOException {
    try {
      JsonWriter jsonWriter = newJsonWriter(Streams.writerForAppendable(writer));
      toJson(src, typeOfSrc, jsonWriter);
    } catch (IOException e) {
      throw new JsonIOException(e);
    }
  }

  /**
   * Writes the JSON representation of {@code src} of type {@code typeOfSrc} to
   * {@code writer}.
   *
   * <p>The JSON data is written in {@linkplain JsonWriter#setLenient(boolean) lenient mode},
   * regardless of the lenient mode setting of the provided writer. The lenient mode setting
   * of the writer is restored once this method returns.
   *
   * <p>The 'HTML-safe' and 'serialize {@code null}' settings of this {@code Gson} instance
   * (configured by the {@link GsonBuilder}) are applied, and the original settings of the
   * writer are restored once this method returns.
   *
   * @throws JsonIOException if there was a problem writing to the writer
   */
  public void toJson(Object src, Type typeOfSrc, JsonWriter writer) throws JsonIOException {
    @SuppressWarnings("unchecked")
    TypeAdapter<Object> adapter = (TypeAdapter<Object>) getAdapter(TypeToken.get(typeOfSrc));
    boolean oldLenient = writer.isLenient();
    writer.setLenient(true);
    boolean oldHtmlSafe = writer.isHtmlSafe();
    writer.setHtmlSafe(htmlSafe);
    boolean oldSerializeNulls = writer.getSerializeNulls();
    writer.setSerializeNulls(serializeNulls);
    try {
      adapter.write(writer, src);
    } catch (IOException e) {
      throw new JsonIOException(e);
    } catch (AssertionError e) {
      throw new AssertionError("AssertionError (GSON " + GsonBuildConfig.VERSION + "): " + e.getMessage(), e);
    } finally {
      writer.setLenient(oldLenient);
      writer.setHtmlSafe(oldHtmlSafe);
      writer.setSerializeNulls(oldSerializeNulls);
    }
  }

  /**
   * Converts a tree of {@link JsonElement}s into its equivalent JSON representation.
   *
   * @param jsonElement root of a tree of {@link JsonElement}s
   * @return JSON String representation of the tree
   * @since 1.4
   */
  public String toJson(JsonElement jsonElement) {
    StringWriter writer = new StringWriter();
    toJson(jsonElement, writer);
    return writer.toString();
  }

  /**
   * Writes out the equivalent JSON for a tree of {@link JsonElement}s.
   *
   * @param jsonElement root of a tree of {@link JsonElement}s
   * @param writer Writer to which the JSON representation needs to be written
   * @throws JsonIOException if there was a problem writing to the writer
   * @since 1.4
   */
  public void toJson(JsonElement jsonElement, Appendable writer) throws JsonIOException {
    try {
      JsonWriter jsonWriter = newJsonWriter(Streams.writerForAppendable(writer));
      toJson(jsonElement, jsonWriter);
    } catch (IOException e) {
      throw new JsonIOException(e);
    }
  }

  /**
   * Returns a new JSON writer configured for the settings on this Gson instance.
   *
   * <p>The following settings are considered:
   * <ul>
   *   <li>{@link GsonBuilder#disableHtmlEscaping()}</li>
   *   <li>{@link GsonBuilder#generateNonExecutableJson()}</li>
   *   <li>{@link GsonBuilder#serializeNulls()}</li>
   *   <li>{@link GsonBuilder#setLenient()}</li>
   *   <li>{@link GsonBuilder#setPrettyPrinting()}</li>
   * </ul>
   */
  public JsonWriter newJsonWriter(Writer writer) throws IOException {
    if (generateNonExecutableJson) {
      writer.write(JSON_NON_EXECUTABLE_PREFIX);
    }
    JsonWriter jsonWriter = new JsonWriter(writer);
    if (prettyPrinting) {
      jsonWriter.setIndent("  ");
    }
    jsonWriter.setHtmlSafe(htmlSafe);
    jsonWriter.setLenient(lenient);
    jsonWriter.setSerializeNulls(serializeNulls);
    return jsonWriter;
  }

  /**
   * Returns a new JSON reader configured for the settings on this Gson instance.
   *
   * <p>The following settings are considered:
   * <ul>
   *   <li>{@link GsonBuilder#setLenient()}</li>
   * </ul>
   */
  public JsonReader newJsonReader(Reader reader) {
    JsonReader jsonReader = new JsonReader(reader);
    jsonReader.setLenient(lenient);
    return jsonReader;
  }

  /**
   * Writes the JSON for {@code jsonElement} to {@code writer}.
   *
   * <p>The JSON data is written in {@linkplain JsonWriter#setLenient(boolean) lenient mode},
   * regardless of the lenient mode setting of the provided writer. The lenient mode setting
   * of the writer is restored once this method returns.
   *
   * <p>The 'HTML-safe' and 'serialize {@code null}' settings of this {@code Gson} instance
   * (configured by the {@link GsonBuilder}) are applied, and the original settings of the
   * writer are restored once this method returns.
   *
   * @throws JsonIOException if there was a problem writing to the writer
   */
  public void toJson(JsonElement jsonElement, JsonWriter writer) throws JsonIOException {
    boolean oldLenient = writer.isLenient();
    writer.setLenient(true);
    boolean oldHtmlSafe = writer.isHtmlSafe();
    writer.setHtmlSafe(htmlSafe);
    boolean oldSerializeNulls = writer.getSerializeNulls();
    writer.setSerializeNulls(serializeNulls);
    try {
      Streams.write(jsonElement, writer);
    } catch (IOException e) {
      throw new JsonIOException(e);
    } catch (AssertionError e) {
      throw new AssertionError("AssertionError (GSON " + GsonBuildConfig.VERSION + "): " + e.getMessage(), e);
    } finally {
      writer.setLenient(oldLenient);
      writer.setHtmlSafe(oldHtmlSafe);
      writer.setSerializeNulls(oldSerializeNulls);
    }
  }

  /**
   * This method deserializes the specified JSON into an object of the specified class. It is not
   * suitable to use if the specified class is a generic type since it will not have the generic
   * type information because of the Type Erasure feature of Java. Therefore, this method should not
   * be used if the desired type is a generic type. Note that this method works fine if the any of
   * the fields of the specified object are generics, just the object itself should not be a
   * generic type. For the cases when the object is of generic type, invoke
   * {@link #fromJson(String, TypeToken)}. If you have the JSON in a {@link Reader} instead of
   * a String, use {@link #fromJson(Reader, Class)} instead.
   *
   * <p>An exception is thrown if the JSON string has multiple top-level JSON elements, or if there
   * is trailing data. Use {@link #fromJson(JsonReader, Type)} if this behavior is not desired.
   *
   * @param <T> the type of the desired object
   * @param json the string from which the object is to be deserialized
   * @param classOfT the class of T
   * @return an object of type T from the string. Returns {@code null} if {@code json} is {@code null}
   * or if {@code json} is empty.
   * @throws JsonSyntaxException if json is not a valid representation for an object of type
   * classOfT
   *
   * @see #fromJson(Reader, Class)
   * @see #fromJson(String, TypeToken)
   */
  public <T> T fromJson(String json, Class<T> classOfT) throws JsonSyntaxException {
    T object = fromJson(json, TypeToken.get(classOfT));
    return Primitives.wrap(classOfT).cast(object);
  }

  /**
   * This method deserializes the specified JSON into an object of the specified type. This method
   * is useful if the specified object is a generic type. For non-generic objects, use
   * {@link #fromJson(String, Class)} instead. If you have the JSON in a {@link Reader} instead of
   * a String, use {@link #fromJson(Reader, Type)} instead.
   *
   * <p>Since {@code Type} is not parameterized by T, this method is not type-safe and
   * should be used carefully. If you are creating the {@code Type} from a {@link TypeToken},
   * prefer using {@link #fromJson(String, TypeToken)} instead since its return type is based
   * on the {@code TypeToken} and is therefore more type-safe.
   *
   * <p>An exception is thrown if the JSON string has multiple top-level JSON elements,
   * or if there is trailing data. Use {@link #fromJson(JsonReader, Type)} if this behavior is
   * not desired.
   *
   * @param <T> the type of the desired object
   * @param json the string from which the object is to be deserialized
   * @param typeOfT The specific genericized type of src
   * @return an object of type T from the string. Returns {@code null} if {@code json} is {@code null}
   * or if {@code json} is empty.
   * @throws JsonSyntaxException if json is not a valid representation for an object of type typeOfT
   *
   * @see #fromJson(Reader, Type)
   * @see #fromJson(String, Class)
   * @see #fromJson(String, TypeToken)
   */
  @SuppressWarnings("unchecked")
  public <T> T fromJson(String json, Type typeOfT) throws JsonSyntaxException {
    return (T) fromJson(json, TypeToken.get(typeOfT));
  }

  /**
   * This method deserializes the specified JSON into an object of the specified type. This method
   * is useful if the specified object is a generic type. For non-generic objects, use
   * {@link #fromJson(String, Class)} instead. If you have the JSON in a {@link Reader} instead of
   * a String, use {@link #fromJson(Reader, TypeToken)} instead.
   *
   * <p>An exception is thrown if the JSON string has multiple top-level JSON elements, or if there
   * is trailing data. Use {@link #fromJson(JsonReader, TypeToken)} if this behavior is not desired.
   *
   * @param <T> the type of the desired object
   * @param json the string from which the object is to be deserialized
   * @param typeOfT The specific genericized type of src. You should create an anonymous subclass of
   * {@code TypeToken} with the specific generic type arguments. For example, to get the type for
   * {@code Collection<Foo>}, you should use:
   * <pre>
   * new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}
   * </pre>
   * @return an object of type T from the string. Returns {@code null} if {@code json} is {@code null}
   * or if {@code json} is empty.
   * @throws JsonSyntaxException if json is not a valid representation for an object of the type typeOfT
   *
   * @see #fromJson(Reader, TypeToken)
   * @see #fromJson(String, Class)
   * @since 2.10
   */
  public <T> T fromJson(String json, TypeToken<T> typeOfT) throws JsonSyntaxException {
    if (json == null) {
      return null;
    }
    StringReader reader = new StringReader(json);
    return fromJson(reader, typeOfT);
  }

  /**
   * This method deserializes the JSON read from the specified reader into an object of the
   * specified class. It is not suitable to use if the specified class is a generic type since it
   * will not have the generic type information because of the Type Erasure feature of Java.
   * Therefore, this method should not be used if the desired type is a generic type. Note that
   * this method works fine if any of the fields of the specified object are generics, just the
   * object itself should not be a generic type. For the cases when the object is of generic type,
   * invoke {@link #fromJson(Reader, TypeToken)}. If you have the JSON in a String form instead of a
   * {@link Reader}, use {@link #fromJson(String, Class)} instead.
   *
   * <p>An exception is thrown if the JSON data has multiple top-level JSON elements, or if there
   * is trailing data. Use {@link #fromJson(JsonReader, Type)} if this behavior is not desired.
   *
   * @param <T> the type of the desired object
   * @param json the reader producing the JSON from which the object is to be deserialized.
   * @param classOfT the class of T
   * @return an object of type T from the Reader. Returns {@code null} if {@code json} is at EOF.
   * @throws JsonIOException if there was a problem reading from the Reader
   * @throws JsonSyntaxException if json is not a valid representation for an object of type typeOfT
   * @since 1.2
   *
   * @see #fromJson(String, Class)
   * @see #fromJson(Reader, TypeToken)
   */
  public <T> T fromJson(Reader json, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
    T object = fromJson(json, TypeToken.get(classOfT));
    return Primitives.wrap(classOfT).cast(object);
  }

  /**
   * This method deserializes the JSON read from the specified reader into an object of the
   * specified type. This method is useful if the specified object is a generic type. For
   * non-generic objects, use {@link #fromJson(Reader, Class)} instead. If you have the JSON in a
   * String form instead of a {@link Reader}, use {@link #fromJson(String, Type)} instead.
   *
   * <p>Since {@code Type} is not parameterized by T, this method is not type-safe and
   * should be used carefully. If you are creating the {@code Type} from a {@link TypeToken},
   * prefer using {@link #fromJson(Reader, TypeToken)} instead since its return type is based
   * on the {@code TypeToken} and is therefore more type-safe.
   *
   * <p>An exception is thrown if the JSON data has multiple top-level JSON elements, or if there
   * is trailing data. Use {@link #fromJson(JsonReader, Type)} if this behavior is not desired.
   *
   * @param <T> the type of the desired object
   * @param json the reader producing JSON from which the object is to be deserialized
   * @param typeOfT The specific genericized type of src
   * @return an object of type T from the Reader. Returns {@code null} if {@code json} is at EOF.
   * @throws JsonIOException if there was a problem reading from the Reader
   * @throws JsonSyntaxException if json is not a valid representation for an object of type typeOfT
   * @since 1.2
   *
   * @see #fromJson(String, Type)
   * @see #fromJson(Reader, Class)
   * @see #fromJson(Reader, TypeToken)
   */
  @SuppressWarnings("unchecked")
  public <T> T fromJson(Reader json, Type typeOfT) throws JsonIOException, JsonSyntaxException {
    return (T) fromJson(json, TypeToken.get(typeOfT));
  }

  /**
   * This method deserializes the JSON read from the specified reader into an object of the
   * specified type. This method is useful if the specified object is a generic type. For
   * non-generic objects, use {@link #fromJson(Reader, Class)} instead. If you have the JSON in a
   * String form instead of a {@link Reader}, use {@link #fromJson(String, TypeToken)} instead.
   *
   * <p>An exception is thrown if the JSON data has multiple top-level JSON elements, or if there
   * is trailing data. Use {@link #fromJson(JsonReader, TypeToken)} if this behavior is not desired.
   *
   * @param <T> the type of the desired object
   * @param json the reader producing JSON from which the object is to be deserialized
   * @param typeOfT The specific genericized type of src. You should create an anonymous subclass of
   * {@code TypeToken} with the specific generic type arguments. For example, to get the type for
   * {@code Collection<Foo>}, you should use:
   * <pre>
   * new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}
   * </pre>
   * @return an object of type T from the Reader. Returns {@code null} if {@code json} is at EOF.
   * @throws JsonIOException if there was a problem reading from the Reader
   * @throws JsonSyntaxException if json is not a valid representation for an object of type of typeOfT
   *
   * @see #fromJson(String, TypeToken)
   * @see #fromJson(Reader, Class)
   * @since 2.10
   */
  public <T> T fromJson(Reader json, TypeToken<T> typeOfT) throws JsonIOException, JsonSyntaxException {
    JsonReader jsonReader = newJsonReader(json);
    T object = fromJson(jsonReader, typeOfT);
    assertFullConsumption(object, jsonReader);
    return object;
  }

  private static void assertFullConsumption(Object obj, JsonReader reader) {
    try {
      if (obj != null && reader.peek() != JsonToken.END_DOCUMENT) {
        throw new JsonSyntaxException("JSON document was not fully consumed.");
      }
    } catch (MalformedJsonException e) {
      throw new JsonSyntaxException(e);
    } catch (IOException e) {
      throw new JsonIOException(e);
    }
  }

  // fromJson(JsonReader, Class) is unfortunately missing and cannot be added now without breaking
  // source compatibility in certain cases, see https://github.com/google/gson/pull/1700#discussion_r973764414

  /**
   * Reads the next JSON value from {@code reader} and converts it to an object
   * of type {@code typeOfT}. Returns {@code null}, if the {@code reader} is at EOF.
   *
   * <p>Since {@code Type} is not parameterized by T, this method is not type-safe and
   * should be used carefully. If you are creating the {@code Type} from a {@link TypeToken},
   * prefer using {@link #fromJson(JsonReader, TypeToken)} instead since its return type is based
   * on the {@code TypeToken} and is therefore more type-safe. If the provided type is a
   * {@code Class} the {@code TypeToken} can be created with {@link TypeToken#get(Class)}.
   *
   * <p>Unlike the other {@code fromJson} methods, no exception is thrown if the JSON data has
   * multiple top-level JSON elements, or if there is trailing data.
   *
   * <p>The JSON data is parsed in {@linkplain JsonReader#setLenient(boolean) lenient mode},
   * regardless of the lenient mode setting of the provided reader. The lenient mode setting
   * of the reader is restored once this method returns.
   *
   * @param <T> the type of the desired object
   * @param reader the reader whose next JSON value should be deserialized
   * @param typeOfT The specific genericized type of src
   * @return an object of type T from the JsonReader. Returns {@code null} if {@code reader} is at EOF.
   * @throws JsonIOException if there was a problem reading from the JsonReader
   * @throws JsonSyntaxException if json is not a valid representation for an object of type typeOfT
   *
   * @see #fromJson(Reader, Type)
   * @see #fromJson(JsonReader, TypeToken)
   */
  @SuppressWarnings("unchecked")
  public <T> T fromJson(JsonReader reader, Type typeOfT) throws JsonIOException, JsonSyntaxException {
    return (T) fromJson(reader, TypeToken.get(typeOfT));
  }

  /**
   * Reads the next JSON value from {@code reader} and converts it to an object
   * of type {@code typeOfT}. Returns {@code null}, if the {@code reader} is at EOF.
   * This method is useful if the specified object is a generic type. For non-generic objects,
   * {@link #fromJson(JsonReader, Type)} can be called, or {@link TypeToken#get(Class)} can
   * be used to create the type token.
   *
   * <p>Unlike the other {@code fromJson} methods, no exception is thrown if the JSON data has
   * multiple top-level JSON elements, or if there is trailing data.
   *
   * <p>The JSON data is parsed in {@linkplain JsonReader#setLenient(boolean) lenient mode},
   * regardless of the lenient mode setting of the provided reader. The lenient mode setting
   * of the reader is restored once this method returns.
   *
   * @param <T> the type of the desired object
   * @param reader the reader whose next JSON value should be deserialized
   * @param typeOfT The specific genericized type of src. You should create an anonymous subclass of
   * {@code TypeToken} with the specific generic type arguments. For example, to get the type for
   * {@code Collection<Foo>}, you should use:
   * <pre>
   * new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}
   * </pre>
   * @return an object of type T from the JsonReader. Returns {@code null} if {@code reader} is at EOF.
   * @throws JsonIOException if there was a problem reading from the JsonReader
   * @throws JsonSyntaxException if json is not a valid representation for an object of the type typeOfT
   *
   * @see #fromJson(Reader, TypeToken)
   * @see #fromJson(JsonReader, Type)
   * @since 2.10
   */
  public <T> T fromJson(JsonReader reader, TypeToken<T> typeOfT) throws JsonIOException, JsonSyntaxException {
    boolean isEmpty = true;
    boolean oldLenient = reader.isLenient();
    reader.setLenient(true);
    try {
      reader.peek();
      isEmpty = false;
      TypeAdapter<T> typeAdapter = getAdapter(typeOfT);
      return typeAdapter.read(reader);
    } catch (EOFException e) {
      /*
       * For compatibility with JSON 1.5 and earlier, we return null for empty
       * documents instead of throwing.
       */
      if (isEmpty) {
        return null;
      }
      throw new JsonSyntaxException(e);
    } catch (IllegalStateException e) {
      throw new JsonSyntaxException(e);
    } catch (IOException e) {
      // TODO(inder): Figure out whether it is indeed right to rethrow this as JsonSyntaxException
      throw new JsonSyntaxException(e);
    } catch (AssertionError e) {
      throw new AssertionError("AssertionError (GSON " + GsonBuildConfig.VERSION + "): " + e.getMessage(), e);
    } finally {
      reader.setLenient(oldLenient);
    }
  }

  /**
   * This method deserializes the JSON read from the specified parse tree into an object of the
   * specified type. It is not suitable to use if the specified class is a generic type since it
   * will not have the generic type information because of the Type Erasure feature of Java.
   * Therefore, this method should not be used if the desired type is a generic type. Note that
   * this method works fine if any of the fields of the specified object are generics, just the
   * object itself should not be a generic type. For the cases when the object is of generic type,
   * invoke {@link #fromJson(JsonElement, TypeToken)}.
   *
   * @param <T> the type of the desired object
   * @param json the root of the parse tree of {@link JsonElement}s from which the object is to
   * be deserialized
   * @param classOfT The class of T
   * @return an object of type T from the JSON. Returns {@code null} if {@code json} is {@code null}
   * or if {@code json} is empty.
   * @throws JsonSyntaxException if json is not a valid representation for an object of type classOfT
   * @since 1.3
   *
   * @see #fromJson(Reader, Class)
   * @see #fromJson(JsonElement, TypeToken)
   */
  public <T> T fromJson(JsonElement json, Class<T> classOfT) throws JsonSyntaxException {
    T object = fromJson(json, TypeToken.get(classOfT));
    return Primitives.wrap(classOfT).cast(object);
  }

  /**
   * This method deserializes the JSON read from the specified parse tree into an object of the
   * specified type. This method is useful if the specified object is a generic type. For
   * non-generic objects, use {@link #fromJson(JsonElement, Class)} instead.
   *
   * <p>Since {@code Type} is not parameterized by T, this method is not type-safe and
   * should be used carefully. If you are creating the {@code Type} from a {@link TypeToken},
   * prefer using {@link #fromJson(JsonElement, TypeToken)} instead since its return type is based
   * on the {@code TypeToken} and is therefore more type-safe.
   *
   * @param <T> the type of the desired object
   * @param json the root of the parse tree of {@link JsonElement}s from which the object is to
   * be deserialized
   * @param typeOfT The specific genericized type of src
   * @return an object of type T from the JSON. Returns {@code null} if {@code json} is {@code null}
   * or if {@code json} is empty.
   * @throws JsonSyntaxException if json is not a valid representation for an object of type typeOfT
   * @since 1.3
   *
   * @see #fromJson(Reader, Type)
   * @see #fromJson(JsonElement, Class)
   * @see #fromJson(JsonElement, TypeToken)
   */
  @SuppressWarnings("unchecked")
  public <T> T fromJson(JsonElement json, Type typeOfT) throws JsonSyntaxException {
    return (T) fromJson(json, TypeToken.get(typeOfT));
  }

  /**
   * This method deserializes the JSON read from the specified parse tree into an object of the
   * specified type. This method is useful if the specified object is a generic type. For
   * non-generic objects, use {@link #fromJson(JsonElement, Class)} instead.
   *
   * @param <T> the type of the desired object
   * @param json the root of the parse tree of {@link JsonElement}s from which the object is to
   * be deserialized
   * @param typeOfT The specific genericized type of src. You should create an anonymous subclass of
   * {@code TypeToken} with the specific generic type arguments. For example, to get the type for
   * {@code Collection<Foo>}, you should use:
   * <pre>
   * new TypeToken&lt;Collection&lt;Foo&gt;&gt;(){}
   * </pre>
   * @return an object of type T from the JSON. Returns {@code null} if {@code json} is {@code null}
   * or if {@code json} is empty.
   * @throws JsonSyntaxException if json is not a valid representation for an object of type typeOfT
   *
   * @see #fromJson(Reader, TypeToken)
   * @see #fromJson(JsonElement, Class)
   * @since 2.10
   */
  public <T> T fromJson(JsonElement json, TypeToken<T> typeOfT) throws JsonSyntaxException {
    if (json == null) {
      return null;
    }
    return fromJson(new JsonTreeReader(json), typeOfT);
  }

  /**
   * Proxy type adapter for cyclic type graphs.
   *
   * <p><b>Important:</b> Setting the delegate adapter is not thread-safe; instances of
   * {@code FutureTypeAdapter} must only be published to other threads after the delegate
   * has been set.
   *
   * @see Gson#threadLocalAdapterResults
   */
  static class FutureTypeAdapter<T> extends SerializationDelegatingTypeAdapter<T> {
    private TypeAdapter<T> delegate = null;

    public void setDelegate(TypeAdapter<T> typeAdapter) {
      if (delegate != null) {
        throw new AssertionError("Delegate is already set");
      }
      delegate = typeAdapter;
    }

    private TypeAdapter<T> delegate() {
      TypeAdapter<T> delegate = this.delegate;
      if (delegate == null) {
        // Can occur when adapter is leaked to other thread or when adapter is used for (de-)serialization
        // directly within the TypeAdapterFactory which requested it
        throw new IllegalStateException("Adapter for type with cyclic dependency has been used"
            + " before dependency has been resolved");
      }
      return delegate;
    }

    @Override public TypeAdapter<T> getSerializationDelegate() {
      return delegate();
    }

    @Override public T read(JsonReader in) throws IOException {
      return delegate().read(in);
    }

    @Override public void write(JsonWriter out, T value) throws IOException {
      delegate().write(out, value);
    }
  }

  @Override
  public String toString() {
    return "{serializeNulls:" + serializeNulls
        + ",factories:" + factories
        + ",instanceCreators:" + constructorConstructor
        + "}";
  }

  /**
   * Represents a generic type {@code T}. Java doesn't yet provide a way to
   * represent generic types, so this class does. Forces clients to create a
   * subclass of this class which enables retrieval the type information even at
   * runtime.
   *
   * <p>For example, to create a type literal for {@code List<String>}, you can
   * create an empty anonymous class:
   *
   * <p>
   * {@code TypeToken<List<String>> list = new TypeToken<List<String>>() {};}
   *
   * <p>Capturing a type variable as type argument of a {@code TypeToken} should
   * be avoided. Due to type erasure the runtime type of a type variable is not
   * available to Gson and therefore it cannot provide the functionality one
   * might expect, which gives a false sense of type-safety at compilation time
   * and can lead to an unexpected {@code ClassCastException} at runtime.
   *
   * <p>If the type arguments of the parameterized type are only available at
   * runtime, for example when you want to create a {@code List<E>} based on
   * a {@code Class<E>} representing the element type, the method
   * {@link #getParameterized(Type, Type...)} can be used.
   *
   * @author Bob Lee
   * @author Sven Mawson
   * @author Jesse Wilson
   */
  public static class TypeToken<T> {
    private final Class<? super T> rawType;
    private final Type type;
    private final int hashCode;

    /**
     * Constructs a new type literal. Derives represented class from type
     * parameter.
     *
     * <p>Clients create an empty anonymous subclass. Doing so embeds the type
     * parameter in the anonymous class's type hierarchy so we can reconstitute it
     * at runtime despite erasure.
     */
    @SuppressWarnings("unchecked")
    protected TypeToken() {
      this.type = getTypeTokenTypeArgument();
      this.rawType = (Class<? super T>) GsonTypes.getRawType(type);
      this.hashCode = type.hashCode();
    }

    /**
     * Unsafe. Constructs a type literal manually.
     */
    @SuppressWarnings("unchecked")
    private TypeToken(Type type) {
      this.type = GsonTypes.canonicalize(Objects.requireNonNull(type));
      this.rawType = (Class<? super T>) GsonTypes.getRawType(this.type);
      this.hashCode = this.type.hashCode();
    }

    /**
     * Verifies that {@code this} is an instance of a direct subclass of TypeToken and
     * returns the type argument for {@code T} in {@link GsonTypes#canonicalize
     * canonical form}.
     */
    private Type getTypeTokenTypeArgument() {
      Type superclass = getClass().getGenericSuperclass();
      if (superclass instanceof ParameterizedType) {
        ParameterizedType parameterized = (ParameterizedType) superclass;
        if (parameterized.getRawType() == TypeToken.class) {
          return GsonTypes.canonicalize(parameterized.getActualTypeArguments()[0]);
        }
      }
      // Check for raw TypeToken as superclass
      else if (superclass == TypeToken.class) {
        throw new IllegalStateException("TypeToken must be created with a type argument: new TypeToken<...>() {}; "
            + "When using code shrinkers (ProGuard, R8, ...) make sure that generic signatures are preserved.");
      }

      // User created subclass of subclass of TypeToken
      throw new IllegalStateException("Must only create direct subclasses of TypeToken");
    }

    /**
     * Returns the raw (non-generic) type for this type.
     */
    public final Class<? super T> getRawType() {
      return rawType;
    }

    /**
     * Gets underlying {@code Type} instance.
     */
    public final Type getType() {
      return type;
    }

    /**
     * Check if this type is assignable from the given class object.
     *
     * @deprecated this implementation may be inconsistent with javac for types
     *     with wildcards.
     */
    @Deprecated
    public boolean isAssignableFrom(Class<?> cls) {
      return isAssignableFrom((Type) cls);
    }

    /**
     * Check if this type is assignable from the given Type.
     *
     * @deprecated this implementation may be inconsistent with javac for types
     *     with wildcards.
     */
    @Deprecated
    public boolean isAssignableFrom(Type from) {
      if (from == null) {
        return false;
      }

      if (type.equals(from)) {
        return true;
      }

      if (type instanceof Class<?>) {
        return rawType.isAssignableFrom(GsonTypes.getRawType(from));
      } else if (type instanceof ParameterizedType) {
        return isAssignableFrom(from, (ParameterizedType) type,
            new HashMap<String, Type>());
      } else if (type instanceof GenericArrayType) {
        return rawType.isAssignableFrom(GsonTypes.getRawType(from))
            && isAssignableFrom(from, (GenericArrayType) type);
      } else {
        throw buildUnexpectedTypeError(
            type, Class.class, ParameterizedType.class, GenericArrayType.class);
      }
    }

    /**
     * Check if this type is assignable from the given type token.
     *
     * @deprecated this implementation may be inconsistent with javac for types
     *     with wildcards.
     */
    @Deprecated
    public boolean isAssignableFrom(TypeToken<?> token) {
      return isAssignableFrom(token.getType());
    }

    /**
     * Private helper function that performs some assignability checks for
     * the provided GenericArrayType.
     */
    private static boolean isAssignableFrom(Type from, GenericArrayType to) {
      Type toGenericComponentType = to.getGenericComponentType();
      if (toGenericComponentType instanceof ParameterizedType) {
        Type t = from;
        if (from instanceof GenericArrayType) {
          t = ((GenericArrayType) from).getGenericComponentType();
        } else if (from instanceof Class<?>) {
          Class<?> classType = (Class<?>) from;
          while (classType.isArray()) {
            classType = classType.getComponentType();
          }
          t = classType;
        }
        return isAssignableFrom(t, (ParameterizedType) toGenericComponentType,
            new HashMap<String, Type>());
      }
      // No generic defined on "to"; therefore, return true and let other
      // checks determine assignability
      return true;
    }

    /**
     * Private recursive helper function to actually do the type-safe checking
     * of assignability.
     */
    private static boolean isAssignableFrom(Type from, ParameterizedType to,
        Map<String, Type> typeVarMap) {

      if (from == null) {
        return false;
      }

      if (to.equals(from)) {
        return true;
      }

      // First figure out the class and any type information.
      Class<?> clazz = GsonTypes.getRawType(from);
      ParameterizedType ptype = null;
      if (from instanceof ParameterizedType) {
        ptype = (ParameterizedType) from;
      }

      // Load up parameterized variable info if it was parameterized.
      if (ptype != null) {
        Type[] tArgs = ptype.getActualTypeArguments();
        TypeVariable<?>[] tParams = clazz.getTypeParameters();
        for (int i = 0; i < tArgs.length; i++) {
          Type arg = tArgs[i];
          TypeVariable<?> var = tParams[i];
          while (arg instanceof TypeVariable<?>) {
            TypeVariable<?> v = (TypeVariable<?>) arg;
            arg = typeVarMap.get(v.getName());
          }
          typeVarMap.put(var.getName(), arg);
        }

        // check if they are equivalent under our current mapping.
        if (typeEquals(ptype, to, typeVarMap)) {
          return true;
        }
      }

      for (Type itype : clazz.getGenericInterfaces()) {
        if (isAssignableFrom(itype, to, new HashMap<>(typeVarMap))) {
          return true;
        }
      }

      // Interfaces didn't work, try the superclass.
      Type sType = clazz.getGenericSuperclass();
      return isAssignableFrom(sType, to, new HashMap<>(typeVarMap));
    }

    /**
     * Checks if two parameterized types are exactly equal, under the variable
     * replacement described in the typeVarMap.
     */
    private static boolean typeEquals(ParameterizedType from,
        ParameterizedType to, Map<String, Type> typeVarMap) {
      if (from.getRawType().equals(to.getRawType())) {
        Type[] fromArgs = from.getActualTypeArguments();
        Type[] toArgs = to.getActualTypeArguments();
        for (int i = 0; i < fromArgs.length; i++) {
          if (!matches(fromArgs[i], toArgs[i], typeVarMap)) {
            return false;
          }
        }
        return true;
      }
      return false;
    }

    private static AssertionError buildUnexpectedTypeError(
        Type token, Class<?>... expected) {

      // Build exception message
      StringBuilder exceptionMessage =
          new StringBuilder("Unexpected type. Expected one of: ");
      for (Class<?> clazz : expected) {
        exceptionMessage.append(clazz.getName()).append(", ");
      }
      exceptionMessage.append("but got: ").append(token.getClass().getName())
          .append(", for type token: ").append(token).append('.');

      return new AssertionError(exceptionMessage.toString());
    }

    /**
     * Checks if two types are the same or are equivalent under a variable mapping
     * given in the type map that was provided.
     */
    private static boolean matches(Type from, Type to, Map<String, Type> typeMap) {
      return to.equals(from)
          || (from instanceof TypeVariable
          && to.equals(typeMap.get(((TypeVariable<?>) from).getName())));

    }

    @Override public final int hashCode() {
      return this.hashCode;
    }

    @Override public final boolean equals(Object o) {
      return o instanceof TypeToken<?>
          && GsonTypes.equals(type, ((TypeToken<?>) o).type);
    }

    @Override public final String toString() {
      return GsonTypes.typeToString(type);
    }

    /**
     * Gets type literal for the given {@code Type} instance.
     */
    public static TypeToken<?> get(Type type) {
      return new TypeToken<>(type);
    }

    /**
     * Gets type literal for the given {@code Class} instance.
     */
    public static <T> TypeToken<T> get(Class<T> type) {
      return new TypeToken<>(type);
    }

    /**
     * Gets a type literal for the parameterized type represented by applying {@code typeArguments} to
     * {@code rawType}. This is mainly intended for situations where the type arguments are not
     * available at compile time. The following example shows how a type token for {@code Map<K, V>}
     * can be created:
     * <pre>{@code
     * Class<K> keyClass = ...;
     * Class<V> valueClass = ...;
     * TypeToken<?> mapTypeToken = TypeToken.getParameterized(Map.class, keyClass, valueClass);
     * }</pre>
     * As seen here the result is a {@code TypeToken<?>}; this method cannot provide any type safety,
     * and care must be taken to pass in the correct number of type arguments.
     *
     * @throws IllegalArgumentException
     *   If {@code rawType} is not of type {@code Class}, or if the type arguments are invalid for
     *   the raw type
     */
    public static TypeToken<?> getParameterized(Type rawType, Type... typeArguments) {
      Objects.requireNonNull(rawType);
      Objects.requireNonNull(typeArguments);

      // Perform basic validation here because this is the only public API where users
      // can create malformed parameterized types
      if (!(rawType instanceof Class)) {
        // See also https://bugs.openjdk.org/browse/JDK-8250659
        throw new IllegalArgumentException("rawType must be of type Class, but was " + rawType);
      }
      Class<?> rawClass = (Class<?>) rawType;
      TypeVariable<?>[] typeVariables = rawClass.getTypeParameters();

      int expectedArgsCount = typeVariables.length;
      int actualArgsCount = typeArguments.length;
      if (actualArgsCount != expectedArgsCount) {
        throw new IllegalArgumentException(rawClass.getName() + " requires " + expectedArgsCount +
            " type arguments, but got " + actualArgsCount);
      }

      for (int i = 0; i < expectedArgsCount; i++) {
        Type typeArgument = typeArguments[i];
        Class<?> rawTypeArgument = GsonTypes.getRawType(typeArgument);
        TypeVariable<?> typeVariable = typeVariables[i];

        for (Type bound : typeVariable.getBounds()) {
          Class<?> rawBound = GsonTypes.getRawType(bound);

          if (!rawBound.isAssignableFrom(rawTypeArgument)) {
            throw new IllegalArgumentException("Type argument " + typeArgument + " does not satisfy bounds "
                + "for type variable " + typeVariable + " declared by " + rawType);
          }
        }
      }

      return new TypeToken<>(GsonTypes.newParameterizedTypeWithOwner(null, rawType, typeArguments));
    }

    /**
     * Gets type literal for the array type whose elements are all instances of {@code componentType}.
     */
    public static TypeToken<?> getArray(Type componentType) {
      return new TypeToken<>(GsonTypes.arrayOf(componentType));
    }
  }
}
