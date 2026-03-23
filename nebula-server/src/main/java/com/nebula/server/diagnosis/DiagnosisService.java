package com.nebula.server.diagnosis;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DiagnosisService - Handles AI diagnosis API calls with retry mechanism
 * Communicates with Azure OpenAI Models API (GitHub Models)
 */
public class DiagnosisService {
    private static final Logger logger = Logger.getLogger(DiagnosisService.class.getName());
    private static final DiagnosisConfig config = DiagnosisConfig.getInstance();
    private static final String CONTENT_TYPE = "application/json";

    /**
     * Send diagnosis request to AI service with retry logic
     * @param prompt Diagnosis prompt
     * @return AI diagnosis result or null if all retries fail
     */
    public static String diagnoseWithAI(String prompt) {
        if (!AIPromptBuilder.validatePrompt(prompt)) {
            logger.warning("Invalid prompt, skipping AI diagnosis");
            return null;
        }

        int maxRetries = config.getAiRetryCount();
        long retryInterval = config.getAiRetryInterval();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String result = callAIAPI(prompt);
                if (result != null) {
                    logger.info("AI diagnosis successful on attempt " + attempt);
                    return result;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING,
                        String.format("AI diagnosis attempt %d/%d failed", attempt, maxRetries), e);

                // Don't retry if it's the last attempt
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryInterval * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.log(Level.WARNING, "Interrupted during retry sleep", ie);
                        break;
                    }
                }
            }
        }

        logger.severe("AI diagnosis failed after " + maxRetries + " retries");
        return null;
    }

    /**
     * Call Azure OpenAI API directly using HttpURLConnection
     * @param prompt User prompt for diagnosis
     * @return AI response text or null if request fails
     */
    private static String callAIAPI(String prompt) throws Exception {
        String baseUrl = config.getAiBaseUrl();
        String apiKey = config.getAiApiKey();
        String model = config.getAiModel();
        long timeout = config.getAiCallTimeout();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("AI API key not configured");
        }

        // Build request URL
        String url = baseUrl + "/chat/completions";

        // Build request body
        String requestBody = buildRequestBody(prompt, model);

        // Create connection
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", CONTENT_TYPE);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setConnectTimeout((int) timeout);
        connection.setReadTimeout((int) timeout);
        connection.setDoOutput(true);

        // Send request
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Read response
        int responseCode = connection.getResponseCode();
        logger.fine("AI API response code: " + responseCode);

        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                String response = br.readLine();
                return parseAIResponse(response);
            }
        } else {
            // Read error response
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    errorResponse.append(line);
                }
                logger.warning("AI API error response: " + errorResponse);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to read error response", e);
            }
            throw new IOException("AI API request failed with status " + responseCode);
        }
    }

    /**
     * Build JSON request body for the API
     */
    private static String buildRequestBody(String prompt, String model) {
        // Simple JSON construction without external library
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(model).append("\",");
        json.append("\"messages\":[");
        json.append("{\"role\":\"system\",\"content\":\"You are a performance diagnostician.\"},");
        json.append("{\"role\":\"user\",\"content\":\"").append(escapeJson(prompt)).append("\"}");
        json.append("],");
        json.append("\"temperature\":0.7,");
        json.append("\"max_tokens\":1000");
        json.append("}");
        return json.toString();
    }

    /**
     * Parse AI response JSON to extract the diagnosis text
     */
    private static String parseAIResponse(String response) {
        if (response == null) {
            return null;
        }

        // Simple JSON parsing - extract content from response
        // Expected format: {"choices":[{"message":{"content":"..."}}]}
        try {
            int contentStart = response.indexOf("\"content\":");
            if (contentStart == -1) {
                logger.warning("Content field not found in AI response");
                return null;
            }

            int textStart = response.indexOf("\"", contentStart + 10);
            if (textStart == -1) {
                return null;
            }

            int textEnd = response.indexOf("\"", textStart + 1);
            if (textEnd == -1) {
                return null;
            }

            String content = response.substring(textStart + 1, textEnd);
            // Unescape JSON escaped strings
            content = unescapeJson(content);

            if (content.isEmpty()) {
                logger.warning("AI response content is empty");
                return null;
            }

            logger.fine("Parsed AI response: " + content.substring(0, Math.min(100, content.length())));
            return content;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to parse AI response", e);
            return null;
        }
    }

    /**
     * Escape string for JSON serialization
     */
    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Unescape JSON escaped strings
     */
    private static String unescapeJson(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                switch (next) {
                    case '\"':
                        sb.append('\"');
                        i++;
                        break;
                    case '\\':
                        sb.append('\\');
                        i++;
                        break;
                    case 'n':
                        sb.append('\n');
                        i++;
                        break;
                    case 'r':
                        sb.append('\r');
                        i++;
                        break;
                    case 't':
                        sb.append('\t');
                        i++;
                        break;
                    default:
                        sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Validate AI configuration before attempting diagnosis
     */
    public static boolean validateAIConfig() {
        return SlowTraceDetector.isAiConfigValid();
    }
}
