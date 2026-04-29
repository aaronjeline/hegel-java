package com.aaroneline.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PacketTest {

    private static byte[] serialize(Packet p) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Packet.write(out, p);
        return out.toByteArray();
    }

    private static Packet deserialize(byte[] bytes) throws IOException {
        return Packet.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void roundTrip_request() throws IOException {
        byte[] payload = "hello".getBytes();
        Packet original = new Packet(3, 1, false, payload);

        Packet restored = deserialize(serialize(original));

        assertEquals(original.streamId, restored.streamId);
        assertEquals(original.messageId, restored.messageId);
        assertFalse(restored.isReply);
        assertArrayEquals(original.payload, restored.payload);
    }

    @Test
    void roundTrip_reply() throws IOException {
        byte[] payload = new byte[]{0x01, 0x02, 0x03};
        Packet original = new Packet(5, 42, true, payload);

        Packet restored = deserialize(serialize(original));

        assertEquals(5, restored.streamId);
        assertEquals(42, restored.messageId);
        assertTrue(restored.isReply);
        assertArrayEquals(payload, restored.payload);
    }

    @Test
    void roundTrip_emptyPayload() throws IOException {
        Packet original = new Packet(0, 1, false, new byte[0]);
        Packet restored = deserialize(serialize(original));
        assertEquals(0, restored.payload.length);
        assertFalse(restored.isReply);
    }

    @Test
    void roundTrip_largePayload() throws IOException {
        byte[] large = new byte[64 * 1024];
        for (int i = 0; i < large.length; i++) large[i] = (byte) (i & 0xFF);
        Packet original = new Packet(1, 1, false, large);
        Packet restored = deserialize(serialize(original));
        assertArrayEquals(large, restored.payload);
    }

    @Test
    void rejectsInvalidMagic() {
        byte[] bytes = new byte[Packet.HEADER_SIZE + 1];
        // Put wrong magic
        bytes[0] = (byte) 0xDE; bytes[1] = (byte) 0xAD;
        bytes[2] = (byte) 0xBE; bytes[3] = (byte) 0xEF;
        assertThrows(IOException.class, () -> Packet.read(new ByteArrayInputStream(bytes)));
    }

    @Test
    void rejectsChecksumMismatch() throws IOException {
        byte[] serialized = serialize(new Packet(1, 1, false, "test".getBytes()));
        // Flip a byte in the payload area
        serialized[serialized.length - 2] ^= 0xFF;
        assertThrows(IOException.class, () -> Packet.read(new ByteArrayInputStream(serialized)));
    }

    @Test
    void rejectsInvalidTerminator() throws IOException {
        byte[] serialized = serialize(new Packet(1, 1, false, "x".getBytes()));
        // Corrupt terminator
        serialized[serialized.length - 1] = 0x00;
        assertThrows(IOException.class, () -> Packet.read(new ByteArrayInputStream(serialized)));
    }

    @Test
    void replyBitIsStrippedFromMessageId() throws IOException {
        Packet reply = new Packet(0, 99, true, new byte[0]);
        Packet restored = deserialize(serialize(reply));
        assertEquals(99, restored.messageId);
        assertTrue(restored.isReply);
        // Ensure the R-bit is NOT included in the messageId field
        assertEquals(0, restored.messageId & Packet.R_BIT);
    }
}
