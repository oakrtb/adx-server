package com.oakrtb.server.web;

import com.google.protobuf.InvalidProtocolBufferException;
import com.oakrtb.openrtb.v2.BidRequest;
import com.oakrtb.openrtb.v2.BidResponse;
import com.oakrtb.openrtb.v2.NoBidReason;
import com.oakrtb.sdk.builder.BidResponseBuilder;
import com.oakrtb.sdk.codec.Json;
import com.oakrtb.sdk.jsonschema.Issue;
import com.oakrtb.sdk.jsonschema.Report;
import com.oakrtb.sdk.jsonschema.Schema;
import com.oakrtb.sdk.view.RequestView;
import com.oakrtb.server.domain.AuctionSink;
import com.oakrtb.server.domain.InboundAuction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * {@code POST /openrtb/v2/auction}。protobuf 由 Spring 转成 {@link BidRequest}，JSON 交给 OakRTB codec。
 * 决策留在 oakrtb-sdk，成功后交给 {@link AuctionSink}。
 */
@RestController
public class AuctionController {

    public static final String AUCTION_PATH = "/openrtb/v2/auction";
    public static final String OPENRTB_VERSION = "2.6";
    public static final String VERSION_HEADER = "x-openrtb-version";

    private static final String ERROR = "ERROR";

    private final ObjectProvider<AuctionSink> sinks;

    public AuctionController(ObjectProvider<AuctionSink> sinks) {
        this.sinks = sinks;
    }

    @PostMapping(path = AUCTION_PATH, consumes = "application/x-protobuf")
    public ResponseEntity<byte[]> protobuf(@RequestBody BidRequest request) {
        try {
            BidResponse response = decide(request);
            publish(request, response);
            return AuctionResponses.protobuf(response);
        } catch (AuctionRejectedException e) {
            return AuctionResponses.report(e.report());
        } catch (RuntimeException e) {
            return AuctionResponses.reason(HttpStatus.INTERNAL_SERVER_ERROR, ERROR);
        }
    }

    @PostMapping(path = AUCTION_PATH, consumes = "application/json")
    public ResponseEntity<byte[]> json(@RequestBody byte[] body) {
        try {
            Report report = Schema.request(body);
            if (!report.isOk()) {
                throw new AuctionRejectedException(report);
            }
            BidRequest request = Json.parseBidRequest(new String(body, StandardCharsets.UTF_8));
            BidResponse response = decide(request);
            byte[] payload = Json.toJsonBytes(response);
            Report output = Schema.response(payload);
            if (!output.isOk()) {
                throw new IllegalStateException(output.toJson());
            }
            publish(request, response);
            return AuctionResponses.json(payload);
        } catch (AuctionRejectedException e) {
            return AuctionResponses.report(e.report());
        } catch (InvalidProtocolBufferException e) {
            String message = e.getMessage() == null ? "invalid json" : e.getMessage();
            return AuctionResponses.report(Report.fail(new Issue("parse", "", message)));
        } catch (RuntimeException e) {
            return AuctionResponses.reason(HttpStatus.INTERNAL_SERVER_ERROR, ERROR);
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<byte[]> unreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause.getMessage() == null ? "unreadable body" : cause.getMessage();
        return AuctionResponses.report(Report.fail(new Issue("parse", "", message)));
    }

    private void publish(BidRequest request, BidResponse response) {
        InboundAuction inbound = new InboundAuction(request, response);
        sinks.stream().forEach(sink -> sink.onAuction(inbound));
    }

    private static BidResponse decide(BidRequest request) {
        final RequestView view;
        try {
            view = RequestView.of(request);
        } catch (IllegalArgumentException e) {
            throw new AuctionRejectedException(Report.fail(new Issue("constraint", "", e.getMessage())));
        }
        String currency = view.currencies().get(0);
        return BidResponseBuilder.create(view.auctionId())
                .currency(currency)
                .noBid(NoBidReason.NO_BID_REASON_UNKNOWN_ERROR.getNumber())
                .build();
    }
}
