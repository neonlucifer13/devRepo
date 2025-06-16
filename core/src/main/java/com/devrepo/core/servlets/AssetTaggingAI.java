package com.devrepo.core.servlets;

import com.day.cq.dam.api.Asset;
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
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;

import javax.jcr.Node;
import javax.servlet.Servlet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=GET",
                "sling.servlet.paths=/bin/cohere/tagAsset"
        }
)
public class AssetTaggingAI extends SlingAllMethodsServlet {

    private static final String COHERE_API_KEY = "adlzXjlSaQ4XsUoDDOxE8GVB8toWjk2A6DUmvUgK";
    private static final String COHERE_ENDPOINT = "https://api.cohere.ai/v1/generate";

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String assetPath = request.getParameter("path");
        if (assetPath == null || assetPath.isEmpty()) {
            response.setStatus(400);
            response.getWriter().write("Missing asset path.");
            return;
        }

        ResourceResolver resolver = request.getResourceResolver();
        Resource assetResource = resolver.getResource(assetPath);
        if (assetResource == null || !assetResource.isResourceType("dam:Asset")) {
            response.setStatus(404);
            response.getWriter().write("Asset not found.");
            return;
        }

        Asset asset = assetResource.adaptTo(Asset.class);
        String fileName = asset.getName();

        String prompt = "Generate exactly 5 relevant single-word tags based on the input. Respond only with the 5 nouns, separated by commas\n\"" + fileName + "\"\nTags:";

        // Call Cohere API
        String tagsText = callCohere(prompt);
        if (tagsText == null) {
            response.setStatus(500);
            response.getWriter().write("Failed to call Cohere.");
            return;
        }

        // Clean up the tags
        String[] tags = tagsText.split("[,\\n]+");

        // Save to DAM metadata
        try {
            Node metadataNode = assetResource.getChild("jcr:content/metadata").adaptTo(Node.class);
            if (metadataNode != null) {
                JSONArray tagArray = new JSONArray();
                for (String tag : tags) {
                    tagArray.put(tag.trim());
                }
                metadataNode.setProperty("dc:subject", tagArray.toList().toArray(new String[0]));
                metadataNode.getSession().save();
            }

            response.setStatus(200);
            response.getWriter().write("Tags updated: " + String.join(", ", tags));
        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("JCR Error: " + e.getMessage());
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

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                String responseBody = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(responseBody);
                return json.getJSONArray("generations").getJSONObject(0).getString("text").trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

