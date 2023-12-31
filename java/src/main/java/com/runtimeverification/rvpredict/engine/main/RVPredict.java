/* ******************************************************************************
 * Copyright (c) 2013 University of Illinois
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * *****************************************************************************/
package com.runtimeverification.rvpredict.engine.main;

import com.runtimeverification.rvpredict.config.Configuration;
import com.runtimeverification.rvpredict.log.ILoggingEngine;
import com.runtimeverification.rvpredict.metadata.CompactMetadata;
import com.runtimeverification.rvpredict.metadata.Metadata;
import com.runtimeverification.rvpredict.metadata.MetadataInterface;
import com.runtimeverification.rvpredict.order.JavaHappensBeforeRaceDetector;
import com.runtimeverification.rvpredict.performance.AnalysisLimit;
import com.runtimeverification.rvpredict.smt.RaceSolver;
import com.runtimeverification.rvpredict.trace.LLVMCompactTraceCache;
import com.runtimeverification.rvpredict.trace.LLVMTraceCache;
import com.runtimeverification.rvpredict.trace.Trace;
import com.runtimeverification.rvpredict.trace.TraceCache;
import com.runtimeverification.rvpredict.util.Logger;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.runtimeverification.rvpredict.config.Configuration.JAVA_EXECUTABLE;
import static com.runtimeverification.rvpredict.config.Configuration.RV_PREDICT_JAR;

/**
 * Class for predicting violations from a logged execution.
 *
 * Splits the log in segments of length {@link Configuration#windowSize},
 * each of them being executed as a {@link RaceDetector} task.
 *
 * Objects of this class should be close()d.
 */
public class RVPredict implements AutoCloseable {

    private final Configuration config;
    private final TraceCache traceCache;
    private final RaceDetector detector;

    public RVPredict(Configuration config) {
        MetadataInterface metadata;
        this.config = config;
        if (config.isLLVMPrediction()) {
            if (config.isCompactTrace()) {
                CompactMetadata compactMetadata = new CompactMetadata();
                metadata = compactMetadata;
                traceCache = new LLVMCompactTraceCache(config, compactMetadata);
            } else {
                Metadata singleton = Metadata.singleton();
                metadata = singleton;
                traceCache = new LLVMTraceCache(config, singleton);
            }
        } else {
            metadata = Metadata.readFrom(config.getMetadataPath());
            traceCache = new TraceCache(config, metadata);
        }
        if (config.isHappensBefore()) {
            this.detector = new JavaHappensBeforeRaceDetector(config, metadata);
        } else {
            this.detector = new MaximalRaceDetector(config, RaceSolver.create(config));
        }
    }

    @Override
    public void close() throws Exception {
        detector.close();
    }

    public void start() {
        try {
            AnalysisLimit globalAnalysisLimit =
                    new AnalysisLimit(Clock.systemUTC(), "Global", Optional.empty(), config.global_timeout, config.logger());
            traceCache.setup();
            // process the trace window by window
            Trace trace;
            while (true) {
                if ((trace = traceCache.getTraceWindow()) != null) {
                    AnalysisLimit windowAnalysisLimit =
                            new AnalysisLimit(
                                    Clock.systemUTC(),
                                    "Window",
                                    Optional.of(globalAnalysisLimit),
                                    config.window_timeout, config.logger());
                    detector.run(trace, windowAnalysisLimit);
                } else {
                    break;
                }
            }

            List<String> reports = detector.getRaceReports();
            if (reports.isEmpty()) {
                config.logger().report("No races found.", Logger.MSGTYPE.VERBOSE);
            } else {
                reports.forEach(r -> config.logger().report(r, Logger.MSGTYPE.REPORT));
            }
            traceCache.getLockGraph().runDeadlockDetection();
        } catch (IOException e) {
            System.err.println("Error: I/O error during prediction.");
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static Thread getPredictionThread(Configuration config, ILoggingEngine loggingEngine) {
        return new Thread("Cleanup Thread") {
            @Override
            public void run() {
                if (loggingEngine != null) {
                    try {
                        loggingEngine.finishLogging();
                    } catch (IOException e) {
                        System.err.println("Warning: I/O Error while logging the execution. The log might be unreadable.");
                        System.err.println(e.getMessage());
                    }
                }

                if (config.isOfflinePrediction()) {
                    if (config.isLogging()) {
                        config.logger().reportPhase(Configuration.LOGGING_PHASE_COMPLETED);
                    }

                    Process proc = null;
                    try {
                        proc = startPredictionProcess(config);
                        StreamGobbler errorGobbler = StreamGobbler.spawn(proc.getErrorStream(), System.err);
                        StreamGobbler outputGobbler = StreamGobbler.spawn(proc.getInputStream(), System.out);

                        proc.waitFor();

                        // the join() here is necessary even if the gobbler
                        // threads are non-daemon because we are already in the
                        // shutdown hook
                        errorGobbler.join();
                        outputGobbler.join();
                    } catch (IOException | InterruptedException e) {
                        if (proc != null) {
                            proc.destroy();
                        }
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    /**
     * Starts a prediction-only RV-Predict instance in a subprocess.
     */
    private static Process startPredictionProcess(Configuration config) throws IOException {
        List<String> appArgs = new ArrayList<>();
        appArgs.add(JAVA_EXECUTABLE);
        appArgs.add("-ea");
        appArgs.add("-cp");
        appArgs.add(RV_PREDICT_JAR);
        appArgs.add(RVPredict.class.getName());
        int startOfRVArgs = appArgs.size();
        Collections.addAll(appArgs, config.getArgs());

        assert config.isOfflinePrediction();
        if (!appArgs.contains(Configuration.opt_only_predict)) {
            appArgs.add(startOfRVArgs, Configuration.opt_only_predict);
            appArgs.add(startOfRVArgs + 1, config.getLogDir());
        }
        return new ProcessBuilder(appArgs).start();
    }

    /**
     * The entry point of prediction-only RV-Predict started as a subprocess by
     * {@link RVPredict#startPredictionProcess(Configuration)}.
     */
    public static void main(String[] args) {
        Configuration config = Configuration.instance(args);
        try (RVPredict rvPredict = new RVPredict(config)) {
            rvPredict.start();
        } catch (Exception e) {
            System.err.println("Error during prediction.");
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
