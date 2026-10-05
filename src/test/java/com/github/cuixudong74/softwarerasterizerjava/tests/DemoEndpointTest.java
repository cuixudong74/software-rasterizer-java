package com.github.cuixudong74.softwarerasterizerjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoEndpointTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void demoEndpointReturnsDeterministicPng() {
        String url = "http://localhost:" + port + "/render/demo?width=160&height=120";
        byte[] first = rest.getForObject(url, byte[].class);
        byte[] second = rest.getForObject(url, byte[].class);
        assertNotNull(first);
        assertTrue(first.length > 100, "PNG should have content");
        // PNG magic bytes.
        assertEquals((byte) 0x89, first[0]);
        assertEquals((byte) 'P', first[1]);
        assertArrayEquals(first, second, "repeated requests must be byte-identical");
        // Different tiling must still produce the same image.
        byte[] tiled = rest.getForObject(url + "&threads=8&tiles=16", byte[].class);
        assertArrayEquals(first, tiled, "tile/thread count must not change the output");
    }
}
