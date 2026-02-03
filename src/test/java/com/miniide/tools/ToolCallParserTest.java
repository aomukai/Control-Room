package com.miniide.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallParserTest {

    private ToolCallParser buildParser() {
        ToolSchemaRegistry registry = new ToolSchemaRegistry()
            .register(new ToolSchema("file_locator")
                .arg("search_criteria", ToolArgSpec.Type.STRING, true)
                .arg("scan_mode", ToolArgSpec.Type.STRING, false)
                .arg("max_results", ToolArgSpec.Type.INT, false)
                .arg("include_globs", ToolArgSpec.Type.BOOLEAN, false)
            );
        return new ToolCallParser(new ObjectMapper(), registry);
    }

    @Test
    void parseValidToolCall() {
        ToolCallParser parser = buildParser();
        String json = "{\"tool\":\"file_locator\",\"args\":{\"search_criteria\":\"Story/SCN-outline.md\",\"scan_mode\":\"FAST_SCAN\",\"max_results\":2,\"include_globs\":false},\"nonce\":\"abc\"}";
        ToolCallParseResult result = parser.parseStrict(json, "abc");
        assertTrue(result.isToolCall());
        assertEquals("file_locator", result.getCall().getName());
        assertEquals("abc", result.getCall().getNonce());
    }

    @Test
    void rejectsUnknownTool() {
        ToolCallParser parser = buildParser();
        String json = "{\"tool\":\"outline_analyzer\",\"args\":{\"outline_path\":\"Story/SCN-outline.md\"},\"nonce\":\"abc\"}";
        ToolCallParseResult result = parser.parseStrict(json, "abc");
        assertFalse(result.isToolCall());
        assertEquals(ToolCallParser.ERR_UNKNOWN_TOOL, result.getErrorCode());
    }

    @Test
    void rejectsInvalidArgsUnknownKey() {
        ToolCallParser parser = buildParser();
        String json = "{\"tool\":\"file_locator\",\"args\":{\"search_criteria\":\"Story/SCN-outline.md\",\"extra\":true},\"nonce\":\"abc\"}";
        ToolCallParseResult result = parser.parseStrict(json, "abc");
        assertFalse(result.isToolCall());
        assertEquals(ToolCallParser.ERR_INVALID_ARGS, result.getErrorCode());
    }

    @Test
    void rejectsUnknownTopLevelField() {
        ToolCallParser parser = buildParser();
        String json = "{\"tool\":\"file_locator\",\"args\":{\"search_criteria\":\"Story/SCN-outline.md\"},\"nonce\":\"abc\",\"extra\":1}";
        ToolCallParseResult result = parser.parseStrict(json, "abc");
        assertFalse(result.isToolCall());
        assertEquals(ToolCallParser.ERR_INVALID_FORMAT, result.getErrorCode());
    }

    @Test
    void rejectsInvalidJson() {
        ToolCallParser parser = buildParser();
        String json = "{\"tool\":\"file_locator\",\"args\":";
        ToolCallParseResult result = parser.parseStrict(json, "abc");
        assertFalse(result.isToolCall());
        assertEquals(ToolCallParser.ERR_INVALID_FORMAT, result.getErrorCode());
    }

    @Test
    void rejectsMultipleObjects() {
        ToolCallParser parser = buildParser();
        String json = "{\"tool\":\"file_locator\",\"args\":{\"search_criteria\":\"x\"},\"nonce\":\"abc\"}{\"tool\":\"file_locator\",\"args\":{\"search_criteria\":\"y\"},\"nonce\":\"abc\"}";
        ToolCallParseResult result = parser.parseStrict(json, "abc");
        assertFalse(result.isToolCall());
        assertEquals(ToolCallParser.ERR_MULTIPLE, result.getErrorCode());
    }

    @Test
    void rejectsNonceMismatch() {
        ToolCallParser parser = buildParser();
        String json = "{\"tool\":\"file_locator\",\"args\":{\"search_criteria\":\"x\"},\"nonce\":\"wrong\"}";
        ToolCallParseResult result = parser.parseStrict(json, "abc");
        assertFalse(result.isToolCall());
        assertEquals(ToolCallParser.ERR_NONCE_INVALID, result.getErrorCode());
    }

    @Test
    void functionLikeSyntaxIsNotParsed() {
        ToolCallParser parser = buildParser();
        String text = "file_locator(search_criteria: \"Story/SCN-outline.md\")";
        ToolCallParseResult result = parser.parseStrict(text, "abc");
        assertFalse(result.isToolCall());
        assertNull(result.getErrorCode());
    }
}
