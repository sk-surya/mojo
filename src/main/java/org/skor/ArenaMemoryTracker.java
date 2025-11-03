package org.skor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.LongAdder;

public class ArenaMemoryTracker implements SegmentAllocator {
    private final Arena arena;
    private final LongAdder allocatedBytes = new LongAdder();
    private final LongAdder allocationCount = new LongAdder();

    public ArenaMemoryTracker(Arena arena) {
        this.arena = arena;
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        MemorySegment segment = arena.allocate(byteSize, byteAlignment);
        allocatedBytes.add(byteSize);
        allocationCount.increment();
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfByte layout, byte... values) {
        MemorySegment segment = arena.allocateFrom(layout, values);
        allocatedBytes.add(layout.byteSize() * values.length);
        allocationCount.increment();
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfShort layout, short... values) {
        MemorySegment segment = arena.allocateFrom(layout, values);
        allocatedBytes.add(layout.byteSize() * values.length);
        allocationCount.increment();
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfChar layout, char... values) {
        MemorySegment segment = arena.allocateFrom(layout, values);
        allocatedBytes.add(layout.byteSize() * values.length);
        allocationCount.increment();
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfInt layout, int... values) {
        MemorySegment segment = arena.allocateFrom(layout, values);
        allocatedBytes.add(layout.byteSize() * values.length);
        allocationCount.increment();
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfFloat layout, float... values) {
        MemorySegment segment = arena.allocateFrom(layout, values);
        allocatedBytes.add(layout.byteSize() * values.length);
        allocationCount.increment();
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfLong layout, long... values) {
        MemorySegment segment = arena.allocateFrom(layout, values);
        allocatedBytes.add(layout.byteSize() * values.length);
        allocationCount.increment();
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfDouble layout, double... values) {
        MemorySegment segment = arena.allocateFrom(layout, values);
        allocatedBytes.add(layout.byteSize() * values.length);
        allocationCount.increment();
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(String str) {
        MemorySegment segment = arena.allocateFrom(str);
        allocatedBytes.add(segment.byteSize());
        allocationCount.increment();
        return segment;
    }

    public long getTotalAllocatedBytes() {
        return allocatedBytes.longValue();
    }

    public long getAllocationCount() {
        return allocationCount.longValue();
    }

    public void reset() {
        allocatedBytes.reset();
        allocationCount.reset();
    }

    public void printStats() {
        System.out.printf("Memory Allocations: %d calls, %d bytes (%.2f KB, %.2f MB)%n",
            getAllocationCount(),
            getTotalAllocatedBytes(),
            getTotalAllocatedBytes() / 1024.0,
            getTotalAllocatedBytes() / (1024.0 * 1024.0));
    }

    public void close() {
        arena.close();
    }
}
