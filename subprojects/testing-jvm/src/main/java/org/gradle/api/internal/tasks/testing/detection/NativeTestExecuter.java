/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection;

import com.google.common.collect.Lists;
import org.gradle.api.Nullable;
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.processors.MaxNParallelTestClassProcessor;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.Factory;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.io.LineBufferingOutputStream;
import org.gradle.internal.io.TextStream;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;
import org.gradle.internal.progress.OperationIdGenerator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.DefaultExecHandleBuilder;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.ExecHandleListener;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class NativeTestExecuter implements TestExecuter {
    private final ServiceRegistry serviceRegistry;

    public NativeTestExecuter(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @Inject
    public ExecActionFactory getExecActionFactory() {
        throw new UnsupportedOperationException();
    }

    public ExecHandleBuilder getExecHandleBuilder() {
        return new DefaultExecHandleBuilder();
    }
    @Inject
    public ActorFactory getActorFactory() {
        throw new UnsupportedOperationException();
    }
    @Inject
    public BuildOperationWorkerRegistry getBuildOperationWorkerRegistry() {
        throw new UnsupportedOperationException();
    }
    public IdGenerator getIdGenerator() {
        return new LongIdGenerator();
    }
    @Inject
    public TimeProvider getTimeProvider() {
        throw new UnsupportedOperationException();
    }

    public ServiceRegistry getServices() {
        return serviceRegistry;
    }

    @Override
    public void execute(Test testTask, TestResultProcessor testResultProcessor) {
        final File executable = (File)testTask.getExtensions().getExtraProperties().get("testBinary");
        final File workingDir = (File)testTask.getExtensions().getExtraProperties().get("workingDir");
        final BuildOperationWorkerRegistry.Operation currentOperation = getBuildOperationWorkerRegistry().getCurrent();
        final Factory<TestClassProcessor> forkingProcessorFactory = new Factory<TestClassProcessor>() {
            public TestClassProcessor create() {
                return new NativeTestClassProcessor(currentOperation, executable, workingDir, getExecHandleBuilder(), getIdGenerator(), getTimeProvider());
            }
        };

        TestClassProcessor processor = new MaxNParallelTestClassProcessor(testTask.getMaxParallelForks(),
            forkingProcessorFactory, getActorFactory());

        Runnable detector = new NativeTestDetector(processor, executable, workingDir, getExecActionFactory());

        final Object testTaskOperationId = OperationIdGenerator.generateId(testTask);

        new TestMainAction(detector, processor, testResultProcessor, new TrueTimeProvider(), testTaskOperationId, testTask.getPath(), "Gradle Test Run " + testTask.getPath()).run();
    }

    static class NativeTestDetector implements Runnable {
        private final TestClassProcessor testClassProcessor;
        private final File executable;
        private final ExecActionFactory execActionFactory;
        private final File workingDir;

        NativeTestDetector(TestClassProcessor testClassProcessor, File executable, File workingDir, ExecActionFactory execActionFactory) {
            this.testClassProcessor = testClassProcessor;
            this.executable = executable;
            this.workingDir = workingDir;
            this.execActionFactory = execActionFactory;
        }

        @Override
        public void run() {
            GoogleTestList listParser = new GoogleTestList();
            ExecAction action = execActionFactory.newExecAction();
            action.executable(executable);
            action.args("--gtest_list_tests");
            action.setWorkingDir(workingDir);
            action.setStandardOutput(new LineBufferingOutputStream(listParser));
            action.execute();

            for (String testName : listParser.tests) {
                System.out.println("Found " + testName);
                TestClassRunInfo testClass = new DefaultTestClassRunInfo(testName);
                testClassProcessor.processTestClass(testClass);
            }
        }
    }

    static class GoogleTestList implements TextStream {
        private String testCase;
        private final List<String> tests = Lists.newArrayList();

        @Override
        public void text(String text) {
            if (text.startsWith("#")) {
                // ignore comments
                return;
            }
            if (text.contains(".")) {
                testCase = text.trim();
                return;
            }
            tests.add(testCase + text.trim());
        }

        @Override
        public void endOfStream(@Nullable Throwable failure) {
            if (failure != null) {
                UncheckedException.throwAsUncheckedException(failure);
            }
        }
    }

    private static class NativeTestClassProcessor implements TestClassProcessor {
        private final BuildOperationWorkerRegistry.Operation owner;
        private BuildOperationWorkerRegistry.Completion workerCompletion;
        private TestResultProcessor resultProcessor;
        private ExecHandle execHandle;
        private final ExecHandleBuilder execHandleBuilder;
        private final IdGenerator<?> idGenerator;
        private final TimeProvider timeProvider;

        NativeTestClassProcessor(BuildOperationWorkerRegistry.Operation owner, File executable, File workingDir, ExecHandleBuilder execHandleBuilder, IdGenerator<?> idGenerator, TimeProvider timeProvider) {
            this.owner = owner;
            this.execHandleBuilder = execHandleBuilder;
            this.idGenerator = idGenerator;
            this.timeProvider = timeProvider;
            execHandleBuilder.executable(executable);
            execHandleBuilder.setWorkingDir(workingDir);
        }


        @Override
        public void startProcessing(TestResultProcessor resultProcessor) {
            this.resultProcessor = resultProcessor;
        }

        @Override
        public void processTestClass(TestClassRunInfo testClass) {
            workerCompletion = owner.operationStart();
            try {
                execHandle = executeTest(testClass.getTestClassName());
                System.out.println("Executing " + testClass + " " + execHandle);
                execHandle.waitForFinish();
            } finally {
                workerCompletion.operationFinish();
            }
        }

        private ExecHandle executeTest(String testName) {
            execHandleBuilder.setArgs(Arrays.asList("--gtest_filter=" + testName));
            int endOfTestSuiteName = testName.indexOf('.');
            String testSuite  = testName.substring(0, endOfTestSuiteName);
            String testMethod = testName.substring(endOfTestSuiteName+1);
            TestDescriptorInternal testDescriptorInternal = new DefaultTestMethodDescriptor(idGenerator.generateId(), testSuite, testMethod);
            TextStream stdOut = new TextStreamToProcessor(testDescriptorInternal, TestOutputEvent.Destination.StdOut, resultProcessor);
            TextStream stdErr = new TextStreamToProcessor(testDescriptorInternal, TestOutputEvent.Destination.StdErr, resultProcessor);
            NativeTestExecutionEventGenerator adapter = new NativeTestExecutionEventGenerator(testDescriptorInternal, resultProcessor, timeProvider);
            execHandleBuilder.setStandardOutput(new LineBufferingOutputStream(stdOut));
            execHandleBuilder.setErrorOutput(new LineBufferingOutputStream(stdErr));
            ExecHandle handle = execHandleBuilder.build();
            handle.addListener(adapter);
            return handle.start();
        }

        @Override
        public void stop() {
//            if (execHandle != null) {
//                try {
//                    execHandle.abort();
//                    execHandle.waitForFinish();
//                } finally {
//                    workerCompletion.operationFinish();
//                }
//            }
        }
    }

    private static class NativeTestExecutionEventGenerator implements ExecHandleListener {

        private final TestResultProcessor resultProcessor;
        private final TestDescriptorInternal testDescriptorInternal;
        private final TimeProvider timeProvider;

        private NativeTestExecutionEventGenerator(TestDescriptorInternal testDescriptorInternal, TestResultProcessor resultProcessor, TimeProvider timeProvider) {
            this.resultProcessor = resultProcessor;
            this.testDescriptorInternal = testDescriptorInternal;
            this.timeProvider = timeProvider;
        }

        @Override
        public void executionStarted(ExecHandle execHandle) {
            resultProcessor.started(testDescriptorInternal, new TestStartEvent(timeProvider.getCurrentTime()));
        }

        @Override
        public void executionFinished(ExecHandle execHandle, ExecResult execResult) {
            if (execResult.getExitValue() == 0) {
                resultProcessor.completed(testDescriptorInternal.getId(), new TestCompleteEvent(timeProvider.getCurrentTime(), TestResult.ResultType.SUCCESS));
            } else {
                resultProcessor.completed(testDescriptorInternal.getId(), new TestCompleteEvent(timeProvider.getCurrentTime(), TestResult.ResultType.FAILURE));
            }
        }
    }

    private static class TextStreamToProcessor implements TextStream {
        private final TestResultProcessor processor;
        private final TestOutputEvent.Destination destination;
        private final TestDescriptorInternal testDescriptorInternal;

        private TextStreamToProcessor(TestDescriptorInternal testDescriptorInternal, TestOutputEvent.Destination destination, TestResultProcessor processor) {
            this.processor = processor;
            this.destination = destination;
            this.testDescriptorInternal = testDescriptorInternal;
        }

        @Override
        public void text(String text) {
            System.out.println(text);
            processor.output(testDescriptorInternal.getId(), new DefaultTestOutputEvent(destination, text));
        }

        @Override
        public void endOfStream(@Nullable Throwable failure) {
            if (failure!=null) {
                processor.failure(testDescriptorInternal.getId(), failure);
            }
        }
    }
}
