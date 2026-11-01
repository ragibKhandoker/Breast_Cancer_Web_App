package com.cancerweb.backend.audit;

import java.nio.charset.StandardCharsets;

/**
 * Keccak-256 as Ethereum uses it (the original Keccak padding, not NIST SHA3-256), needed for Solidity function
 * selectors. Small and self-contained so the backend needs no web3 library; checked against known vectors in
 * Keccak256Test.
 */
final class Keccak256 {

    private static final long[] RC = {
        0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L,
        0x000000000000808BL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
        0x000000000000008AL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
        0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L, 0x8000000000008003L,
        0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
        0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L,
    };
    private static final int[] ROTATION = {
        0, 1, 62, 28, 27, 36, 44, 6, 55, 20, 3, 10, 43, 25, 39, 41, 45, 15, 21, 8, 18, 2, 61, 56, 14,
    };
    private static final int RATE = 136;

    private Keccak256() {
    }

    static byte[] hash(byte[] input) {
        long[] state = new long[25];
        int blocks = input.length / RATE + 1;
        byte[] padded = new byte[blocks * RATE];
        System.arraycopy(input, 0, padded, 0, input.length);
        padded[input.length] ^= 0x01;
        padded[padded.length - 1] ^= (byte) 0x80;
        for (int b = 0; b < blocks; b++) {
            for (int i = 0; i < RATE / 8; i++) {
                state[i] ^= littleEndian(padded, b * RATE + i * 8);
            }
            permute(state);
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 8; j++) {
                out[i * 8 + j] = (byte) (state[i] >>> (8 * j));
            }
        }
        return out;
    }

    /** The 4-byte selector of a Solidity function signature such as {@code register(bytes32,bytes32,uint64)}. */
    static byte[] selector(String signature) {
        byte[] h = hash(signature.getBytes(StandardCharsets.US_ASCII));
        return new byte[] { h[0], h[1], h[2], h[3] };
    }

    private static long littleEndian(byte[] b, int off) {
        long v = 0;
        for (int i = 7; i >= 0; i--) {
            v = (v << 8) | (b[off + i] & 0xFF);
        }
        return v;
    }

    private static void permute(long[] a) {
        long[] c = new long[5];
        long[] b = new long[25];
        for (int round = 0; round < 24; round++) {
            for (int x = 0; x < 5; x++) {
                c[x] = a[x] ^ a[x + 5] ^ a[x + 10] ^ a[x + 15] ^ a[x + 20];
            }
            for (int x = 0; x < 5; x++) {
                long d = c[(x + 4) % 5] ^ Long.rotateLeft(c[(x + 1) % 5], 1);
                for (int y = 0; y < 25; y += 5) {
                    a[y + x] ^= d;
                }
            }
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    int i = x + 5 * y;
                    b[y + 5 * ((2 * x + 3 * y) % 5)] = Long.rotateLeft(a[i], ROTATION[i]);
                }
            }
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 25; y += 5) {
                    a[y + x] = b[y + x] ^ (~b[y + (x + 1) % 5] & b[y + (x + 2) % 5]);
                }
            }
            a[0] ^= RC[round];
        }
    }
}
