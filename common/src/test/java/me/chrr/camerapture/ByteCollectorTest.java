package me.chrr.camerapture;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ByteCollectorTest {

    @Test
    public void testReconstructSegmentedBytes() {
        AtomicReference<byte[]> result = new AtomicReference<>();
        ByteCollector collector = new ByteCollector(result::set);

        byte[] part1 = new byte[]{1, 2, 3};
        byte[] part2 = new byte[]{4, 5};
        byte[] part3 = new byte[]{6, 7, 8, 9};

        assertTrue(collector.push(part1, 6));
        assertNull(result.get());

        assertTrue(collector.push(part2, 4));
        assertNull(result.get());

        assertTrue(collector.push(part3, 0));
        assertNotNull(result.get());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, result.get());
    }

    @Test
    public void testMalformedSegmentRejections() {
        ByteCollector collector = new ByteCollector((b) -> {});

        // Null bytes
        assertFalse(collector.push(null, 10));

        // Negative bytesLeft
        assertFalse(collector.push(new byte[]{1, 2}, -1));

        // Mismatched declared length on second push
        assertTrue(collector.push(new byte[]{1, 2, 3}, 7)); // expects total 10
        assertFalse(collector.push(new byte[]{4, 5}, 10));  // 3 + 2 + 10 = 15 != 10
    }

    @Test
    public void testSplitSingleSectionZeroCopy() {
        byte[] original = new byte[]{10, 20, 30, 40, 50};
        List<byte[]> received = new ArrayList<>();
        List<Integer> left = new ArrayList<>();

        ByteCollector.split(original, 100, (section, bytesLeft) -> {
            received.add(section);
            left.add(bytesLeft);
        });

        assertEquals(1, received.size());
        assertSame(original, received.get(0), "Single-section split should reuse input array without copying");
        assertEquals(0, left.get(0));
    }

    @Test
    public void testSplitMultiSection() {
        byte[] original = new byte[]{1, 2, 3, 4, 5, 6, 7};
        List<byte[]> received = new ArrayList<>();
        List<Integer> left = new ArrayList<>();

        ByteCollector.split(original, 3, (section, bytesLeft) -> {
            received.add(section);
            left.add(bytesLeft);
        });

        assertEquals(3, received.size());
        assertArrayEquals(new byte[]{1, 2, 3}, received.get(0));
        assertEquals(4, left.get(0));

        assertArrayEquals(new byte[]{4, 5, 6}, received.get(1));
        assertEquals(1, left.get(1));

        assertArrayEquals(new byte[]{7}, received.get(2));
        assertEquals(0, left.get(2));
    }
}
