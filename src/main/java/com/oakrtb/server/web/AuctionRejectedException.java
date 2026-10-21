package com.oakrtb.server.web;

import com.oakrtb.sdk.jsonschema.Report;

/** 请求体无法解析，或未通过 OakRTB 校验。 */
final class AuctionRejectedException extends RuntimeException {

    private final Report report;

    AuctionRejectedException(Report report) {
        super(report.toJson());
        this.report = report;
    }

    Report report() {
        return report;
    }
}
