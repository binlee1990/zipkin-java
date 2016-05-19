/**
 * Copyright 2015-2016 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package zipkin.storage.cassandra;


import com.datastax.driver.core.CCMBridge;
import com.datastax.driver.core.Cluster;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;
import zipkin.Span;

public final class CassandraSpanConsumerTest {

    private static final Random RAND = new Random(System.currentTimeMillis());

    private static CCMBridge ccm = null;

    @BeforeClass
    public static void beforeClass() {
        ccm = CCMBridge.builder().withNodes(3).withVersion("3.4").build();//create("cassandra_testSpanConsumer_test", 3);
        ccm.start();
    }

    @AfterClass
    public static void afterClass() {
        if (null != ccm) {
            ccm.stop();
            ccm.remove();
        }
    }

    @Test
    public void testSpanConsumer() throws IOException, InterruptedException, ExecutionException {

        testSpanConsumerImpl(
                "testSpanConsumer",
                () -> Cluster.builder()
                        .addContactPointsWithPorts(ccm.addressOfNode(1), ccm.addressOfNode(2), ccm.addressOfNode(3))
                        .build());
    }

    void testSpanConsumerImpl(String keyspace, Supplier<Cluster> cluster) throws IOException, InterruptedException, ExecutionException {
        Schema.ensureExists(keyspace, cluster.get().connect());

        CassandraSpanConsumer repo = new CassandraSpanConsumer(cluster.get().connect(keyspace), 10);

        Endpoint ep = Endpoint.create("service", 127 << 24 | 1, 8080);

        Queue<Future> q = new ArrayDeque<>();

        for (int i = 0 ; i < 100; ++i) {

            long micros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())
                    + TimeUnit.NANOSECONDS.toMicros(System.nanoTime());

            Span span1 = Span.builder()
                    .traceId(RAND.nextInt(1000))
                    .name("methodcall")
                    .id(RAND.nextLong())
                    .timestamp(micros)
                    .duration(RAND.nextInt(100) *10L)
                    .annotations(Arrays.asList(
                            Annotation.create(micros, "cs", ep),
                            Annotation.create(micros + 10, "cs", ep)))
                    .addBinaryAnnotation(BinaryAnnotation.create("BAH", "BEH", ep)).build();

            Span span2 = Span.builder()
                    .traceId(RAND.nextInt(1000))
                    .name("methodcall")
                    .id(RAND.nextLong())
                    .timestamp(micros)
                    .duration(RAND.nextInt(100) *10L)
                    .annotations(Arrays.asList(
                            Annotation.create(micros + 1, "cs", ep),
                            Annotation.create(micros + 11, "cs", ep)))
                    .addBinaryAnnotation(BinaryAnnotation.create("BAH", "BEH", ep)).build();

            Span span3 = Span.builder()
                    .traceId(RAND.nextInt(1000))
                    .name("methodcall")
                    .id(RAND.nextLong())
                    .timestamp(micros)
                    .duration(RAND.nextInt(100) *10L)
                    .annotations(Arrays.asList(
                            Annotation.create(micros + 2, "cs", ep),
                            Annotation.create(micros + 12, "cs", ep)))
                    .addBinaryAnnotation(BinaryAnnotation.create("BAH", "BEH", ep)).build();

            q.add(repo.accept(Arrays.asList(span1, span2, span3)));
        }

        while (!q.isEmpty()) {
            q.peek().get();
            q.remove();
        }
    }

}
