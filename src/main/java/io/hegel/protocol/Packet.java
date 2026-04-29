package io.hegel.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * A single protocol packet.
 *
 * Wire format (20-byte header + variable payload + 1-byte terminator):
 * <pre>
 *  0               1               2               3
 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Magic (0x4845474C)                       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Checksum (CRC32)                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Stream id                              |S|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |R|                    Message id                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Payload length                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Payload (variable length)                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Term. (0x0A) |
 * +-+-+-+-+-+-+-+-+
 * </pre>
 */
public final class Packet {
    static final int MAGIC = 0x4845474C;
    static final int HEADER_SIZE = 20;
    static final byte TERMINATOR = 0x0A;

    // S-bit: bit 0 of the stream ID word. 1 = client-created stream.
    static final int S_BIT = 1;
    // R-bit: bit 31 of the message ID word. 1 = reply packet.
    static final int R_BIT = 0x80000000;

    /** Full 32-bit stream ID word (includes S-bit). */
    public final int streamId;
    /** 31-bit message ID (R-bit stripped). */
    public final int messageId;
    /** True if this is a reply to a previous request. */
    public final boolean isReply;
    public final byte[] payload;

    public Packet(int streamId, int messageId, boolean isReply, byte[] payload) {
        this.streamId = streamId;
        this.messageId = messageId;
        this.isReply = isReply;
        this.payload = payload;
    }

    // ── Write ──────────────────────────────────────────────────────────────────

    /**
     * Serialize and write {@code packet} to {@code out}.
     * Computes and embeds the CRC32 checksum before writing.
     *
     * @throws IOException if the underlying stream throws
     */
    public static void write(OutputStream out, Packet packet) throws IOException {
        int messageIdWord = packet.isReply
                ? (packet.messageId | R_BIT)
                : packet.messageId;

        byte[] header = new byte[HEADER_SIZE];
        // Magic at 0-3 (leave checksum at 4-7 as zeros for now)
        putInt(header, 0, MAGIC);
        // Checksum placeholder at 4-7 left as zero
        putInt(header, 8, packet.streamId);
        putInt(header, 12, messageIdWord);
        putInt(header, 16, packet.payload.length);

        // CRC32 over header (checksum zeroed) + payload
        CRC32 crc = new CRC32();
        crc.update(header);
        crc.update(packet.payload);
        int checksum = (int) crc.getValue();
        putInt(header, 4, checksum);

        out.write(header);
        out.write(packet.payload);
        out.write(TERMINATOR);
        out.flush();
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    /**
     * Read and deserialize one packet from {@code in}.
     * Validates the magic number, CRC32 checksum, and terminator byte.
     *
     * @throws IOException if the stream ends prematurely, the magic number is
     *                     invalid, the checksum does not match, or the terminator
     *                     byte is missing
     */
    public static Packet read(InputStream in) throws IOException {
        byte[] header = readFully(in, HEADER_SIZE);

        int magic = getInt(header, 0);
        if (magic != MAGIC) {
            throw new IOException(String.format(
                    "Invalid magic: expected 0x%08X, got 0x%08X", MAGIC, magic));
        }
        int expectedChecksum = getInt(header, 4);
        int streamId = getInt(header, 8);
        int messageIdWord = getInt(header, 12);
        int payloadLength = getInt(header, 16);

        byte[] payload = readFully(in, payloadLength);

        int terminator = in.read();
        if (terminator == -1) throw new IOException("Unexpected end of stream (terminator)");
        if ((byte) terminator != TERMINATOR) {
            throw new IOException(String.format(
                    "Invalid terminator: expected 0x%02X, got 0x%02X", TERMINATOR, terminator));
        }

        // Verify CRC32
        byte[] headerForCheck = header.clone();
        putInt(headerForCheck, 4, 0); // zero checksum field
        CRC32 crc = new CRC32();
        crc.update(headerForCheck);
        crc.update(payload);
        int computed = (int) crc.getValue();
        if (computed != expectedChecksum) {
            throw new IOException(String.format(
                    "Checksum mismatch: expected 0x%08X, got 0x%08X", expectedChecksum, computed));
        }

        boolean isReply = (messageIdWord & R_BIT) != 0;
        int messageId = messageIdWord & ~R_BIT;

        return new Packet(streamId, messageId, isReply, payload);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void putInt(byte[] buf, int offset, int value) {
        buf[offset]     = (byte) (value >>> 24);
        buf[offset + 1] = (byte) (value >>> 16);
        buf[offset + 2] = (byte) (value >>>  8);
        buf[offset + 3] = (byte)  value;
    }

    private static int getInt(byte[] buf, int offset) {
        return ((buf[offset]     & 0xFF) << 24)
             | ((buf[offset + 1] & 0xFF) << 16)
             | ((buf[offset + 2] & 0xFF) <<  8)
             |  (buf[offset + 3] & 0xFF);
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int remaining = length;
        int offset = 0;
        while (remaining > 0) {
            int read = in.read(buf, offset, remaining);
            if (read == -1) throw new IOException("Unexpected end of stream (readFully)");
            offset += read;
            remaining -= read;
        }
        return buf;
    }
}
