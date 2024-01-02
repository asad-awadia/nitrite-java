/*
 * Copyright (c) 2017-2020. Nitrite author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dizitart.no2.common.mapper;

import org.dizitart.no2.NitriteConfig;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.common.util.ObjectUtils;
import org.dizitart.no2.exceptions.ObjectMappingException;

import java.util.*;

import static org.dizitart.no2.common.util.Iterables.listOf;
import static org.dizitart.no2.common.util.ValidationUtils.notNull;

/**
 * A {@link NitriteMapper} based on {@link EntityConverter} implementation.
 *
 * <p>
 * This mapper is used by default in nitrite. It uses {@link EntityConverter} to
 * convert an object and vice versa.
 *
 * @author Anindya Chatterjee.
 * @since 4.0
 */
public class SimpleNitriteMapper implements NitriteMapper {
    private final Set<Class<?>> valueTypes;
    private final Map<Class<?>, EntityConverter<?>> converterRegistry;

    /**
     * Instantiates a new {@link SimpleNitriteMapper}.
     *
     * @param valueTypes the value types
     */
    public SimpleNitriteMapper(Class<?>... valueTypes) {
        this.valueTypes = new HashSet<>();
        this.converterRegistry = new HashMap<>();
        init(listOf(valueTypes));
    }

    @Override
    public <Source, Target> Object tryConvert(Source source, Class<Target> type) {
        if (source == null) {
            return null;
        }

        if (isValue(source)) {
            return source;
        } else {
            if (Document.class.isAssignableFrom(type)) {
                if (source instanceof Document) {
                    return source;
                } else {
                    return convertToDocument(source);
                }
            } else if (source instanceof Document) {
                return convertFromDocument((Document) source, type);
            } else if (source.getClass().isAssignableFrom(type) || type.isAssignableFrom(source.getClass())) {
                return source;
            }
        }

        throw new ObjectMappingException("Can't convert object to type " + type
            + ", try registering a EntityConverter for it.");
    }

    /**
     * Registers an {@link EntityConverter}.
     *
     * @param entityConverter the entity converter
     */
    public void registerEntityConverter(EntityConverter<?> entityConverter) {
        notNull(entityConverter, "entityConverter cannot be null");
        converterRegistry.put(entityConverter.getEntityType(), entityConverter);
    }

    @Override
    public void initialize(NitriteConfig nitriteConfig) {
    }

    /**
     * Converts a document to a target object of type <code>Target</code>.
     *
     * @param <Target> the type parameter
     * @param source   the source
     * @param type     the type
     * @return the target
     */
    @SuppressWarnings("unchecked")
    protected <Target> Target convertFromDocument(Document source, Class<Target> type) {
        if (source == null) {
            return null;
        }

        try (EntityConverter<Target> converter = (EntityConverter<Target>) findConverter(type)) {
            if (converter != null) {
                return converter.fromDocument(source, this);
            }
        }

        throw new ObjectMappingException("Can't convert Document to type " + type
            + ", try registering a EntityConverter for it.");
    }

    /**
     * Converts an object of type <code>Source</code> to a document.
     *
     * @param <Source> the type parameter
     * @param source   the source
     * @return the document
     */
    @SuppressWarnings("unchecked")
    protected <Source> Document convertToDocument(Source source) {
        try (EntityConverter<Source> converter = (EntityConverter<Source>) findConverter(source.getClass())) {
            if (converter != null) {
                return converter.toDocument(source, this);
            }
        }

        throw new ObjectMappingException("Can't convert object of type " + source.getClass().getName() +
            " to Document, try registering a EntityConverter for it.");
    }

    private EntityConverter<?> findConverter(Class<?> type) {
        if (converterRegistry.containsKey(type)) {
            return converterRegistry.get(type);
        }

        for (EntityConverter<?> value : converterRegistry.values()) {
            if (value.getEntityType().isAssignableFrom(type)) {
                return value;
            }
        }
        return null;
    }

    private boolean isValueType(Class<?> type) {
        if (type.isPrimitive() && type != void.class) return true;
        if (valueTypes.contains(type)) return true;
        for (Class<?> valueType : valueTypes) {
            if (valueType.isAssignableFrom(type)) return true;
        }
        return false;
    }

    private boolean isValue(Object object) {
        return isValueType(object.getClass());
    }

    private void init(List<Class<?>> valueTypes) {
        this.valueTypes.addAll(ObjectUtils.builtInTypes());

        this.valueTypes.add(Enum.class);
        this.valueTypes.add(NitriteId.class);

        if (valueTypes != null && !valueTypes.isEmpty()) {
            this.valueTypes.addAll(valueTypes);
        }
    }
}
