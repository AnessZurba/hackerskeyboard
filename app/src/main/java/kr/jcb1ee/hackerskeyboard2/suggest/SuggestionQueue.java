/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package kr.jcb1ee.hackerskeyboard2.suggest;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asks a {@link SuggestionEngine} on one background thread, so typing never waits for the
 * decoder. Only the latest request matters: a request that is superseded before it runs is
 * skipped, and a result that is superseded before it reaches the main thread is dropped.
 *
 * <p>All methods except the engine work are for the main (UI) thread.
 */
public final class SuggestionQueue {
    /**
     * AOSP's auto-correction threshold on the normalized score ("modest", the default
     * there): corrections below it are only offered, not applied.
     */
    public static final float AUTO_CORRECTION_THRESHOLD = 0.185f;

    /** Gets the latest result, on the main thread. */
    public interface Listener {
        void onSuggestions(Result result);
    }

    /** What the engine said about one typed word. */
    public static final class Result {
        public final String typed;
        /** Best first. */
        public final List<Suggestion> suggestions;
        /** Whether the word as typed is in the dictionary. */
        public final boolean typedIsWord;
        /** The engine's confidence in the first suggestion as a correction of {@code typed}. */
        public final float confidence;
        final long generation;

        Result(long generation, String typed, List<Suggestion> suggestions, boolean typedIsWord,
                float confidence) {
            this.generation = generation;
            this.typed = typed;
            this.suggestions = Collections.unmodifiableList(suggestions);
            this.typedIsWord = typedIsWord;
            this.confidence = confidence;
        }

        /**
         * The word that space or punctuation should replace {@code typed} with, or null to keep
         * it: as in AOSP, only for a typed word of two or more letters that is not in the
         * dictionary (and has no digits), and only when the engine's confidence in its first
         * suggestion reaches {@code threshold}.
         */
        public String autoCorrection(float threshold) {
            if (typedIsWord || typed.length() < 2 || suggestions.isEmpty()) return null;
            for (int i = 0; i < typed.length(); i++) {
                if (Character.isDigit(typed.charAt(i))) return null;
            }
            Suggestion first = suggestions.get(0);
            if (first.word.equals(typed) || confidence < threshold) return null;
            return first.word;
        }
    }

    private final SuggestionEngine mEngine;
    private final ExecutorService mWorker;
    private final Executor mMain;
    private final Listener mListener;
    // Bumped by every request and cancel; work and results of older generations are dropped.
    private final AtomicLong mGeneration = new AtomicLong();
    private Future<Result> mLatest;
    private volatile boolean mClosed;

    /**
     * @param main runs result deliveries on the main thread (e.g. a main-looper Handler's post)
     */
    public SuggestionQueue(SuggestionEngine engine, Executor main, Listener listener) {
        this(engine, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "word-suggestions");
            t.setDaemon(true);
            return t;
        }), main, listener);
    }

    SuggestionQueue(SuggestionEngine engine, ExecutorService worker, Executor main,
            Listener listener) {
        mEngine = engine;
        mWorker = worker;
        mMain = main;
        mListener = listener;
    }

    /**
     * Asks for up to {@code max} suggestions for {@code typed}; the result arrives at the
     * listener unless another request or {@link #cancel} comes first.
     */
    public void request(final String typed, final int[] xs, final int[] ys,
            final KeyGeometry keys, final int max) {
        if (mClosed) return;
        final long generation = mGeneration.incrementAndGet();
        try {
            mLatest = mWorker.submit(() -> {
                if (generation != mGeneration.get()) return null; // superseded while queued
                List<Suggestion> s = mEngine.suggestions(typed, xs, ys, null, keys, max);
                boolean valid = mEngine.isValidWord(typed);
                float confidence = s.isEmpty() ? 0f
                        : mEngine.autoCorrectionConfidence(typed, s.get(0));
                final Result result = new Result(generation, typed, s, valid, confidence);
                mMain.execute(() -> {
                    if (!mClosed && generation == mGeneration.get()) {
                        mListener.onSuggestions(result);
                    }
                });
                return result;
            });
        } catch (RejectedExecutionException e) {
            mLatest = null;
        }
    }

    /** Drops any pending request and result. */
    public void cancel() {
        mGeneration.incrementAndGet();
        mLatest = null;
    }

    /**
     * The result of the latest request, waiting at most {@code timeoutMs} for it (AOSP waits
     * like this when space or punctuation needs the auto-correction); null when there was no
     * request since the last cancel, or it did not finish in time.
     */
    public Result await(long timeoutMs) {
        Future<Result> latest = mLatest;
        if (latest == null) return null;
        try {
            Result r = latest.get(timeoutMs, TimeUnit.MILLISECONDS);
            return r != null && r.generation == mGeneration.get() ? r : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            return null;
        }
    }

    /** Waits until the work queued so far is done (tests). */
    public boolean flush(long timeoutMs) {
        try {
            mWorker.submit(() -> { }).get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RejectedExecutionException e) {
            return false;
        }
    }

    /** Stops the thread and closes the engine (after any query that is running). */
    public void close() {
        if (mClosed) return;
        mClosed = true;
        cancel();
        try {
            mWorker.execute(mEngine::close);
        } catch (RejectedExecutionException e) {
            mEngine.close();
        }
        mWorker.shutdown();
    }
}
