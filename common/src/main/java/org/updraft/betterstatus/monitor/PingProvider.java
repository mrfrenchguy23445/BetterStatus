package org.updraft.betterstatus.monitor;
import org.updraft.betterstatus.model.PingResult;
import java.util.concurrent.CompletableFuture;
@FunctionalInterface public interface PingProvider { CompletableFuture<PingResult> ping(); }