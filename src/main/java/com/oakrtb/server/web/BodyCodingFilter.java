package com.oakrtb.server.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 竞价地址上的 body 编码：请求 {@code Content-Encoding: gzip} 先解压再交给解析器；
 * 缺 {@code Content-Type} 时按 JSON 处理；响应仅在 {@code Accept-Encoding} 接受 gzip 时压缩。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BodyCodingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!auction(request)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest inbound = request;
        if (gzipEncoded(request.getHeader("Content-Encoding"))) {
            inbound = new GzipRequest(inbound);
        }
        if (inbound.getContentType() == null || inbound.getContentType().isBlank()) {
            inbound = new JsonContentTypeRequest(inbound);
        }
        response.setHeader("Vary", "Accept-Encoding");
        if (!acceptsGzip(request.getHeader("Accept-Encoding"))) {
            chain.doFilter(inbound, response);
            return;
        }
        GzipResponseWrapper wrapped = new GzipResponseWrapper(response);
        chain.doFilter(inbound, wrapped);
        wrapped.finish();
    }

    private static boolean auction(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "POST".equalsIgnoreCase(request.getMethod())
                && uri != null
                && uri.endsWith(AuctionController.AUCTION_PATH);
    }

    static boolean gzipEncoded(String header) {
        return header != null && header.trim().equalsIgnoreCase("gzip");
    }

    static boolean acceptsGzip(String header) {
        if (header == null || header.isBlank()) {
            return false;
        }
        for (String part : header.split(",")) {
            String[] bits = part.trim().split(";");
            if (bits.length == 0 || bits[0].isBlank()) {
                continue;
            }
            String token = bits[0].trim();
            if (!token.equalsIgnoreCase("gzip") && !token.equals("*")) {
                continue;
            }
            double quality = 1;
            for (int i = 1; i < bits.length; i++) {
                String param = bits[i].trim();
                if (param.regionMatches(true, 0, "q=", 0, 2)) {
                    try {
                        quality = Double.parseDouble(param.substring(2).trim());
                    } catch (NumberFormatException e) {
                        quality = 0;
                    }
                }
            }
            if (quality > 0) {
                return true;
            }
        }
        return false;
    }

    private static final class GzipRequest extends HttpServletRequestWrapper {

        private ServletInputStream stream;

        private GzipRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new GzipServletInputStream(super.getInputStream());
            }
            return stream;
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public String getHeader(String name) {
            if (skip(name)) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (skip(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        private static boolean skip(String name) {
            return "Content-Encoding".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name);
        }
    }

    private static final class GzipServletInputStream extends ServletInputStream {

        private final GZIPInputStream gzip;

        private GzipServletInputStream(ServletInputStream in) throws IOException {
            this.gzip = new GZIPInputStream(in);
        }

        @Override
        public int read() throws IOException {
            return gzip.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return gzip.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class JsonContentTypeRequest extends HttpServletRequestWrapper {

        private JsonContentTypeRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public String getHeader(String name) {
            if ("Content-Type".equalsIgnoreCase(name)) {
                return "application/json";
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Content-Type".equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of("application/json"));
            }
            return super.getHeaders(name);
        }
    }

    private static final class GzipResponseWrapper extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final ServletOutputStream stream = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void write(int b) {
                buffer.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                buffer.write(b, off, len);
            }
        };
        private PrintWriter writer;

        private GzipResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return stream;
        }

        @Override
        public PrintWriter getWriter() {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
            }
            return writer;
        }

        @Override
        public void setContentLength(int len) {
        }

        @Override
        public void setContentLengthLong(long len) {
        }

        @Override
        public void setHeader(String name, String value) {
            if (!"Content-Length".equalsIgnoreCase(name)) {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (!"Content-Length".equalsIgnoreCase(name)) {
                super.addHeader(name, value);
            }
        }

        @Override
        public void setIntHeader(String name, int value) {
            if (!"Content-Length".equalsIgnoreCase(name)) {
                super.setIntHeader(name, value);
            }
        }

        @Override
        public void flushBuffer() {
        }

        private void finish() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            HttpServletResponse raw = (HttpServletResponse) getResponse();
            byte[] body = buffer.toByteArray();
            if (body.length == 0 || raw.getStatus() == HttpServletResponse.SC_NO_CONTENT) {
                raw.setContentLength(0);
                return;
            }
            ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(gzipped)) {
                gzip.write(body);
            }
            byte[] out = gzipped.toByteArray();
            raw.setHeader("Content-Encoding", "gzip");
            raw.setContentLength(out.length);
            raw.getOutputStream().write(out);
        }
    }
}
