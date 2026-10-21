package com.oakrtb.server;

import com.oakrtb.openrtb.v2.BidRequest;
import com.oakrtb.openrtb.v2.BidResponse;
import com.oakrtb.sdk.builder.BidRequestBuilder;
import com.oakrtb.sdk.builder.ImpBuilders;
import com.oakrtb.sdk.builder.Parts;
import com.oakrtb.sdk.codec.Json;
import com.oakrtb.server.domain.AuctionSink;
import com.oakrtb.server.domain.InboundAuction;
import com.oakrtb.server.web.AuctionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuctionControllerTest.SinkConfig.class)
class AuctionControllerTest {

    private static final String AUCTION = AuctionController.AUCTION_PATH;
    private static final MediaType PROTOBUF = MediaType.parseMediaType("application/x-protobuf");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RecordingSink seen;

    @BeforeEach
    void reset() {
        seen.auctions().clear();
    }

    @Test
    void protobufPostParsesBidRequestAndReturnsNoBid() throws Exception {
        MvcResult result = mvc.perform(post(AUCTION)
                        .contentType(PROTOBUF)
                        .header(AuctionController.VERSION_HEADER, AuctionController.OPENRTB_VERSION)
                        .content(bannerRequest().toByteArray()))
                .andExpect(status().isOk())
                .andExpect(header().string(AuctionController.VERSION_HEADER, AuctionController.OPENRTB_VERSION))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/x-protobuf")))
                .andReturn();

        BidResponse response = BidResponse.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals("auction-1", response.getId());
        assertEquals("USD", response.getCur());
        assertTrue(response.hasNbr());
        assertEquals(0, response.getNbr());
        assertEquals(0, response.getSeatbidCount());
        assertEquals(1, seen.auctions().size());
        assertEquals("auction-1", seen.auctions().get(0).request().getId());
    }

    @Test
    void jsonPostUsesTheSameAuction() throws Exception {
        byte[] json = Json.toJsonBytes(bannerRequest());

        MvcResult result = mvc.perform(post(AUCTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
                .andReturn();

        BidResponse response = Json.parseBidResponse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("auction-1", response.getId());
        assertEquals(0, response.getNbr());
    }

    @Test
    void missingContentTypeIsJson() throws Exception {
        mvc.perform(post(AUCTION).content(Json.toJsonBytes(bannerRequest())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")));
    }

    @Test
    void gzipProtobufRoundTrip() throws Exception {
        MvcResult result = mvc.perform(post(AUCTION)
                        .contentType(PROTOBUF)
                        .header("Content-Encoding", "gzip")
                        .header("Accept-Encoding", "gzip, br;q=0.8")
                        .content(gzip(bannerRequest().toByteArray())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Encoding", "gzip"))
                .andReturn();

        BidResponse response = BidResponse.parseFrom(gunzip(result.getResponse().getContentAsByteArray()));
        assertEquals("auction-1", response.getId());
        assertEquals(0, response.getNbr());
    }

    @Test
    void uncompressedWhenAcceptEncodingOmitsGzip() throws Exception {
        mvc.perform(post(AUCTION)
                        .contentType(PROTOBUF)
                        .content(bannerRequest().toByteArray()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Encoding"));
    }

    @Test
    void invalidProtobufIs400() throws Exception {
        mvc.perform(post(AUCTION)
                        .contentType(PROTOBUF)
                        .content(new byte[] {0x0A}))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("parse"));
        assertEquals(0, seen.auctions().size());
    }

    @Test
    void protobufThatFailsValidationIs400() throws Exception {
        byte[] body = BidRequest.newBuilder().setId("auction-1").build().toByteArray();

        mvc.perform(post(AUCTION).contentType(PROTOBUF).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("constraint"));
    }

    @Test
    void invalidJsonIs400() throws Exception {
        mvc.perform(post(AUCTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errors[?(@.code == 'required')]").exists());
    }

    private static BidRequest bannerRequest() {
        return BidRequestBuilder.create("auction-1").firstPrice().currency("USD").test()
                .site(Parts.site().id("site-1").domain("publisher.example").build())
                .addImp(ImpBuilders.banner("imp-1").size(300, 250).floor(1, "USD").secure().build())
                .build();
    }

    private static byte[] gzip(byte[] body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(body);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] body) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return gzip.readAllBytes();
        }
    }

    @TestConfiguration
    static class SinkConfig {
        @Bean
        RecordingSink recordingSink() {
            return new RecordingSink();
        }
    }

    static final class RecordingSink implements AuctionSink {
        private final List<InboundAuction> auctions = new ArrayList<>();

        @Override
        public void onAuction(InboundAuction auction) {
            auctions.add(auction);
        }

        List<InboundAuction> auctions() {
            return auctions;
        }
    }
}
