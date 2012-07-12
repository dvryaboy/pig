package org.apache.pig.spark;

import static org.apache.pig.builtin.mock.Storage.bag;
import static org.apache.pig.builtin.mock.Storage.tuple;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Level;
import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.executionengine.ExecJob;
import org.apache.pig.builtin.mock.Storage;
import org.apache.pig.builtin.mock.Storage.Data;
import org.apache.pig.data.Tuple;
import org.junit.Assert;
import org.junit.Test;

public class TestSpark {

    private static final ExecType MODE = ExecType.SPARK;
    private static final Log LOG = LogFactory.getLog(TestSpark.class);

    static {
        org.apache.log4j.Logger.getLogger("org.apache.pig.backend.hadoop.executionengine.spark").setLevel(Level.DEBUG);
    }

    private PigServer newPigServer() throws ExecException {
        Properties properties = new Properties();
        // to avoid pig running out of memory in LOCAL mode
        properties.put("io.sort.mb", "1");
//        properties.put("io.file.buffer.size", "128");

        PigServer pigServer = new PigServer(MODE, properties);

        return pigServer;
    }

    @Test
    public void testLoadStore() throws Exception {
        PigServer pigServer = newPigServer();
        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("test1"),
                tuple("test2"));
        pigServer.setBatchOn();
        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        pigServer.registerQuery("STORE A INTO 'output' using mock.Storage;");
        List<ExecJob> executeBatch = pigServer.executeBatch();
        // TODO: good stats
        //      assertEquals(1, executeBatch.size());
        //      assertTrue(executeBatch.get(0).hasCompleted());

        assertEquals(
                Arrays.asList(tuple("test1"), tuple("test2")),
                data.get("output"));
    }

    @Test
    public void testDump() throws Exception {
        PigServer pigServer = new PigServer(MODE);
        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("test1"),
                tuple("test2"));

        pigServer.setBatchOn();
        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        Iterator<Tuple> result = pigServer.openIterator("A");

        List<Tuple> resultList = new ArrayList<Tuple>();
        while (result.hasNext()) {
            resultList.add(result.next());
        }

        assertEquals(Arrays.asList(tuple("test1"), tuple("test2")), resultList);
    }

    @Test
    public void testGroupBy() throws Exception {
        PigServer pigServer = newPigServer();
        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("foo", "key1", "test1"),
                tuple("bar", "key1", "test2"),
                tuple("baz", "key2", "test3"));

        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        pigServer.registerQuery("B = GROUP A BY $1;");
        pigServer.registerQuery("STORE B INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(
                        tuple("key1", bag(tuple("foo", "key1", "test1"), tuple("bar", "key1", "test2"))),
                        tuple("key2", bag(tuple("baz", "key2", "test3")))),
                        sortByIndex(data.get("output"), 0));
    }

    private List<Tuple> sortByIndex(List<Tuple> out, final int i) {
        List<Tuple> result = new ArrayList<Tuple>(out);
        Collections.sort(result, new Comparator<Tuple>() {
            @Override
            public int compare(Tuple o1, Tuple o2) {
                try {
                    Comparable c1 = (Comparable)o1.get(i);
                    Comparable c2 = (Comparable)o2.get(i);
                    return c1.compareTo(c2);
                } catch (ExecException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return result;
    }

    @Test
    public void testGroupByFlatten() throws Exception {
        PigServer pigServer = newPigServer();
        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("test1"),
                tuple("test1"),
                tuple("test2"));

        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        pigServer.registerQuery("B = GROUP A BY $0;");
        pigServer.registerQuery("C = FOREACH B GENERATE FLATTEN(A);");
        pigServer.registerQuery("STORE C INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(tuple("test1"), tuple("test1"), tuple("test2")),
                data.get("output"));
    }

    @Test
    public void testCount() throws Exception {
        PigServer pigServer = newPigServer();
        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("test1"),
                tuple("test1"),
                tuple("test2"));

        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        pigServer.registerQuery("B = GROUP A BY $0;");
        pigServer.registerQuery("C = FOREACH B GENERATE COUNT(A);");
        pigServer.registerQuery("STORE C INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(tuple(2l), tuple(1l)),
                data.get("output"));
    }

    @Test
    public void testCountWithNoData() throws Exception {
        PigServer pigServer = new PigServer(ExecType.SPARK);
        Data data = Storage.resetData(pigServer);
        data.set("input");

        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        pigServer.registerQuery("B = GROUP A BY $0;");
        pigServer.registerQuery("C = FOREACH B GENERATE COUNT(A);");
        pigServer.registerQuery("STORE C INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(),
                data.get("output"));
    }

    @Test
    public void testForEach() throws Exception {
        PigServer pigServer = newPigServer();
        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("1"),
                tuple("12"),
                tuple("123"));

        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        pigServer.registerQuery("B = FOREACH A GENERATE StringSize($0);");
        pigServer.registerQuery("STORE B INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(tuple(1l), tuple(2l), tuple(3l)),
                data.get("output"));
    }


    @Test
    public void testForEachFlatten() throws Exception {
        PigServer pigServer = newPigServer();
        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple(bag(tuple("1"), tuple("2"), tuple("3"))),
                tuple(bag(tuple("4"), tuple("5"), tuple("6"))));

        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        pigServer.registerQuery("B = FOREACH A GENERATE FLATTEN($0);");
        pigServer.registerQuery("STORE B INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(tuple("1"), tuple("2"), tuple("3"), tuple("4"), tuple("5"), tuple("6")),
                data.get("output"));
    }

    @Test
    public void testSimpleUDF() throws Exception {
        PigServer pigServer = new PigServer(ExecType.SPARK);
        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("Foo"),
                tuple("BAR"),
                tuple("baT"));

        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        pigServer.registerQuery("B = FOREACH A GENERATE org.apache.pig.spark.LowercaseUDF($0);");
        pigServer.registerQuery("STORE B INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(tuple("foo"), tuple("bar"), tuple("bat")),
                data.get("output"));
    }

    @Test
    public void testFilter() throws Exception {
        PigServer pigServer = newPigServer();
        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("1"),
                tuple("2"),
                tuple("3"),
                tuple("1"));

        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        pigServer.registerQuery("B = FILTER A BY $0 == '1';");
        pigServer.registerQuery("STORE B INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(tuple("1"), tuple("1")),
                data.get("output"));
    }

    @Test
    public void testLimit() throws Exception {
        PigServer pigServer = newPigServer();
        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("1"),
                tuple("2"),
                tuple("3"),
                tuple("4"));

        pigServer.registerQuery("A = LOAD 'input' using mock.Storage;");
        pigServer.registerQuery("B = LIMIT A 2;");
        pigServer.registerQuery("STORE B INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(tuple("1"), tuple("2")),
                data.get("output"));
    }

    @Test
    public void testCoGroup() throws Exception {
        PigServer pigServer = newPigServer();
        Data data = Storage.resetData(pigServer);
        data.set("input1",
                tuple("foo", 1, "a"),
                tuple("foo", 2, "b"),
                tuple("foo", 3, "c"),
                tuple("foo", 1, "d"));
        data.set("input2",
                tuple("bar", 1, "e"),
                tuple("bar", 2, "f"),
                tuple("bar", 1, "g"));

        pigServer.registerQuery("A = LOAD 'input1' using mock.Storage;");
        pigServer.registerQuery("B = LOAD 'input2' using mock.Storage;");
        pigServer.registerQuery("C = COGROUP A BY $1, B BY $1;");
        pigServer.registerQuery("STORE C INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(
                        tuple(1,bag(tuple("foo", 1,"a"),tuple("foo", 1,"d")),bag(tuple("bar", 1,"e"),tuple("bar", 1,"g"))),
                        tuple(2,bag(tuple("foo", 2,"b")),bag(tuple("bar", 2,"f"))),
                        tuple(3,bag(tuple("foo", 3,"c")),bag())
                        ),
                        sortByIndex(data.get("output"), 0));
    }

    @Test
    public void testJoin() throws Exception {
        PigServer pigServer = newPigServer();
        Data data = Storage.resetData(pigServer);
        data.set("input1",
                tuple(1, "a"),
                tuple(2, "b"),
                tuple(3, "c"),
                tuple(1, "d"));
        data.set("input2",
                tuple(1, "e"),
                tuple(2, "f"),
                tuple(1, "g"));

        pigServer.registerQuery("A = LOAD 'input1' using mock.Storage;");
        pigServer.registerQuery("B = LOAD 'input2' using mock.Storage;");
        pigServer.registerQuery("C = JOIN A BY $0, B BY $0;");
        pigServer.registerQuery("STORE C INTO 'output' using mock.Storage;");

        assertEquals(
                Arrays.asList(
                        tuple(1, "a", 1, "e"),
                        tuple(1, "a", 1, "g"),
                        tuple(1, "d", 1, "e"),
                        tuple(1, "d", 1, "g"),
                        tuple(2, "b", 2, "f")
                        ),
                        data.get("output"));
    }

    @Test
    public void testCachingLoad() throws Exception {

        testCaching("A = LOAD 'input' using mock.Storage;" +
                "CACHE A;" +
                "STORE A INTO 'output' using mock.Storage;");
    }

    @Test
    public void testCachingLoadCast() throws Exception {

        testCaching("A = LOAD 'input' using mock.Storage as (foo:chararray);" +
                "CACHE A;" +
                "STORE A INTO 'output' using mock.Storage;");
    }

    @Test
    public void testCachingWithFilter() throws Exception {
        testCaching("A = LOAD 'input' using mock.Storage; " +
                "B = FILTER A by $0 == $0;" + // useless filter
                "A = FOREACH B GENERATE (chararray) $0;" +
                "CACHE A;" +
                "STORE A INTO 'output' using mock.Storage;");
    }

    @Test
    public void testCachingJoin() throws Exception {
        testCaching("A = LOAD 'input' using mock.Storage; " +
                "B = LOAD 'input' using mock.Storage; " +
                "A = JOIN A by $0, B by LOWER($0); " +
                "CACHE A; " +
                "STORE A INTO 'output' using mock.Storage;");
    }

    @Test
    public void testIgnoreWrongUDFCache() throws Exception {
        testIgnoreCache(
                "A = LOAD 'input' using mock.Storage; " +
                "B = LOAD 'input' using mock.Storage; " +
                "A = JOIN A by $0, B by LOWER($0); " +
                "CACHE A; " +
                "STORE A INTO 'output' using mock.Storage;",
                "A = LOAD 'input' using mock.Storage; " +
                "B = LOAD 'input' using mock.Storage; " +
                "A = JOIN A by $0, B by UPPER($0); " +
                "CACHE A; " +
                "STORE A INTO 'output' using mock.Storage;");
    }

    @Test
    public void testIgnoreDiffFilterCache() throws Exception {
        testIgnoreCache("A = LOAD 'input' using mock.Storage;" +
                "A = FILTER A by $0 == 'test1';" +
                "CACHE A;" +
                "STORE A INTO 'output' using mock.Storage;",
                "A = LOAD 'input' using mock.Storage;" +
                "A = FILTER A by $0 == 'test2';" +
                "CACHE A;" +
                "STORE A INTO 'output' using mock.Storage;");

    }

    public void testIgnoreCache(String query1, String query2) throws Exception {
        PigServer pigServer = newPigServer();

        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("test1"),
                tuple("test2"));

        pigServer.setBatchOn();
        pigServer.registerQuery(query1);
        pigServer.executeBatch();

        List<Tuple> originalOutput = data.get("output");
        LOG.debug("After first query: " + originalOutput);

        data = Storage.resetData(pigServer);
        data.set("input",
                tuple("test3"),
                tuple("test4"));
        pigServer.registerQuery(query2);
        pigServer.executeBatch();

        LOG.debug("After second query: " + data.get("output"));

        Assert.assertFalse(
                originalOutput.equals(
                        data.get("output")));
    }

    /**
     * Kind of a hack: To test whether caching is happening, we modify a file on disk after caching
     * it in Spark.
     */
    private void testCaching(String query) throws Exception {
        PigServer pigServer = newPigServer();

        Data data = Storage.resetData(pigServer);
        data.set("input",
                tuple("test1"),
                tuple("test2"));

        pigServer.setBatchOn();
        pigServer.registerQuery(query);
        pigServer.executeBatch();

        LOG.debug("After first query: " + data.get("output"));
        List<Tuple> originalOutput = data.get("output");

        data = Storage.resetData(pigServer);
        data.set("input",
                tuple("test3"),
                tuple("test4"));

        pigServer.registerQuery("STORE A INTO 'output' using mock.Storage;");
        pigServer.executeBatch();

        LOG.debug("After second query: " + data.get("output"));

        assertEquals(
                originalOutput,
                data.get("output"));
    }
}
