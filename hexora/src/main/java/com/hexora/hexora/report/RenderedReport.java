package com.hexora.hexora.report;

public record RenderedReport(byte[] content, String contentType, String fileName) {
}
