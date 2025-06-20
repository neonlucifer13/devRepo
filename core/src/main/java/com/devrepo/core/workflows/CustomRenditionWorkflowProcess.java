package com.devrepo.core.workflows;

import com.devrepo.core.service.AssetRenditionService;
import com.day.cq.dam.api.Asset;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.jcr.Session;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(service = WorkflowProcess.class, property = {
        "process.label=Generate Custom Image Renditions (No Service User)"
})
public class CustomRenditionWorkflowProcess implements WorkflowProcess {

    private static final Logger logger = LoggerFactory.getLogger(CustomRenditionWorkflowProcess.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private AssetRenditionService renditionService;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap)
            throws WorkflowException {

        String payloadPath = workItem.getWorkflowData().getPayload().toString();
        logger.info("Workflow triggered. Payload path: {}", payloadPath);

        try {
            Session jcrSession = workflowSession.getSession();
            logger.info("Workflow session user: {}", jcrSession.getUserID());

            Map<String, Object> sessionInfo = new HashMap<>();
            sessionInfo.put(ResourceResolverFactory.USER, jcrSession.getUserID());
            sessionInfo.put("user.jcr.session", jcrSession);

            ResourceResolver resolver = resourceResolverFactory.getResourceResolver(sessionInfo);
            logger.info("Resource resolver obtained for session user.");

            Resource assetResource = resolver.getResource(payloadPath);
            if (assetResource == null) {
                logger.error("Asset not found at path: {}", payloadPath);
                throw new WorkflowException("Asset not found: " + payloadPath);
            }
            logger.info("Asset resource found at: {}", payloadPath);

            Asset asset = assetResource.adaptTo(Asset.class);
            if (asset == null) {
                logger.error("Asset adaptation failed for resource: {}", payloadPath);
                throw new WorkflowException("Invalid DAM asset: " + payloadPath);
            }
            logger.info("Asset adapted successfully.");

            InputStream originalStream = asset.getOriginal().getStream();
            BufferedImage originalImage = ImageIO.read(originalStream);
            if (originalImage == null) {
                logger.error("ImageIO failed to read the original image.");
                throw new WorkflowException("Cannot read original image.");
            }
            logger.info("Original image read successfully.");

            List<int[]> sizes = renditionService.getParsedSizes();
            logger.info("Parsed rendition sizes: {}", sizes.size());

            for (int[] size : sizes) {
                int width = size[0];
                int height = size[1];
                logger.info("Processing rendition size: {}x{}", width, height);

                BufferedImage resized = resizeImage(originalImage, width, height);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(resized, "jpeg", baos);
                InputStream renditionStream = new ByteArrayInputStream(baos.toByteArray());

                String renditionName = "custom_" + width + "x" + height + ".jpg";
                asset.addRendition(renditionName, renditionStream, "image/jpeg");
                logger.info("Added rendition: {}", renditionName);
            }

            resolver.adaptTo(Session.class).save();
            logger.info("Session changes saved successfully.");

        } catch (Exception e) {
            logger.error("Exception in rendition generation: {}", e.getMessage(), e);
            throw new WorkflowException("Rendition generation failed: " + e.getMessage(), e);
        }
    }

    private BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        logger.info("Resizing image to {}x{}", width, height);
        Image tmp = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = resized.createGraphics();
        g2d.setComposite(AlphaComposite.Src);
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resized;
    }
}
