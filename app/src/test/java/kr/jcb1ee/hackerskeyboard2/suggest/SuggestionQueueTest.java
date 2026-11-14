package kr.jcb1ee.hackerskeyboard2.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** The background queue: only the latest request counts, stale results are dropped. */
public class SuggestionQueueTest {
    /** Answers "<typed>!" after an optional gate; counts its queries. */
    private static final class FakeEngine implements SuggestionEngine {
        final List<String> queried = Collections.synchronizedList(new ArrayList<>());
        volatile CountDownLatch gate;
        final CountDownLatch started = new CountDownLatch(1);
        volatile boolean closed;

        @Override
        public List<Suggestion> suggestions(String typed, int[] xs, int[] ys, String previous,
                KeyGeometry keys, int max) {
            queried.add(typed);
            started.countDown();
            CountDownLatch g = gate;
            if (g != null) {
                try {
                    g.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return Arrays.asList(new Suggestion(typed + "!", 10, 0));
        }

        @Override
        public boolean isValidWord(String word) {
            return word.equals("ok");
        }

        @Override
        public float autoCorrectionConfidence(String typed, Suggestion suggestion) {
            return 0.5f;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private final FakeEngine mEngine = new FakeEngine();
    private final ExecutorService mWorker = Executors.newSingleThreadExecutor();
    private final List<Runnable> mMainQueue = new ArrayList<>();
    private final List<SuggestionQueue.Result> mDelivered = new ArrayList<>();
    private final SuggestionQueue mQueue = new SuggestionQueue(mEngine, mWorker,
            r -> {
                synchronized (mMainQueue) {
                    mMainQueue.add(r);
                }
            }, mDelivered::add);

    @After
    public void tearDown() {
        mQueue.close();
    }

    /** Runs what the worker posted to the "main thread". */
    private void runMain() {
        List<Runnable> run;
        synchronized (mMainQueue) {
            run = new ArrayList<>(mMainQueue);
            mMainQueue.clear();
        }
        for (Runnable r : run) r.run();
    }

    @Test
    public void deliversOnTheMainThread() {
        mQueue.request("hel", null, null, null, 3);
        assertTrue(mQueue.flush(5000));
        assertTrue("not before the main thread runs", mDelivered.isEmpty());
        runMain();
        assertEquals(1, mDelivered.size());
        assertEquals("hel", mDelivered.get(0).typed);
        assertEquals("hel!", mDelivered.get(0).suggestions.get(0).word);
    }

    @Test
    public void requestsQueuedBehindASlowOneAreSkippedExceptTheLatest() {
        mEngine.gate = new CountDownLatch(1);
        mQueue.request("h", null, null, null, 3); // starts and blocks
        try {
            assertTrue(mEngine.started.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        mQueue.request("he", null, null, null, 3);
        mQueue.request("hel", null, null, null, 3);
        mEngine.gate.countDown();
        assertTrue(mQueue.flush(5000));
        runMain();
        assertEquals("the queued 'he' never ran", Arrays.asList("h", "hel"), mEngine.queried);
        assertEquals("only the latest arrives", 1, mDelivered.size());
        assertEquals("hel", mDelivered.get(0).typed);
    }

    @Test
    public void aResultSupersededOnItsWayIsDropped() {
        mQueue.request("he", null, null, null, 3);
        assertTrue(mQueue.flush(5000)); // computed, delivery waiting on the main thread
        mQueue.request("hel", null, null, null, 3);
        runMain(); // the "he" delivery: stale now
        assertTrue(mDelivered.isEmpty());
        assertTrue(mQueue.flush(5000));
        runMain();
        assertEquals(1, mDelivered.size());
        assertEquals("hel", mDelivered.get(0).typed);
    }

    @Test
    public void cancelDropsEverything() {
        mQueue.request("he", null, null, null, 3);
        mQueue.cancel();
        assertTrue(mQueue.flush(5000));
        runMain();
        assertTrue(mDelivered.isEmpty());
        assertNull(mQueue.await(10));
    }

    @Test
    public void awaitGetsTheLatestWithoutTheMainThread() {
        mEngine.gate = new CountDownLatch(1);
        mQueue.request("helo", null, null, null, 3);
        assertNull("times out while the decoder runs", mQueue.await(20));
        mEngine.gate.countDown();
        SuggestionQueue.Result r = mQueue.await(5000);
        assertNotNull(r);
        assertEquals("helo", r.typed);
    }

    @Test
    public void closeClosesTheEngineAndStopsRequests() throws Exception {
        mQueue.close();
        assertTrue(mWorker.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(mEngine.closed);
        mQueue.request("x", null, null, null, 3); // no exception
        assertNull(mQueue.await(10));
    }

    @Test
    public void autoCorrectionRules() {
        List<Suggestion> hello = Arrays.asList(new Suggestion("hello", 10, 0));
        assertEquals("hello", result("helo", hello, false, 0.5f).autoCorrection(0.185f));
        assertNull("not confident", result("helo", hello, false, 0.1f).autoCorrection(0.185f));
        assertNull("typed word is a word", result("helo", hello, true, 0.9f).autoCorrection(0.185f));
        assertNull("single letters stay", result("h", hello, false, 0.9f).autoCorrection(0.185f));
        assertNull("digits stay", result("he1o", hello, false, 0.9f).autoCorrection(0.185f));
        assertNull("nothing to correct to",
                result("helo", Collections.<Suggestion>emptyList(), false, 0.9f).autoCorrection(0.185f));
        assertNull("same word", result("hello", hello, false, 0.9f).autoCorrection(0.185f));
    }

    private static SuggestionQueue.Result result(String typed, List<Suggestion> s, boolean valid,
            float confidence) {
        return new SuggestionQueue.Result(1, typed, s, valid, confidence);
    }
}
