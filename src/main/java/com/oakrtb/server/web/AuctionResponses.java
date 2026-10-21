package com.oakrtb.server.web;

import com.oakrtb.openrtb.v2.BidResponse;
import com.oakrtb.sdk.jsonschema.Report;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class AuctionResponses {

    private static final MediaType PROTOBUF = MediaType.parseMediaType("application/x-protobuf");

    private AuctionResponses() {
    }

    static ResponseEntity<byte[]> protobuf(BidResponse response) {
        return ok(PROTOBUF, response.toByteArray());
    }

    static ResponseEntity<byte[]> json(byte[] payload) {
        return ok(MediaType.APPLICATION_JSON, payload);
    }

    static ResponseEntity<byte[]> report(Report report) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AuctionController.VERSION_HEADER, AuctionController.OPENRTB_VERSION)
                .body(report.toJsonBytes());
    }

    static ResponseEntity<byte[]> reason(HttpStatus status, String reason) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(("{\"reason\":\"" + reason + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static ResponseEntity<byte[]> ok(MediaType mediaType, byte[] body) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(AuctionController.VERSION_HEADER, AuctionController.OPENRTB_VERSION)
                .body(body);
    }
}
