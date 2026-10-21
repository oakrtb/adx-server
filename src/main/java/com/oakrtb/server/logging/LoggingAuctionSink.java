package com.oakrtb.server.logging;

import com.oakrtb.server.domain.AuctionSink;
import com.oakrtb.server.domain.InboundAuction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 把竞价结果交给日志框架。异步落盘由 logback 的 AsyncAppender 完成。 */
@Slf4j
@Component
public class LoggingAuctionSink implements AuctionSink {

    @Override
    public void onAuction(InboundAuction auction) {
        log.info("auction id={} imps={} nbr={}",
                auction.request().getId(),
                auction.request().getImpCount(),
                auction.response().hasNbr() ? auction.response().getNbr() : null);
    }
}
