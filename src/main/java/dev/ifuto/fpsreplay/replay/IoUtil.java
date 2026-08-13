package dev.ifuto.fpsreplay.replay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Low-level helpers for the compact binary replay format.
 *
 * <p>Everything here is deliberately tiny and allocation-light so that the
 * hot path (recording every tick) stays as cheap as possible — the whole point
 * of the "record packets, not pixels" design.</p>
 */
public final class IoUtil {
    private IoUtil() {
    }

    /** Write a non-negative integer using Minecraft-style variable-length encoding. */
    public static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    /** Read a non-negative integer written by {@link #writeVarInt}. */
    public static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        int shift = 0;
        int b;
        do {
            b = in.readUnsignedByte();
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }

    /** Write a (possibly negative) integer using zigzag varint encoding. */
    public static void writeVarIntZigZag(DataOutputStream out, int value) throws IOException {
        writeVarInt(out, (value << 1) ^ (value >> 31));
    }

    /** Read an integer written by {@link #writeVarIntZigZag}. */
    public static int readVarIntZigZag(DataInputStream in) throws IOException {
        int raw = readVarInt(in);
        return (raw >>> 1) ^ -(raw & 1);
    }

    /** Write a UTF-8 string, length-prefixed. */
    public static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    /** Read a UTF-8 string written by {@link #writeString}. */
    public static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
