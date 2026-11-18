package com.zurrtum.create.infrastructure.transfer;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A single index of a {@link ResourceHandler}, addressed as an object.
 *
 * <h2>Why this exists</h2>
 * This is the hinge the whole transfer port turns on.
 * <p>
 * Fabric's Transfer API models a storage as a collection of {@code StorageView}s: you ask a
 * {@code Storage} for its views, and each one answers what it holds and can be inserted into or
 * extracted from. Create Fly's adapters are written against that shape in about 4,000 lines, with
 * roughly thirty call sites going through a view.
 * <p>
 * NeoForge 26.2's {@link ResourceHandler} is the same idea addressed differently: it is flat and
 * indexed, so instead of handing out view objects it takes an {@code int index} on every call.
 * Semantically the two are equivalent -- same insert/extract contract, same transaction model, same
 * immutable resource values. Only the addressing differs.
 * <p>
 * So rather than restructure 4,000 lines of working, upstream-maintained logic around an index
 * parameter, this re-creates the view as a two-field record over (handler, index). Upstream's call
 * sites then port by renaming types, and the behaviour they encode -- which is the valuable part,
 * and the part that took upstream real effort to get right -- carries over untouched.
 *
 * <h2>Transactions</h2>
 * Both APIs scope changes to a transaction, but they end one differently: Fabric has an explicit
 * {@code abort()}, while NeoForge rolls back whenever a transaction is closed without
 * {@code commit()}. So upstream's "simulate" methods -- the ones that opened a transaction, measured
 * what would move and aborted -- become methods that simply do not commit. In try-with-resources
 * that is the default, which is why the ported code reads as a plain omission.
 */
public record ResourceView<T extends Resource>(ResourceHandler<T> handler, int index) {

    public T getResource() {
        return handler.getResource(index);
    }

    public long getAmount() {
        return handler.getAmountAsLong(index);
    }

    public long getCapacity() {
        return handler.getCapacityAsLong(index, getResource());
    }

    public boolean isResourceBlank() {
        return getResource().isEmpty() || getAmount() <= 0;
    }

    public boolean isValid(T resource) {
        return handler.isValid(index, resource);
    }

    public int insert(T resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        return handler.insert(index, resource, amount, transaction);
    }

    public int extract(T resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        return handler.extract(index, resource, amount, transaction);
    }

    /**
     * Whether a handler can be inserted into.
     *
     * <p>Fabric's {@code Storage} answered this directly, and upstream uses it as an early-out so a
     * read-only storage is not probed. {@link ResourceHandler} has no equivalent query -- its
     * contract is simply that {@code insert} returns 0 when nothing can be inserted -- and there is
     * no way to ask without attempting a real insert, which is precisely what the guard exists to
     * avoid.
     *
     * <p>So this answers {@code true} and lets the insert report 0. The cost is a wasted call on a
     * read-only handler, not a wrong result. It lives here, rather than as a literal at each of the
     * twenty-five call sites, so the assumption is visible and there is one place to change if
     * NeoForge ever adds a capability probe.
     */
    public static boolean supportsInsertion(ResourceHandler<?> handler) {
        return true;
    }

    /** As {@link #supportsInsertion}, for extraction. */
    public static boolean supportsExtraction(ResourceHandler<?> handler) {
        return true;
    }

    /** Every index of the handler, empty ones included. */
    public static <T extends Resource> Iterable<ResourceView<T>> all(ResourceHandler<T> handler) {
        return () -> new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                return index < handler.size();
            }

            @Override
            public ResourceView<T> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return new ResourceView<>(handler, index++);
            }
        };
    }

    /**
     * The indices currently holding something, which is what upstream's {@code nonEmptyViews()}
     * call sites want.
     * <p>
     * Iteration is lazy and re-reads the handler as it goes, matching Fabric's behaviour: a view
     * obtained here reflects the handler's state when it is asked, not when iteration started. Code
     * that extracts while iterating therefore behaves as it did upstream.
     */
    public static <T extends Resource> Iterable<ResourceView<T>> nonEmpty(ResourceHandler<T> handler) {
        return () -> new Iterator<>() {
            private int index;
            private int next = -1;

            @Override
            public boolean hasNext() {
                if (next >= 0) {
                    return true;
                }
                int size = handler.size();
                while (index < size) {
                    int candidate = index++;
                    if (!handler.getResource(candidate).isEmpty() && handler.getAmountAsLong(candidate) > 0) {
                        next = candidate;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public ResourceView<T> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int result = next;
                next = -1;
                return new ResourceView<>(handler, result);
            }
        };
    }
}
