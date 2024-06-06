// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.utils.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * Source :
 * https://github.com/JetBrains/intellij-scala/blob/9395de0f3ae6e4c3f7411edabc5374e6595162f5/bsp/src/org/jetbrains/bsp/protocol/session/CancellableFuture.java
 * 
 * JDK's CompletableFuture does not handle cancellation well.
 * When a future overrides a `cancel` method, it is lost
 * when doing transformations like `thenApply` or `thenCompose`.
 *
 * In Java 9, the new CompletableFuture's method was introduced:
 * `newIncompleteFuture`.
 * When using it, the cancel method of the original future will be called event
 * when the
 * `cancel` is done on a transformed one.
 *
 * When we know about CompletableFutures that override `cancel` methods, we
 * should convert them
 * to CancellableFuture before calling methods like `thenCompose`
 *
 * The `newIncompleteFuture` is not annotated with @Override, so the code can be
 * compiled with both JVM8 and JVM9
 * (and newer), but the actual cancellation effect takes place only with 9 or
 * newer.
 */
public class CancellableFuture<T> extends CompletableFuture<T> {

  private final CompletableFuture<?> original;

  public CancellableFuture(CompletableFuture<?> original) {
    this.original = original;
  }

  /**
   * Cancels the original future
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    original.cancel(mayInterruptIfRunning);
    return super.cancel(mayInterruptIfRunning);
  }

  @Override
  public <U> CompletableFuture<U> newIncompleteFuture() {
    return new CancellableFuture<>(original);
  }

  /**
   * Creates a new CancellableFuture from the given CompletableFuture.
   * If there's an error in the original future, the new one will be completed
   * exceptionally.
   * 
   * @param <U>      the type of the result value of the original future
   * @param original the original future
   * @return the new CancellableFuture
   */
  @SuppressWarnings("unchecked")
  public static <U> CancellableFuture<U> from(CompletableFuture<U> original) {
    CancellableFuture<U> result = new CancellableFuture<>(original);
    original.whenComplete((value, error) -> {
      if (error instanceof CancellationException) {
        result.complete((U) "Request cancelled");
      } else if (error != null) {
        result.completeExceptionally(error);
      } else {
        result.complete(value);
      }
    });
    return result;
  }
}
