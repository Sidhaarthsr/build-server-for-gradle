// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.server;

import static com.microsoft.java.bs.core.Launcher.LOGGER;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;

import com.microsoft.java.bs.core.internal.log.BspTraceEntity;
import com.microsoft.java.bs.core.internal.services.BuildTargetService;
import com.microsoft.java.bs.core.internal.services.LifecycleService;
import com.microsoft.java.bs.core.internal.utils.concurrent.CancellableFuture;

import ch.epfl.scala.bsp4j.BuildServer;
import ch.epfl.scala.bsp4j.CleanCacheParams;
import ch.epfl.scala.bsp4j.CleanCacheResult;
import ch.epfl.scala.bsp4j.CompileParams;
import ch.epfl.scala.bsp4j.CompileResult;
import ch.epfl.scala.bsp4j.DebugSessionAddress;
import ch.epfl.scala.bsp4j.DebugSessionParams;
import ch.epfl.scala.bsp4j.DependencyModulesParams;
import ch.epfl.scala.bsp4j.DependencyModulesResult;
import ch.epfl.scala.bsp4j.DependencySourcesParams;
import ch.epfl.scala.bsp4j.DependencySourcesResult;
import ch.epfl.scala.bsp4j.InitializeBuildParams;
import ch.epfl.scala.bsp4j.InitializeBuildResult;
import ch.epfl.scala.bsp4j.InverseSourcesParams;
import ch.epfl.scala.bsp4j.InverseSourcesResult;
import ch.epfl.scala.bsp4j.JavaBuildServer;
import ch.epfl.scala.bsp4j.JavacOptionsParams;
import ch.epfl.scala.bsp4j.JavacOptionsResult;
import ch.epfl.scala.bsp4j.OutputPathsParams;
import ch.epfl.scala.bsp4j.OutputPathsResult;
import ch.epfl.scala.bsp4j.ResourcesParams;
import ch.epfl.scala.bsp4j.ResourcesResult;
import ch.epfl.scala.bsp4j.RunParams;
import ch.epfl.scala.bsp4j.RunResult;
import ch.epfl.scala.bsp4j.ScalaBuildServer;
import ch.epfl.scala.bsp4j.ScalaMainClassesParams;
import ch.epfl.scala.bsp4j.ScalaMainClassesResult;
import ch.epfl.scala.bsp4j.ScalaTestClassesParams;
import ch.epfl.scala.bsp4j.ScalaTestClassesResult;
import ch.epfl.scala.bsp4j.ScalacOptionsParams;
import ch.epfl.scala.bsp4j.ScalacOptionsResult;
import ch.epfl.scala.bsp4j.SourcesParams;
import ch.epfl.scala.bsp4j.SourcesResult;
import ch.epfl.scala.bsp4j.TestParams;
import ch.epfl.scala.bsp4j.TestResult;
import ch.epfl.scala.bsp4j.WorkspaceBuildTargetsResult;

/**
 * The implementation of the Build Server Protocol.
 */
public class GradleBuildServer implements BuildServer, JavaBuildServer, ScalaBuildServer {

  private LifecycleService lifecycleService;

  private BuildTargetService buildTargetService;

  public GradleBuildServer(LifecycleService lifecycleService,
      BuildTargetService buildTargetService) {
    this.lifecycleService = lifecycleService;
    this.buildTargetService = buildTargetService;
  }

  @Override
  public CompletableFuture<InitializeBuildResult> buildInitialize(InitializeBuildParams params) {
    try {
      return handleRequest("build/initialize", cc -> lifecycleService.initializeServer(params, cc));
    } catch (CancellationException e) {
      // TODO : reset to prior state?
      return null;
    }
  }

  @Override
  public void onBuildInitialized() {
    handleNotification("build/initialized", lifecycleService::onBuildInitialized, true /* async */);
  }

  @Override
  public CompletableFuture<Object> buildShutdown() {
    try {
      return handleRequest("build/shutdown", cc -> lifecycleService.shutdown(cc));
    } catch (CancellationException e) {
      // TODO : reset to prior state?
      return null;
    }
  }

  @Override
  public void onBuildExit() {
    handleNotification("build/exit", lifecycleService::exit, false /* async */);
  }

  @Override
  public CompletableFuture<WorkspaceBuildTargetsResult> workspaceBuildTargets() {
    final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("workspace/buildTargets",
          cc -> buildTargetService.getWorkspaceBuildTargets(cc, tokenSource.token()));
    } catch (CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<Object> workspaceReload() {
    final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("workspace/reload", cc -> {
        buildTargetService.reloadWorkspace(cc, tokenSource.token());
        return null;
      });
    } catch (CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<SourcesResult> buildTargetSources(SourcesParams params) {
    final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("buildTarget/sources",
          cc -> buildTargetService.getBuildTargetSources(params, cc, tokenSource.token()));
    } catch (CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<InverseSourcesResult> buildTargetInverseSources(
      InverseSourcesParams params) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'buildTargetInverseSources'");
  }

  @Override
  public CompletableFuture<DependencySourcesResult> buildTargetDependencySources(
      DependencySourcesParams params) {
    final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("buildTarget/dependencySources",
          cc -> buildTargetService.getBuildTargetDependencySources(params, cc, tokenSource.token()));
    } catch (final CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<ResourcesResult> buildTargetResources(ResourcesParams params) {
    final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("buildTarget/resources", cc -> buildTargetService.getBuildTargetResources(params, cc, tokenSource.token()));
    } catch (CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<OutputPathsResult> buildTargetOutputPaths(OutputPathsParams params) {
    final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("buildTarget/outputPaths", cc -> buildTargetService.getBuildTargetOutputPaths(params, cc, tokenSource.token()));
    } catch (CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<CompileResult> buildTargetCompile(CompileParams params) {
    final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("buildTarget/compile", cc -> buildTargetService.compile(params, cc, tokenSource.token()));
    } catch (CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<TestResult> buildTargetTest(TestParams params) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'buildTargetTest'");
  }

  @Override
  public CompletableFuture<RunResult> buildTargetRun(RunParams params) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'buildTargetRun'");
  }

  @Override
  public CompletableFuture<DebugSessionAddress> debugSessionStart(DebugSessionParams params) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'debugSessionStart'");
  }

  @Override
  public CompletableFuture<CleanCacheResult> buildTargetCleanCache(CleanCacheParams params) {
    final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("buildTarget/cleanCache", cc -> buildTargetService.cleanCache(params, cc, tokenSource.token()));
    } catch (CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<DependencyModulesResult> buildTargetDependencyModules(
      DependencyModulesParams params) {
        final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("buildTarget/dependencyModules",
          cc -> buildTargetService.getBuildTargetDependencyModules(params, cc, tokenSource.token()));
    } catch (CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<JavacOptionsResult> buildTargetJavacOptions(JavacOptionsParams params) {
    final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("buildTarget/javacOptions", cc -> buildTargetService.getBuildTargetJavacOptions(params, cc, tokenSource.token()));
    } catch (CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<ScalacOptionsResult> buildTargetScalacOptions(ScalacOptionsParams params) {
    final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    try {
      return handleRequest("buildTarget/scalacOptions",
          cc -> buildTargetService.getBuildTargetScalacOptions(params, cc, tokenSource.token()));
    } catch (CancellationException e) {
      tokenSource.cancel();
      return null;
    }
  }

  @Override
  public CompletableFuture<ScalaTestClassesResult> buildTargetScalaTestClasses(
      ScalaTestClassesParams params) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'buildTargetScalaTestClasses'");
  }

  @Override
  public CompletableFuture<ScalaMainClassesResult> buildTargetScalaMainClasses(
      ScalaMainClassesParams params) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'buildTargetScalaMainClasses'");
  }

  private void handleNotification(String methodName, Runnable runnable, boolean async) {
    BspTraceEntity entity = new BspTraceEntity.Builder()
        .operationName(escapeMethodName(methodName))
        .build();
    LOGGER.log(Level.INFO, "Received notification '" + methodName + "'.", entity);
    if (async) {
      CompletableFuture.runAsync(runnable);
    } else {
      runnable.run();
    }
  }

  private <R> CompletableFuture<R> handleRequest(String methodName,
      Function<CancelChecker, R> supplier) {
    return runAsync(methodName, supplier);
  }

  public <T, R> CompletableFuture<R> handleRequest(String methodName,
      BiFunction<CancelChecker, T, R> function, T arg) {
    LOGGER.info("Received request '" + methodName + "'.");
    return runAsync(methodName, cancelChecker -> function.apply(cancelChecker, arg));
  }

  private <T> CompletableFuture<T> runAsync(String methodName, Function<CancelChecker, T> request) {
    long startTime = System.nanoTime();

    return CancellableFuture.from(CompletableFutures.computeAsync(request))
        .thenApply(Either::<Throwable, T>forRight)
        .exceptionally(Either::forLeft)
        .thenCompose(either -> {
          long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
          return either.isLeft()
              ? failure(methodName, either.getLeft())
              : success(methodName, either.getRight(), elapsedTime);
        });
  }

  private <T> CompletableFuture<T> success(String methodName, T response, long elapsedTime) {
    BspTraceEntity entity = new BspTraceEntity.Builder()
        .operationName(escapeMethodName(methodName))
        .duration(String.valueOf(elapsedTime))
        .build();
    String message = String.format("Sending response '%s'. Processing request took %d ms.",
        methodName, elapsedTime);
    LOGGER.log(Level.INFO, message, entity);
    return CompletableFuture.completedFuture(response);
  }

  private <T> CompletableFuture<T> failure(String methodName, Throwable throwable) {
    String stackTrace = ExceptionUtils.getStackTrace(throwable);
    Throwable rootCause = ExceptionUtils.getRootCause(throwable);
    String rootCauseMessage = rootCause != null ? rootCause.getMessage() : null;
    BspTraceEntity entity = new BspTraceEntity.Builder()
        .operationName(escapeMethodName(methodName))
        .trace(stackTrace)
        .rootCauseMessage(rootCauseMessage)
        .build();
    String message = String.format("Failed to process '%s': %s", methodName, stackTrace);
    LOGGER.log(Level.SEVERE, message, entity);
    if (throwable instanceof ResponseErrorException) {
      return CompletableFuture.failedFuture(throwable);
    }
    return CompletableFuture.failedFuture(
        new ResponseErrorException(
            new ResponseError(ResponseErrorCode.InternalError, throwable.getMessage(), null)));
  }

  private String escapeMethodName(String name) {
    return name.replace('/', '-');
  }
}
