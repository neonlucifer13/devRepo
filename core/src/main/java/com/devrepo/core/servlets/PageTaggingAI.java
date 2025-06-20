package com.devrepo.core.servlets;

import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=GET",
                "sling.servlet.paths=/bin/cohere/tagPage"
        }
)
public class PageTaggingAI extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(PageTaggingAI.class);
    private static final String COHERE_API_KEY = "NwC2Crpj0l0qFLKY8skAmThy0WAGboes0yrFSMDp";
    private static final String COHERE_ENDPOINT = "https://api.cohere.ai/v1/generate";
    private static final String TAG_NAMESPACE = "devRepo";

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String pagePath = request.getParameter("page");
        if (pagePath == null || pagePath.isEmpty()) {
            response.setStatus(400);
            response.getWriter().write("Missing page path.");
            return;
        }

        ResourceResolver resolver = request.getResourceResolver();
        Resource pageResource = resolver.getResource(pagePath + "/jcr:content");
        if (pageResource == null) {
            response.setStatus(404);
            response.getWriter().write("Page not found.");
            return;
        }

        StringBuilder contentBuilder = new StringBuilder();
        collectTextContent(pageResource, contentBuilder);
        String textContent = contentBuilder.toString().replaceAll("\\s+", " ").trim();

        if (textContent.isEmpty()) {
            response.setStatus(204);
            response.getWriter().write("No content found on page.");
            return;
        }

        String prompt = "Extract manually typed text content from the following and generate exactly 5 meaningful, unique, single-word nouns suitable as tags. Only return the nouns, comma-separated, without explanation or extra text. Example: Epic, Journey, Stop, Top. Text: " + textContent;
        String tagsText = callCohere(prompt);

        if (tagsText == null || tagsText.isEmpty()) {
            response.setStatus(500);
            response.getWriter().write("Failed to call Cohere.");
            return;
        }

        TagManager tagManager = resolver.adaptTo(TagManager.class);
        if (tagManager == null) {
            response.setStatus(500);
            response.getWriter().write("TagManager not available.");
            return;
        }

        Set<String> tagIds = new HashSet<>();
        Resource existingTagRes = pageResource.getChild("cq:tags");
        Node pageNode = pageResource.adaptTo(Node.class);
        Set<String> existingTags = new HashSet<>();
        try {
            if (pageNode != null && pageNode.hasProperty("cq:tags")) {
                String[] existing = pageNode.getProperty("cq:tags").getString().split(",");
                Collections.addAll(existingTags, existing);
            }
        } catch (RepositoryException e) {
            log.warn("Could not read existing tags: ", e);
        }

        String[] tags = tagsText.split("[,\\n]+");
        for (String rawTag : tags) {
            String tagName = rawTag.trim().replaceAll("[^a-zA-Z0-9]", "");
            if (tagName.isEmpty()) continue;

            String fullTagId = TAG_NAMESPACE + ":" + tagName.toLowerCase();
            if (existingTags.contains(fullTagId)) {
                log.info("Tag already exists on page: {}", fullTagId);
                continue;
            }

            Tag tag = tagManager.resolve(fullTagId);
            if (tag == null) {
                try {
                    tag = tagManager.createTag(fullTagId, tagName, tagName);
                } catch (Exception e) {
                    log.error("Failed to create tag: {}", fullTagId, e);
                    continue;
                }
            }
            tagIds.add(tag.getTagID());
        }

        try {
            if (pageNode != null && !tagIds.isEmpty()) {
                Set<String> allTags = new HashSet<>(existingTags);
                allTags.addAll(tagIds);
                pageNode.setProperty("cq:tags", allTags.toArray(new String[0]));
                pageNode.getSession().save();
                response.setStatus(200);
                response.getWriter().write("Tags added: " + String.join(", ", tagIds));
            } else {
                response.setStatus(204);
                response.getWriter().write("No new tags added.");
            }
        } catch (RepositoryException e) {
            log.error("Failed to save tags to page", e);
            response.setStatus(500);
            response.getWriter().write("JCR Error: " + e.getMessage());
        }
    }

    private void collectTextContent(Resource resource, StringBuilder builder) {
        for (Resource child : resource.getChildren()) {
            ValueMap props = child.getValueMap();
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.contains("text") || key.contains("title") || key.contains("description")) {
                    builder.append(entry.getValue().toString()).append(" ");
                }
            }
            collectTextContent(child, builder);
        }
    }

    private String callCohere(String prompt) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(COHERE_ENDPOINT);
            post.setHeader("Authorization", "Bearer " + COHERE_API_KEY);
            post.setHeader("Content-Type", "application/json");

            JSONObject body = new JSONObject();
            body.put("model", "command");
            body.put("prompt", prompt);
            body.put("max_tokens", 20);
            body.put("temperature", 0.75);

            post.setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8));

            try (CloseableHttpResponse resp = httpClient.execute(post)) {
                String responseBody = IOUtils.toString(resp.getEntity().getContent(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(responseBody);
                return json.getJSONArray("generations").getJSONObject(0).getString("text").trim();
            }
        } catch (Exception e) {
            log.error("Cohere API call failed", e);
            return null;
        }
    }
}
