/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gson.internal.bind;

import static com.google.gson.internal.bind.JsonAdapterAnnotationTypeAdapterFactory.getTypeAdapter;
import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.internal.$Gson$Types;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.Excluder;
import com.google.gson.internal.ObjectConstructor;
import com.google.gson.internal.Primitives;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Type adapter that reflects over the fields and methods of a class.
 */
public final class MethodReflectiveTypeAdapterFactory implements TypeAdapterFactory
{
    private final ConstructorConstructor constructorConstructor;

    private final FieldNamingStrategy fieldNamingPolicy;

    private final Excluder excluder;

    public MethodReflectiveTypeAdapterFactory(ConstructorConstructor constructorConstructor,
                                              FieldNamingStrategy fieldNamingPolicy, Excluder excluder)
    {
        this.constructorConstructor = constructorConstructor;
        this.fieldNamingPolicy = fieldNamingPolicy;
        this.excluder = excluder;
    }

    public boolean excludeField(Field f, boolean serialize)
    {
        return excludeField(f, serialize, excluder);
    }

    static boolean excludeField(Field f, boolean serialize, Excluder excluder)
    {
        return !excluder.excludeClass(f.getType(), serialize) && !excluder.excludeField(f, serialize);
    }

    private String getFieldName(Field f)
    {
        return getFieldName(fieldNamingPolicy, f);
    }

    static String getFieldName(FieldNamingStrategy fieldNamingPolicy, Field f)
    {
        SerializedName serializedName = f.getAnnotation(SerializedName.class);
        return serializedName == null ? fieldNamingPolicy.translateName(f) : serializedName.value();
    }

    public <T> TypeAdapter<T> create(Gson gson, final TypeToken<T> type)
    {
        Class<? super T> raw = type.getRawType();

        if (!Object.class.isAssignableFrom(raw))
        {
            return null; // it's a primitive!
        }

        ObjectConstructor<T> constructor = constructorConstructor.get(type);
        return new Adapter<T>(constructor, getBoundFields(gson, type, raw));
    }

    private MethodReflectiveTypeAdapterFactory.BoundField createBoundField(
            final Gson context, final Field field, final Method method, final String name,
            final TypeToken<?> fieldType, boolean serialize, boolean deserialize)
    {
        final boolean isPrimitive = Primitives.isPrimitive(fieldType.getRawType());
        // special casing primitives here saves ~5% on Android...
        return new MethodReflectiveTypeAdapterFactory.BoundField(name, serialize, deserialize)
        {
            final TypeAdapter<?> typeAdapter = getFieldAdapter(context, field, fieldType);

            @SuppressWarnings({"unchecked", "rawtypes"})
            // the type adapter and field type always agree
            @Override
            void write(JsonWriter writer, Object value) throws IOException, IllegalAccessException
            {
                try
                {
                    // Object fieldValue = field.get(value);
                    Object fieldValue = method.invoke(value);
                    TypeAdapter t = new TypeAdapterRuntimeTypeWrapper(context, this.typeAdapter, fieldType.getType());
                    t.write(writer, fieldValue);
                }
                catch (InvocationTargetException e)
                {
                    e.printStackTrace();
                }
            }

            @Override
            void read(JsonReader reader, Object value) throws IOException, IllegalAccessException
            {
                Object fieldValue = typeAdapter.read(reader);
                if (fieldValue != null || !isPrimitive)
                {
                    field.set(value, fieldValue);
                }
            }

            public boolean writeField(Object value) throws IOException, IllegalAccessException
            {
                if (!serialized)
                {
                    return false;
                }
                Object fieldValue = field.get(value);
                return fieldValue != value; // avoid recursion for example for Throwable.cause
            }
        };
    }

    private TypeAdapter<?> getFieldAdapter(Gson gson, Field field, TypeToken<?> fieldType)
    {
        JsonAdapter annotation = field.getAnnotation(JsonAdapter.class);
        if (annotation != null)
        {
            TypeAdapter<?> adapter = getTypeAdapter(constructorConstructor, gson, fieldType, annotation);
            if (adapter != null)
            {
                return adapter;
            }
        }
        return gson.getAdapter(fieldType);
    }

    private Map<String, BoundField> getBoundFields(Gson context, TypeToken<?> type, Class<?> raw)
    {
        Map<String, BoundField> result = new LinkedHashMap<String, BoundField>();
        if (raw.isInterface())
        {
            return result;
        }

        Type declaredType = type.getType();
        while (raw != Object.class)
        {
            Field[] fields = raw.getDeclaredFields();
            for (Field field : fields)
            {
                boolean serialize = excludeField(field, true);
                boolean deserialize = excludeField(field, false);
                if (!serialize && !deserialize)
                {
                    continue;
                }
                field.setAccessible(true);
                Type fieldType = $Gson$Types.resolve(type.getType(), raw, field.getGenericType());

                Method method = null;
                try
                {
                    String fieldName = field.getName();
                    String getter = "get";
                    if (field.getType() == Boolean.class || field.getType() == boolean.class)
                    {
                        getter = "is";
                    }
                    getter += fieldName.substring(0, 1).toUpperCase() + (fieldName.length() <= 1 ? "" : fieldName.substring(1));

                    method = raw.getMethod(getter, new Class[]{});
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                BoundField boundField = createBoundField(context, field, method, getFieldName(field), TypeToken.get(fieldType), serialize, deserialize);
                BoundField previous = result.put(boundField.name, boundField);
                if (previous != null)
                {
                    throw new IllegalArgumentException(declaredType
                            + " declares multiple JSON fields named " + previous.name);
                }
            }
            type = TypeToken.get($Gson$Types.resolve(type.getType(), raw, raw.getGenericSuperclass()));
            raw = type.getRawType();
        }
        return result;
    }

    static abstract class BoundField
    {
        final String name;

        final boolean serialized;

        final boolean deserialized;

        protected BoundField(String name, boolean serialized, boolean deserialized)
        {
            this.name = name;
            this.serialized = serialized;
            this.deserialized = deserialized;
        }

        abstract boolean writeField(Object value) throws IOException, IllegalAccessException;

        abstract void write(JsonWriter writer, Object value) throws IOException, IllegalAccessException;

        abstract void read(JsonReader reader, Object value) throws IOException, IllegalAccessException;
    }

    public static final class Adapter<T> extends TypeAdapter<T>
    {
        private final ObjectConstructor<T> constructor;

        private final Map<String, BoundField> boundFields;

        private Adapter(ObjectConstructor<T> constructor, Map<String, BoundField> boundFields)
        {
            this.constructor = constructor;
            this.boundFields = boundFields;
        }

        @Override
        public T read(JsonReader in) throws IOException
        {
            if (in.peek() == JsonToken.NULL)
            {
                in.nextNull();
                return null;
            }

            T instance = constructor.construct();

            try
            {
                in.beginObject();
                while (in.hasNext())
                {
                    String name = in.nextName();
                    BoundField field = boundFields.get(name);
                    if (field == null || !field.deserialized)
                    {
                        in.skipValue();
                    }
                    else
                    {
                        field.read(in, instance);
                    }
                }
            }
            catch (IllegalStateException e)
            {
                throw new JsonSyntaxException(e);
            }
            catch (IllegalAccessException e)
            {
                throw new AssertionError(e);
            }
            in.endObject();
            return instance;
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException
        {
            if (value == null)
            {
                out.nullValue();
                return;
            }

            out.beginObject();
            try
            {
                for (BoundField boundField : boundFields.values())
                {
                    if (boundField.writeField(value))
                    {
                        out.name(boundField.name);
                        boundField.write(out, value);
                    }
                }
            }
            catch (IllegalAccessException e)
            {
                throw new AssertionError();
            }
            out.endObject();
        }
    }
}
