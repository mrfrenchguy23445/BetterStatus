package org.updraft.betterstatus.api;
import org.updraft.betterstatus.model.ServerSnapshot;
@FunctionalInterface public interface StatusListener { void onStatusChange(ServerSnapshot previous,ServerSnapshot current); }