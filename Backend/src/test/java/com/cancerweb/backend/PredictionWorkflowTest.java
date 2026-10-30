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

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

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

        String body = mvc.perform(multipart("/api/predictions").param("view", "CC")
                        .file(new MockMultipartFile("image", "mammo.png", "image/png", png))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value(anyOf(is("benign"), is("malignant"), is("uncertain"))))
                .andExpect(jsonPath("$.malignantProbability").isNumber())
                .andExpect(jsonPath("$.modelVersion").exists())
                .andExpect(jsonPath("$.demoMode").isBoolean())
                .andExpect(jsonPath("$.originalFilename").value("mammo.png"))
                .andExpect(jsonPath("$.view").value("CC"))
                .andExpect(jsonPath("$.threshold").isNumber())
                .andExpect(jsonPath("$.lowConfidence").isBoolean())
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
        mvc.perform(multipart("/api/predictions").param("view", "CC")
                        .file(new MockMultipartFile("image", "notes.txt", "text/plain", "hello".getBytes()))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Upload a PNG or JPG mammogram"));
    }

    @Test
    void reportHasAHeatmapAndOlderReportsGetOneOnFirstRequest() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(modelPath)), "model.onnx not found; run ML-Service/export_onnx.py");
        Cookie patient = signedIn(Role.PATIENT, "scan@example.com");
        String body = mvc.perform(multipart("/api/predictions")
                        .file(new MockMultipartFile("image", "mammo.png", "image/png", samplePng()))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.heatmap").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        String heatmap = "/api/predictions/" + id + "/heatmap";
        Assumptions.assumeTrue(mvc.perform(get(heatmap).cookie(patient)).andReturn().getResponse().getStatus() == 200,
                "heatmap.onnx not found; run ML-Service/heatmap_output.py");

        String first = mvc.perform(get(heatmap).cookie(patient))
                .andExpect(jsonPath("$.rows").value(16))
                .andExpect(jsonPath("$.cols").value(16))
                .andExpect(jsonPath("$.values.length()").value(256))
                .andExpect(jsonPath("$.modelVersion").isString())
                .andReturn().getResponse().getContentAsString();

        // A report saved before heatmaps existed: the map is made from the stored image and kept.
        mongo.getCollection("predictions").updateOne(Filters.eq("_id", new ObjectId(id)), Updates.unset("heatmap"));
        mvc.perform(get(heatmap).cookie(patient)).andExpect(status().isOk()).andExpect(content().json(first));
        assertThat(mongo.getCollection("predictions").find(Filters.eq("_id", new ObjectId(id))).first().get("heatmap"))
                .isNotNull();
    }

    @Test
    void retryingAnUploadReturnsTheFirstRecordInsteadOfSavingTwice() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(modelPath)), "model.onnx not found; run ML-Service/export_onnx.py");
        Cookie patient = signedIn(Role.PATIENT, "scan@example.com");
        MockMultipartFile image = new MockMultipartFile("image", "mammo.png", "image/png", samplePng());
        String uploadId = "3f2b8c1e-7d4a-4b9e-9c2f-1a2b3c4d5e6f";

        String first = mvc.perform(multipart("/api/predictions").param("view", "CC").file(image).param("uploadId", uploadId)
                        .cookie(patient).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(multipart("/api/predictions").param("view", "CC").file(image).param("uploadId", uploadId).cookie(patient).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")));

        assertThat(mongo.getCollection("predictions").countDocuments()).isEqualTo(1);
        assertThat(mongo.getCollection("fs.files").countDocuments()).isEqualTo(1);
    }

    @Test
    void storesTheTypeFoundInTheFileNotTheOneTheBrowserSent() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(modelPath)), "model.onnx not found; run ML-Service/export_onnx.py");
        Cookie patient = signedIn(Role.PATIENT, "scan@example.com");
        String body = mvc.perform(multipart("/api/predictions").param("view", "CC")
                        .file(new MockMultipartFile("image", "mammo.png", "text/html", samplePng()))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/predictions/" + JsonPath.read(body, "$.id") + "/image").cookie(patient))
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void rejectsFilesNamedLikeImagesThatAreNotImages() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "scan@example.com");
        mvc.perform(multipart("/api/predictions").param("view", "CC")
                        .file(new MockMultipartFile("image", "scan.png", "image/png", "<html>not an image</html>".getBytes()))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Upload a PNG or JPG mammogram"));
        assertThat(mongo.getCollection("fs.files").countDocuments()).isZero();
    }

    @Test
    void rejectsEmptyFilesAndMalformedUploadIds() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "scan@example.com");
        mvc.perform(multipart("/api/predictions").param("view", "CC")
                        .file(new MockMultipartFile("image", "scan.png", "image/png", new byte[0]))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The selected file is empty. Upload a PNG or JPG mammogram"));
        mvc.perform(multipart("/api/predictions").param("view", "CC")
                        .file(new MockMultipartFile("image", "mammo.png", "image/png", samplePng()))
                        .param("uploadId", "{\"$gt\": \"\"}")
                        .cookie(patient).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid upload id"));
    }

    @Test
    void theViewIsOptionalButMustBeValidWhenSent() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(modelPath)), "model.onnx not found; run ML-Service/export_onnx.py");
        Cookie patient = signedIn(Role.PATIENT, "scan@example.com");
        mvc.perform(multipart("/api/predictions")
                        .file(new MockMultipartFile("image", "mammo.png", "image/png", samplePng()))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.view").doesNotExist());
        for (String view : new String[] { "LATERAL", "cc" }) {
            mvc.perform(multipart("/api/predictions").param("view", view)
                            .file(new MockMultipartFile("image", "mammo.png", "image/png", samplePng()))
                            .cookie(patient).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(
                            "Choose the mammogram view: CC (craniocaudal) or MLO (mediolateral oblique)"));
        }
    }

    @Test
    void rejectsColourPhotos() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(modelPath)), "model.onnx not found; run ML-Service/export_onnx.py");
        Cookie patient = signedIn(Role.PATIENT, "scan@example.com");
        byte[] photo = new ClassPathResource("preprocessing/colour.png").getContentAsByteArray();
        mvc.perform(multipart("/api/predictions").param("view", "MLO")
                        .file(new MockMultipartFile("image", "holiday.png", "image/png", photo))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("not a mammogram")));
        assertThat(mongo.getCollection("predictions").countDocuments()).isZero();
    }

    @Test
    void rejectsGreyImagesThatAreNotMammograms() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(modelPath)), "model.onnx not found; run ML-Service/export_onnx.py");
        Cookie patient = signedIn(Role.PATIENT, "scan@example.com");
        byte[] cartoon = new ClassPathResource("preprocessing/grey_cartoon.png").getContentAsByteArray();
        mvc.perform(multipart("/api/predictions").param("view", "CC")
                        .file(new MockMultipartFile("image", "cartoon.png", "image/png", cartoon))
                        .cookie(patient).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("does not look like a mammogram")));
        assertThat(mongo.getCollection("predictions").countDocuments()).isZero();
        assertThat(mongo.getCollection("fs.files").countDocuments()).isZero();
    }

    @Test
    void modelInfoIsPublic() throws Exception {
        mvc.perform(get("/api/model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").isBoolean())
                .andExpect(jsonPath("$.trained").isBoolean())
                .andExpect(jsonPath("$.threshold").isNumber())
                .andExpect(jsonPath("$.imageHeight").isNumber());
    }

    @Test
    void doctorsCannotUpload() throws Exception {
        Cookie doctor = signedIn(Role.DOCTOR, "doc@example.com");
        mvc.perform(multipart("/api/predictions").param("view", "CC")
                        .file(new MockMultipartFile("image", "mammo.png", "image/png", samplePng()))
                        .cookie(doctor).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /** A real CBIS-DDSM mammogram (src/test/resources/preprocessing), so the mammogram check lets it through. */
    private static byte[] samplePng() throws Exception {
        return new ClassPathResource("preprocessing/portrait.png").getContentAsByteArray();
    }
}
