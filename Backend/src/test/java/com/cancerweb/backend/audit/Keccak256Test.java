package com.cancerweb.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class Keccak256Test {

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }

    @Test
    void matchesEthereumKeccakVectors() {
        assertThat(hex(Keccak256.hash(new byte[0])))
                .isEqualTo("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470");
        assertThat(hex(Keccak256.hash("abc".getBytes(StandardCharsets.US_ASCII))))
                .isEqualTo("4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45");
        // Longer than one 136-byte block.
        assertThat(hex(Keccak256.hash("a".repeat(200).getBytes(StandardCharsets.US_ASCII)))).hasSize(64);
    }

    @Test
    void givesTheWellKnownSolidityFunctionSelectors() {
        assertThat(hex(Keccak256.selector("transfer(address,uint256)"))).isEqualTo("a9059cbb");
        assertThat(hex(Keccak256.selector("balanceOf(address)"))).isEqualTo("70a08231");
        assertThat(hex(Keccak256.selector("approve(address,uint256)"))).isEqualTo("095ea7b3");
    }
}
