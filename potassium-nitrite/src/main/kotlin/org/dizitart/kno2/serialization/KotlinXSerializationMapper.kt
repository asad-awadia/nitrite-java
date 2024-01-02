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

package org.dizitart.kno2.serialization

import org.dizitart.no2.NitriteConfig
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.NitriteId
import org.dizitart.no2.common.mapper.NitriteMapper
import org.dizitart.no2.exceptions.ObjectMappingException
import java.util.*

/**
 * A [org.dizitart.no2.common.mapper.NitriteMapper] module that uses KotlinX Serialization 
 * for object to [Document] conversion and vice versa.
 * 
 * @author Joris Jensen
 * @since 4.2.0
 */
class KotlinXSerializationMapper : NitriteMapper {
    private fun <Target : Any> convertFromDocument(source: Document?, type: Class<Target>): Target? =
        source?.let { DocumentDecoder.decodeFromDocument(source, type) }

    private fun <Source : Any> convertToDocument(source: Source): Document = DocumentEncoder.encodeToDocument(source)

    override fun <Source, Target : Any> tryConvert(source: Source, type: Class<Target>): Any? {
        val nonNullSource = source ?: return null
        return when {
            isValueType(nonNullSource::class.java) -> source as Target
            Document::class.java.isAssignableFrom(type) -> {
                if (source is Document) {
                    source
                } else {
                    convertToDocument(source)
                }
            }

            source is Document -> convertFromDocument(source, type)
            else -> throw ObjectMappingException("Can't convert object of type " + nonNullSource::class.java + " to type " + type)
        }
    }

    private fun isValueType(type: Class<*>): Boolean {
        if (type.isPrimitive && type != Void.TYPE) return true
        if (valueTypes.contains(type)) return true
        return valueTypes.any { it.isAssignableFrom(type) }
    }

    private val valueTypes: List<Class<*>> = listOf(
        Number::class.java,
        Boolean::class.java,
        Character::class.java,
        String::class.java,
        Array<Byte>::class.java,
        Enum::class.java,
        NitriteId::class.java,
        Date::class.java,
    )

    override fun initialize(nitriteConfig: NitriteConfig) {}
}