package com.oakrtb.server.domain;

import com.oakrtb.openrtb.v2.BidRequest;
import com.oakrtb.openrtb.v2.BidResponse;

/** 一次已经解析并给出响应的竞价。编码后的 HTTP 正文不在这里。 */
public final class InboundAuction {

    private final BidRequest request;
    private final BidResponse response;

    public InboundAuction(BidRequest request, BidResponse response) {
        this.request = request;
        this.response = response;
    }

    public BidRequest request() {
        return request;
    }

    public BidResponse response() {
        return response;
    }
}
