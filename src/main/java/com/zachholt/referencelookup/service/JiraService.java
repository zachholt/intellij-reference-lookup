package com.zachholt.referencelookup.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.zachholt.referencelookup.settings.ReferenceSettingsState;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JiraService {
    private static final Logger LOG = Logger.getInstance(JiraService.class);
    private static final Pattern TICKET_KEY_PATTERN = Pattern.compile("([A-Z][A-Z0-9]+-\\d+)");

    private final HttpClient httpClient;

    public JiraService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static class JiraTicket {
        public final String key;
        public final String summary;
        public final String description;
        public final String status;
        public final String assignee;
        public final String reporter;
        public final String issueType;
        public final String url;

        public JiraTicket(String key, String summary, String description, String status,
                         String assignee, String reporter, String issueType, String url) {
            this.key = key;
            this.summary = summary;
            this.description = description;
            this.status = status;
            this.assignee = assignee;
            this.reporter = reporter;
            this.issueType = issueType;
            this.url = url;
        }
    }

    public String extractTicketKey(String urlOrKey) {
        Matcher matcher = TICKET_KEY_PATTERN.matcher(urlOrKey);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public JiraTicket fetchTicket(String urlOrKey) throws JiraException {
        ReferenceSettingsState settings = ReferenceSettingsState.getInstance();

        if (settings.jiraBaseUrl.isEmpty() || settings.jiraEmail.isEmpty() || settings.jiraApiToken.isEmpty()) {
            throw new JiraException("Jira credentials not configured. Please configure in Settings → Tools → Reference Lookup");
        }

        String ticketKey = extractTicketKey(urlOrKey);
        if (ticketKey == null) {
            throw new JiraException("Could not extract ticket key from: " + urlOrKey);
        }

        String apiUrl = settings.jiraBaseUrl + "/rest/api/3/issue/" + ticketKey;
        String auth = Base64.getEncoder().encodeToString(
                (settings.jiraEmail + ":" + settings.jiraApiToken).getBytes()
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Basic " + auth)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                throw new JiraException("Authentication failed. Check your email and API token.");
            } else if (response.statusCode() == 404) {
                throw new JiraException("Ticket not found: " + ticketKey);
            } else if (response.statusCode() != 200) {
                throw new JiraException("Jira API error (HTTP " + response.statusCode() + "): " + response.body());
            }

            return parseTicketResponse(response.body(), settings.jiraBaseUrl, ticketKey);

        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to fetch Jira ticket", e);
            throw new JiraException("Failed to connect to Jira: " + e.getMessage());
        }
    }

    private JiraTicket parseTicketResponse(String json, String baseUrl, String ticketKey) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject fields = root.getAsJsonObject("fields");

        String key = root.get("key").getAsString();
        String summary = getStringField(fields, "summary");
        String description = extractDescription(fields);

        String status = "";
        if (fields.has("status") && !fields.get("status").isJsonNull()) {
            status = fields.getAsJsonObject("status").get("name").getAsString();
        }

        String assignee = "";
        if (fields.has("assignee") && !fields.get("assignee").isJsonNull()) {
            assignee = fields.getAsJsonObject("assignee").get("displayName").getAsString();
        }

        String reporter = "";
        if (fields.has("reporter") && !fields.get("reporter").isJsonNull()) {
            reporter = fields.getAsJsonObject("reporter").get("displayName").getAsString();
        }

        String issueType = "";
        if (fields.has("issuetype") && !fields.get("issuetype").isJsonNull()) {
            issueType = fields.getAsJsonObject("issuetype").get("name").getAsString();
        }

        String url = baseUrl + "/browse/" + key;

        return new JiraTicket(key, summary, description, status, assignee, reporter, issueType, url);
    }

    private String extractDescription(JsonObject fields) {
        if (!fields.has("description") || fields.get("description").isJsonNull()) {
            return "";
        }

        // Jira Cloud uses Atlassian Document Format (ADF) for description
        // We'll extract plain text from it
        try {
            JsonObject description = fields.getAsJsonObject("description");
            StringBuilder sb = new StringBuilder();
            extractTextFromAdf(description, sb);
            return sb.toString().trim();
        } catch (Exception e) {
            // Fallback for simple string description (Jira Server)
            try {
                return fields.get("description").getAsString();
            } catch (Exception e2) {
                return "";
            }
        }
    }

    private void extractTextFromAdf(JsonObject node, StringBuilder sb) {
        if (node.has("text")) {
            sb.append(node.get("text").getAsString());
        }
        if (node.has("content")) {
            for (var element : node.getAsJsonArray("content")) {
                extractTextFromAdf(element.getAsJsonObject(), sb);
                if (element.getAsJsonObject().has("type") &&
                    element.getAsJsonObject().get("type").getAsString().equals("paragraph")) {
                    sb.append("\n");
                }
            }
        }
    }

    private String getStringField(JsonObject fields, String fieldName) {
        if (fields.has(fieldName) && !fields.get(fieldName).isJsonNull()) {
            return fields.get(fieldName).getAsString();
        }
        return "";
    }

    public static class JiraException extends Exception {
        public JiraException(String message) {
            super(message);
        }
    }
}
