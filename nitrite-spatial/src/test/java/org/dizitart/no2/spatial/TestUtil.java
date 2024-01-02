/*
 * Copyright (c) 2017-2021 Nitrite author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.dizitart.no2.spatial;

import lombok.extern.slf4j.Slf4j;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.mvstore.MVStoreModule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

/**
 * @author Anindya Chatterjee
 */
@Slf4j
public class TestUtil {
    public static String getRandomTempDbFile() {
        String dataDir = System.getProperty("java.io.tmpdir") + File.separator + "nitrite" + File.separator + "data";
        File file = new File(dataDir);
        if (!file.exists()) {
            assertTrue(file.mkdirs());
        }
        return file.getPath() + File.separator + UUID.randomUUID() + ".db";
    }

    public static Nitrite createDb(String fileName) {
        MVStoreModule storeModule = MVStoreModule.withConfig()
            .filePath(fileName)
            .build();

        return Nitrite.builder()
            .loadModule(storeModule)
            .loadModule(new SpatialModule())
            .registerEntityConverter(new SpatialData.SpatialDataConverter())
            .fieldSeparator(".")
            .openOrCreate();
    }

    public static void deleteDb(String fileName) {
        try {
            Files.delete(Paths.get(fileName));
        } catch (Exception e) {
            log.error("Error while deleting db", e);
        }
    }
}
