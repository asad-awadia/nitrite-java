/*
 *
 * Copyright 2017-2018 Nitrite author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.dizitart.no2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dizitart.no2.exceptions.NitriteIOException;
import org.dizitart.no2.filters.Filters;
import org.dizitart.no2.objects.Id;
import org.dizitart.no2.objects.Index;
import org.dizitart.no2.objects.Indices;
import org.dizitart.no2.objects.ObjectRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.dizitart.no2.Constants.INTERNAL_NAME_SEPARATOR;
import static org.dizitart.no2.DbTestOperations.getRandomTempDbFile;
import static org.dizitart.no2.Document.createDocument;
import static org.dizitart.no2.filters.Filters.ALL;
import static org.dizitart.no2.objects.filters.ObjectFilters.eq;
import static org.dizitart.no2.objects.filters.ObjectFilters.not;
import static org.junit.Assert.*;

public class NitriteTest {
    private Nitrite db;
    private NitriteCollection collection;
    private SimpleDateFormat simpleDateFormat;
    private String fileName = getRandomTempDbFile();

    @Before
    public void setUp() throws ParseException {
        db = new NitriteBuilder()
                .filePath(fileName)
                .compressed()
                .openOrCreate("test-user", "test-password");

        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);

        Document doc1 = createDocument("firstName", "fn1")
                .put("lastName", "ln1")
                .put("birthDay", simpleDateFormat.parse("2012-07-01T16:02:48.440Z"))
                .put("data", new byte[]{1, 2, 3})
                .put("body", "a quick brown fox jump over the lazy dog");
        Document doc2 = createDocument("firstName", "fn2")
                .put("lastName", "ln2")
                .put("birthDay", simpleDateFormat.parse("2010-06-12T16:02:48.440Z"))
                .put("data", new byte[]{3, 4, 3})
                .put("body", "hello world from nitrite");
        Document doc3 = createDocument("firstName", "fn3")
                .put("lastName", "ln2")
                .put("birthDay", simpleDateFormat.parse("2014-04-17T16:02:48.440Z"))
                .put("data", new byte[]{9, 4, 8})
                .put("body", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                        "Sed nunc mi, mattis ullamcorper dignissim vitae, condimentum non lorem.");

        collection = db.getCollection("test");
        collection.remove(ALL);

        collection.createIndex("body", IndexOptions.indexOptions(IndexType.Fulltext));
        collection.createIndex("firstName", IndexOptions.indexOptions(IndexType.Unique));
        collection.insert(doc1, doc2, doc3);
    }

    @After
    public void tearDown() throws IOException {
        if (!collection.isClosed()) {
            collection.remove(ALL);
            collection.close();
        }
        db.close();
        Files.delete(Paths.get(fileName));
    }

    @Test
    public void testListCollectionNames() {
        Set<String> collectionNames = db.listCollectionNames();
        assertEquals(collectionNames.size(), 1);
    }

    @Test
    public void testListRepositories() {
        db.getRepository(getClass());
        Set<String> repositories = db.listRepositories();
        assertEquals(repositories.size(), 1);
    }

    @Test
    public void testHasCollection() {
        assertTrue(db.hasCollection("test"));
        assertFalse(db.hasCollection("lucene" + INTERNAL_NAME_SEPARATOR + "test"));
    }

    @Test
    public void testHasRepository() {
        db.getRepository(getClass());
        assertTrue(db.hasRepository(getClass()));
        assertFalse(db.hasRepository(String.class));
    }

    @Test
    public void testCompact() {
        long initialSize = new File(fileName).length();
        db.compact();
        db.commit();
        db.close();
        // according to documentation MVStore.compactMoveChunks() size would
        // increase temporarily
        assertTrue(new File(fileName).length() > initialSize);
    }

    @Test
    public void testReopen() throws ParseException {
        assertNotNull(db);
        NitriteCollection testCollection = db.getCollection("test");
        assertNotNull(testCollection);
        int prevSize = testCollection.find().size();

        db.close();

        db = null;

        db = new NitriteBuilder()
                .filePath(fileName)
                .compressed()
                .openOrCreate("test-user", "test-password");

        assertNotNull(db);
        testCollection = db.getCollection("test");
        assertNotNull(testCollection);
        int sizeNow = testCollection.find().size();
        assertEquals(prevSize, sizeNow);

        db.close();
        db = null;

        db = new NitriteBuilder()
                .filePath(fileName)
                .compressed()
                .openOrCreate("test-user", "test-password");
        testCollection = db.getCollection("test");
        testCollection.insert(createDocument("firstName", "fn12")
                .put("lastName", "ln12")
                .put("birthDay", simpleDateFormat.parse("2010-07-01T16:02:48.440Z"))
                .put("data", new byte[]{10, 20, 30})
                .put("body", "a quick brown fox jump over the lazy dog"));

        db.close();
        db = null;

        db = new NitriteBuilder()
                .filePath(fileName)
                .compressed()
                .openOrCreate("test-user", "test-password");
        testCollection = db.getCollection("test");
        assertNotNull(testCollection);
        sizeNow = testCollection.find().size();
        assertEquals(prevSize + 1, sizeNow);
    }

    @Test
    public void testCloseImmediately() {
        NitriteCollection testCollection = db.getCollection("test");
        testCollection.insert(createDocument("a", "b"));
        db.closeImmediately();

        assertTrue(testCollection.isClosed());
    }

    @Test
    public void testCloseImmediatelyReadonlyDatabase() {
        db.close();
        db = null;

        db = new NitriteBuilder()
                .filePath(fileName)
                .compressed()
                .readOnly()
                .openOrCreate("test-user", "test-password");
        NitriteCollection testCollection = db.getCollection("test");
        testCollection.insert(createDocument("a", "b"));
        db.closeImmediately();

        assertTrue(testCollection.isClosed());
    }

    @Test
    public void testCloseReadonlyDatabase() {
        db.close();
        db = null;

        db = new NitriteBuilder()
                .filePath(fileName)
                .compressed()
                .readOnly()
                .openOrCreate("test-user", "test-password");
        NitriteCollection testCollection = db.getCollection("test");
        testCollection.insert(createDocument("a", "b"));
        db.close();

        assertTrue(testCollection.isClosed());
    }

    @Test
    public void testValidateUser() {
        assertTrue(db.validateUser("test-user", "test-password"));
        assertFalse(db.validateUser("test-user1", "test-password"));
        assertFalse(db.validateUser("test-user", "test-password1"));
        assertFalse(db.validateUser("test-user", null));
        assertFalse(db.validateUser(null, null));
    }

    @Test
    public void testGetCollection() {
        NitriteCollection collection = db.getCollection("test-collection");
        assertNotNull(collection);
        assertEquals(collection.getName(), "test-collection");
    }

    @Test
    public void testGetRepository() {
        ObjectRepository<NitriteTest> repository = db.getRepository(NitriteTest.class);
        assertNotNull(repository);
        assertEquals(repository.getType(), NitriteTest.class);
    }

    @Test
    public void testGetRepositoryWithKey() {
        ObjectRepository<NitriteTest> repository = db.getRepository("key", NitriteTest.class);
        assertNotNull(repository);
        assertEquals(repository.getType(), NitriteTest.class);
        assertFalse(db.hasRepository(NitriteTest.class));
        assertTrue(db.hasRepository("key", NitriteTest.class));
    }

    @Test
    public void testMultipleGetCollection() {
        NitriteCollection collection = db.getCollection("test-collection");
        assertNotNull(collection);
        assertEquals(collection.getName(), "test-collection");

        NitriteCollection collection2 = db.getCollection("test-collection");
        assertNotNull(collection2);
        assertEquals(collection2.getName(), "test-collection");
    }

    @Test
    public void testMultipleGetRepository() {
        ObjectRepository<NitriteTest> repository = db.getRepository(NitriteTest.class);
        assertNotNull(repository);
        assertEquals(repository.getType(), NitriteTest.class);

        ObjectRepository<NitriteTest> repository2 = db.getRepository(NitriteTest.class);
        assertNotNull(repository2);
        assertEquals(repository2.getType(), NitriteTest.class);
    }

    @Test(expected = NitriteIOException.class)
    public void testIssue112() {
        Nitrite db = Nitrite.builder().filePath("/tmp").openOrCreate();
        assertNull(db);
    }

    @Test
    public void testIssue185() throws InterruptedException {
        final ObjectRepository<Receipt> repository = db.getRepository(Receipt.class);
        final Receipt receipt = new Receipt();
        receipt.clientRef = "111-11111";
        receipt.status = Receipt.Status.PREPARING;
        final CountDownLatch latch = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 1000; ++i) {
                    try {
                        repository.update(receipt, true);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {
                        }
                        repository.remove(receipt);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                latch.countDown();
            }
        }).start();

        for (int i = 0; i < 1000; ++i) {
            repository.find(not(eq("status", Receipt.Status.COMPLETED)), FindOptions.sort("createdTimestamp", SortOrder.Descending)).toList();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        latch.await();
    }

    @Test
    public void testIssue193() throws InterruptedException {
        final ObjectRepository<Receipt> repository = db.getRepository(Receipt.class);
        final PodamFactory factory = new PodamFactoryImpl();
        final String[] refs = new String[] {"1", "2", "3", "4", "5"};
        final Random random = new Random();
        ExecutorService pool = Executors.newCachedThreadPool();

        final CountDownLatch latch = new CountDownLatch(10000);
        for (int i = 0; i < 10000; i++) {
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    int refIndex = random.nextInt(5);
                    Receipt receipt = factory.manufacturePojoWithFullData(Receipt.class);
                    receipt.setClientRef(refs[refIndex]);
                    repository.update(receipt, true);
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertTrue(repository.find().size() <= 5);
    }

    @Test
    public void testIssue212() {
        NitriteCollection collection = db.getCollection("test");
        Document doc1 = createDocument("key", "key").put("second_key", "second_key").put("third_key", "third_key");
        Document doc2 = createDocument("key", "key").put("second_key", "second_key").put("fourth_key", "fourth_key");
        Document doc = createDocument("fifth_key", "fifth_key");

        if(!collection.hasIndex("key")){
            collection.createIndex("key", IndexOptions.indexOptions(IndexType.NonUnique));
        }
        if(!collection.hasIndex("second_key")){
            collection.createIndex("second_key", IndexOptions.indexOptions(IndexType.NonUnique));
        }

        collection.insert(doc1, doc2);
        collection.update(Filters.and(Filters.eq("key", "key"),
            Filters.eq("second_key", "second_key")), doc, UpdateOptions.updateOptions(true));

        for (Document document : collection.find()) {
            System.out.println(document);
        }
    }

    @Test
    public void testIssue220() {
        NitriteCollection collection = db.getCollection("object");
        collection.drop();
        collection = db.getCollection("object");
        Document doc1 = createDocument("key", "key").put("second_key", "second_key").put("third_key", "third_key");
        Document doc2 = createDocument("key", "key").put("second_key", "second_key").put("fourth_key", "fourth_key");
        Document doc = createDocument("fifth_key", "fifth_key");
        
        collection.insert(doc1, doc2, doc);
        db.close();

        db = new NitriteBuilder()
            .filePath(fileName)
            .compressed()
            .openOrCreate("test-user", "test-password");
        collection = db.getCollection("object");
        Cursor documents = collection.find(Filters.eq("fifth_key", "fifth_key"));
        assertEquals(1, documents.size());
        assertEquals(doc, documents.firstOrDefault());
    }

    @Test
    public void testIssue245() throws InterruptedException {
        class ThreadRunner implements Runnable {
            @Override
            public void run() {
                try {
                    long id = Thread.currentThread().getId();
                    NitriteCollection collection = db.getCollection("testIssue245");

                    for (int i = 0; i < 5; i++) {

                        System.out.println("Thread ID = " + id + " Inserting doc " + i);
                        Document doc = Document.createDocument(UUID.randomUUID().toString(), UUID.randomUUID().toString());

                        WriteResult result = collection.insert(doc);//db.commit();
                        System.out.println("Result of insert = " + result.getAffectedCount());
                        System.out.println("Thread id = " + id + " --> count = " + collection.size());

                        Thread.sleep(10);

                    }//for closing

                    collection.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        Thread t0 = new Thread(new ThreadRunner());
        Thread t1 = new Thread(new ThreadRunner());
        Thread t2 = new Thread(new ThreadRunner());

        t0.start();
        t1.start();
        t2.start();

        Thread.sleep(10 * 1000);

        t0.join();
        t1.join();
        t2.join();

        NitriteCollection collection = db.getCollection("testIssue245");
        System.out.println("No of Documents = " + collection.size());
        collection.close();
        db.close();
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Indices({
        @Index(value = "synced", type = IndexType.NonUnique)
    })
    public static class Receipt {
        public enum Status {
            COMPLETED,
            PREPARING,
        }

        private Status status;
        @Id
        private String clientRef;
        public boolean synced;
        private Long createdTimestamp = System.currentTimeMillis();
    }
}
