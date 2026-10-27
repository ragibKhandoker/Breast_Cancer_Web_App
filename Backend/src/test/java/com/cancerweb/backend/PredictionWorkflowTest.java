package com.cancerweb.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** The existing analysis flow, end to end with the real ONNX model, now tied to the signed-in patient. */
class PredictionWorkflowTest extends IntegrationTest {

    @Value("${ml.model.path}")
    private String modelPath;

    @Test
    void patientUploadsListsViewsAndDeletesAnAnalysis() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(modelPath)), "model.onnx not found; run ML-Service/export_onnx.py");
        Cookie patient = signedIn(Role.PATIENT, "scan@example.com");
        String patientId = userService.listAll().getFirst().id();
        byte[] png = samplePng();

        String body = mvc.perform(multipart("/api/predictions")
                        .file(new MockMultipartFile("image", "mammo.png", "image/png", png))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value(anyOf(is("benign"), is("malignant"))))
                .andExpect(jsonPath("$.malignantProbability").isNumber())
                .andExpect(jsonPath("$.modelVersion").exists())
                .andExpect(jsonPath("$.demoMode").isBoolean())
                .andExpect(jsonPath("$.originalFilename").value("mammo.png"))
                .andExpect(jsonPath("$.ownerId").value(patientId))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/predictions").cookie(patient)).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/predictions/" + id + "/image").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
        mvc.perform(delete("/api/predictions/" + id).cookie(patient).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/predictions/" + id).cookie(patient)).andExpect(status().isNotFound());
        assertThat(mongo.getCollection("fs.files").countDocuments()).isZero();
    }

    @Test
    void rejectsFilesThatAreNotImages() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "scan@example.com");
        mvc.perform(multipart("/api/predictions")
                        .file(new MockMultipartFile("image", "notes.txt", "text/plain", "hello".getBytes()))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Upload a PNG or JPG mammogram"));
    }

    private static byte[] samplePng() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                image.setRGB(x, y, (x * 4) << 16 | (y * 4) << 8 | 128);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
