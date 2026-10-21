package com.oakrtb.server.domain;

/** 竞价成功后的下游入口。日志、采样和投递实现这个接口。 */
public interface AuctionSink {

    void onAuction(InboundAuction auction);
}
