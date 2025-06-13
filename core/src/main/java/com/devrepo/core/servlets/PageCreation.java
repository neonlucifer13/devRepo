package com.devrepo.core.servlets;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.devrepo.core.service.AesConfigService;
import com.devrepo.core.service.AesKeyService;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.settings.SlingSettingsService;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;

@Component(
        service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Create Editable AEM Page Servlet",
                "sling.servlet.paths=/apps/test/createpage",
                "sling.servlet.methods=POST"
        }
)
public class PageCreation extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(PageCreation.class);
    @Reference
    private AesConfigService aesConfigService;

    @Reference
    private SlingSettingsService slingSettingsService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {


        if (!(slingSettingsService.getRunModes().contains("author") &&
                (slingSettingsService.getRunModes().contains("local") || slingSettingsService.getRunModes().contains("dev")))) {
            log.warn("Unauthorized access attempt. Servlet is restricted to local/dev author environments.");
            response.setStatus(SlingHttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Access denied. This servlet is only available in local or dev author environments.");
            return;
        }

        String encryptedKey = request.getParameter("authKey");
        log.info("Encrypted key: {}", encryptedKey);
        log.info(aesConfigService.getSecretKey());
        if (encryptedKey == null) {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Missing encrypted authKey");
            return;
        }
        try {
            String decryptedKey = AesKeyService.decrypt(encryptedKey, aesConfigService.getSecretKey());
            log.info("Decrypted key: {}", decryptedKey);
        } catch (Exception e) {
            response.setStatus(SlingHttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Invalid encryption or key mismatch");
            return;
        }

        ResourceResolver resolver = request.getResourceResolver();
        PageManager pageManager = resolver.adaptTo(PageManager.class);

        try {
            if (pageManager == null) {
                response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("PageManager adaptation failed");
                return;
            }

            // Excel from DAM
            String damExcelPath = request.getParameter("filePath");
            String damExcelPathRendition = damExcelPath +"/jcr:content/renditions/original/jcr:content";
            Resource fileResource = resolver.getResource(damExcelPathRendition);

            if (fileResource == null) {
                response.setStatus(404);
                response.getWriter().write("Excel file not found in DAM.");
                return;
            }

            Node fileNode = fileResource.adaptTo(Node.class);
            if (fileNode == null || !fileNode.hasProperty("jcr:data")) {
                response.setStatus(500);
                response.getWriter().write("Excel binary not found.");
                return;
            }

            Binary binary = fileNode.getProperty("jcr:data").getBinary();

            try (InputStream is = binary.getStream(); Workbook workbook = new XSSFWorkbook(is)) {
                Sheet sheet = workbook.getSheetAt(0);
                int count = 0;
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;

                    String pageName = getCellValue(row.getCell(0));
                    String title = getCellValue(row.getCell(1));
                    String parentPath = getCellValue(row.getCell(2));
                    String templatePath = getCellValue(row.getCell(3));

                    try {
                        Page newPage = pageManager.create(parentPath, pageName, templatePath, title);
                        if (newPage != null) {
                            response.getWriter().write("Created: " + newPage.getPath() + "\n");
                        } else {
                            response.getWriter().write("Failed to create: " + pageName + "\n");
                        }
                    } catch (Exception e) {
                        response.getWriter().write("Error for " + pageName + ": " + e.getMessage() + "\n");
                    }
                    count++;
                    if (count % 50 == 0) {
                        response.getWriter().flush(); // flush every 50 to avoid buffer overflow
                    }
                }
                response.getWriter().write("Total Pages Attempted: " + count + "\n");
            }

        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("Exception: " + e.getMessage());
        }
    }
    private String getCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString(); // Format if needed
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula(); // or evaluate with FormulaEvaluator
            default:
                return "";
        }
    }

}
