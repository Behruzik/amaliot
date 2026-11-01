package com.example.amaliyot.service.answer;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Generic registry that resolves a strategy from its domain key. */
public final class StrategyRegistry<K, S> {
    private final Map<K, S> strategies;

    public StrategyRegistry(Collection<S> strategies, Function<? super S, ? extends K> keyOf) {
        this.strategies = strategies.stream().collect(Collectors.toUnmodifiableMap(keyOf, Function.identity()));
    }

    public S get(K key) {
        S strategy = strategies.get(key);
        if (strategy == null) throw new IllegalStateException("No strategy registered for " + key);
        return strategy;
    }
}
