package com.cancerweb.backend.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.cancerweb.backend.audit.AuditLog;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Breast Cancer Knowledge Hub: public reading, progress, the admin CMS and Breast Surgeon submissions. */
class KnowledgeHubTest extends IntegrationTest {

    private static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };
    private static final byte[] PDF = "%PDF-1.4\n% test certificate\n".getBytes(StandardCharsets.US_ASCII);
    private static final List<String> SLUGS = List.of("history", "understanding", "classification", "types", "stages",
            "risk-factors", "symptoms", "diagnosis", "mammography-birads", "treatment", "surgery-recovery", "prevention");

    @Autowired
    private KnowledgeService knowledge;

    @Autowired
    private GridFsTemplate gridFs;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void seed() {
        // The collections were emptied for this test; put the seed back as a fresh start would.
        knowledge.seed();
    }

    private static String moduleJson(String slug, String title, String url) {
        return "{\"slug\":\"" + slug + "\",\"order\":20,\"title\":\"" + title + "\",\"category\":\"Foundations\","
                + "\"illustration\":\"book\",\"readingMinutes\":4,\"description\":\"A short test lesson about breast health.\","
                + "\"sections\":[{\"title\":\"Overview\",\"body\":\"Some text.\\n- A bullet\"},"
                + "{\"title\":\"Steps\",\"display\":\"steps\",\"items\":[{\"title\":\"One\",\"text\":\"First step\"}]}],"
                + "\"references\":[{\"title\":\"Breast Cancer\",\"publisher\":\"NCI\",\"url\":\"" + url + "\"}],"
                + "\"related\":[\"history\"]}";
    }

    // ----- Public reading -----

    @Test
    void visitorsReadTwelvePublishedModulesWithoutSigningIn() throws Exception {
        String list = mvc.perform(get("/api/knowledge/modules"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(list, "$[*].slug")).containsExactlyElementsOf(SLUGS);

        mvc.perform(get("/api/knowledge/modules/mammography-birads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mammography and BI-RADS"))
                .andExpect(jsonPath("$.sections[?(@.display == 'birads')].items.length()").value(7));
        mvc.perform(get("/api/knowledge/modules/symptoms"))
                .andExpect(jsonPath("$.video.youtubeId").value("df1FBYmfZmU"))
                .andExpect(jsonPath("$.video.channel").value("Circle Health Group"));
        assertThat(JsonPath.<List<Boolean>>read(list, "$[*].hasVideo")).containsOnly(true);
        mvc.perform(get("/api/knowledge/glossary")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(20)));
        mvc.perform(get("/api/knowledge/faqs")).andExpect(status().isOk()).andExpect(jsonPath("$[0].question").exists());
        mvc.perform(get("/api/knowledge/modules/no-such-lesson")).andExpect(status().isNotFound());
    }

    @Test
    void everySeededModuleHasHttpsReferencesAndValidRelatedLinks() {
        List<KnowledgeModule> modules = knowledge.listPublished();
        assertThat(modules).hasSize(12);
        for (KnowledgeModule m : modules) {
            assertThat(m.references()).as(m.slug()).isNotEmpty();
            assertThat(m.references()).allSatisfy(r -> assertThat(r.url()).startsWith("https://"));
            assertThat(SLUGS).as(m.slug()).containsAll(m.related());
        }
    }

    @Test
    void seedingAgainKeepsAdministratorEdits() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        mvc.perform(withJson(put("/api/admin/knowledge/modules/history"), moduleJson("ignored", "History (edited)", "https://www.cancer.gov/types/breast"))
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("history"));

        assertThat(knowledge.seed()).isZero();
        mvc.perform(get("/api/knowledge/modules/history")).andExpect(jsonPath("$.title").value("History (edited)"));
    }

    @Test
    void seedingBringsUntouchedModulesUpToDate() {
        // A module written from an older seed and never edited: older content, matching seedHash.
        KnowledgeModule m = knowledge.find("symptoms");
        KnowledgeContent old = new KnowledgeContent(m.slug(), m.order(), "Old title", m.category(), m.illustration(),
                m.readingMinutes(), m.description(), m.sections().subList(0, 1), m.references(), m.related(), null);
        mongo.save(KnowledgeModule.from(old, m.id(), m.published(), true, m.createdAt(), m.updatedAt(),
                KnowledgeService.contentHash(old)));
        // One saved before seedHash existed and never saved again counts as untouched too.
        KnowledgeModule t = knowledge.find("types");
        KnowledgeContent oldTypes = new KnowledgeContent(t.slug(), t.order(), "Old title", t.category(), t.illustration(),
                t.readingMinutes(), t.description(), t.sections(), t.references(), t.related(), null);
        mongo.save(KnowledgeModule.from(oldTypes, t.id(), t.published(), true, t.createdAt(), t.createdAt(), null));

        assertThat(knowledge.seed()).isEqualTo(2);
        KnowledgeModule symptoms = knowledge.find("symptoms");
        assertThat(symptoms.title()).isEqualTo("Signs and Symptoms");
        assertThat(symptoms.sections()).extracting(KnowledgeContent.Section::id).contains("self-exam");
        assertThat(symptoms.createdAt()).isEqualTo(m.createdAt());
        assertThat(knowledge.find("types").title()).isEqualTo("Types of Breast Cancer");
        assertThat(knowledge.seed()).isZero();
    }

    @Test
    void addingAnOptionalFieldKeepsOlderSeedHashes() {
        // Hashes saved before "video" existed were computed from JSON without that key.
        KnowledgeContent c = knowledge.find("history").content();
        KnowledgeContent noVideo = new KnowledgeContent(c.slug(), c.order(), c.title(), c.category(), c.illustration(),
                c.readingMinutes(), c.description(), c.sections(), c.references(), c.related(), null);
        tools.jackson.databind.node.ObjectNode tree = tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(noVideo);
        tree.remove("video");
        String olderHash = com.cancerweb.backend.audit.AuditHasher.sha256(tree.toString());
        assertThat(KnowledgeService.contentHash(noVideo)).isEqualTo(olderHash);

        // A module seeded before videos existed, never edited, is brought up to date (and gets its video).
        KnowledgeModule m = knowledge.find("history");
        mongo.save(KnowledgeModule.from(noVideo, m.id(), m.published(), true, m.createdAt(), m.updatedAt(), olderHash));
        assertThat(knowledge.seed()).isEqualTo(1);
        assertThat(knowledge.find("history").video().youtubeId()).isEqualTo("idjP7Y_qIIM");
    }

    // ----- Progress -----

    @Test
    void progressNeedsSignInAndIsKeptPerAccount() throws Exception {
        mvc.perform(get("/api/learning/progress")).andExpect(status().isUnauthorized());

        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(withJson(put("/api/learning/progress/history"), "{\"completed\":true}").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed[0]").value("history"));
        mvc.perform(withJson(put("/api/learning/bookmarks/stages"), "{\"bookmarked\":true}").cookie(patient))
                .andExpect(status().isOk());
        mvc.perform(withJson(put("/api/learning/last/types"), "{}").cookie(patient)).andExpect(status().isOk());
        mvc.perform(withJson(post("/api/learning/progress/merge"),
                        "{\"completed\":[\"types\",\"history\",\"made-up\"],\"bookmarks\":[\"symptoms\"]}").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed.length()").value(2))
                .andExpect(jsonPath("$.bookmarks.length()").value(2))
                .andExpect(jsonPath("$.lastSlug").value("types"));
        mvc.perform(withJson(put("/api/learning/progress/history"), "{\"completed\":false}").cookie(patient))
                .andExpect(jsonPath("$.completed.length()").value(1));
        mvc.perform(withJson(put("/api/learning/progress/made-up"), "{\"completed\":true}").cookie(patient))
                .andExpect(status().isNotFound());

        Cookie other = signedIn(Role.DOCTOR, "d@example.com");
        mvc.perform(get("/api/learning/progress").cookie(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed.length()").value(0));
    }

    // ----- Administrators -----

    @Test
    void onlyAdministratorsManageContent() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(get("/api/admin/knowledge/modules").cookie(patient)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/admin/knowledge/modules"), moduleJson("new-one", "New lesson", "https://www.who.int"))
                .cookie(patient)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/admin/knowledge/modules"), moduleJson("new-one", "New lesson", "https://www.who.int")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void newModulesStartUnpublishedAndCanBePublished() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        mvc.perform(withJson(post("/api/admin/knowledge/modules"), moduleJson("self-care", "Self care", "https://www.who.int"))
                        .cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.sections[0].id").value("overview"));
        mvc.perform(get("/api/knowledge/modules/self-care")).andExpect(status().isNotFound());

        mvc.perform(withJson(put("/api/admin/knowledge/modules/self-care/published"), "{\"published\":true}").cookie(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/knowledge/modules/self-care")).andExpect(status().isOk());

        // Unpublishing hides a seeded module from visitors without deleting it.
        mvc.perform(withJson(put("/api/admin/knowledge/modules/types/published"), "{\"published\":false}").cookie(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/knowledge/modules/types")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/knowledge/modules/types").cookie(admin)).andExpect(status().isOk());

        List<String> types = mongo.findAll(AuditLog.class).stream().map(e -> e.eventType()).toList();
        assertThat(types).contains("KNOWLEDGE_MODULE_SAVED", "KNOWLEDGE_MODULE_PUBLISHED", "KNOWLEDGE_MODULE_UNPUBLISHED");
    }

    @Test
    void contentIsCheckedBeforeSaving() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        mvc.perform(withJson(post("/api/admin/knowledge/modules"), moduleJson("bad-link", "Bad link", "javascript:alert(1)"))
                        .cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['references[0].url']").exists());
        mvc.perform(withJson(post("/api/admin/knowledge/modules"), moduleJson("Bad Slug!", "Bad slug", "https://www.who.int"))
                .cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(withJson(post("/api/admin/knowledge/modules"), moduleJson("history", "Duplicate", "https://www.who.int"))
                        .cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slug").exists());
        String badVideo = moduleJson("bad-video", "Bad video", "https://www.who.int")
                .replace("\"related\":", "\"video\":{\"youtubeId\":\"javascript:x\",\"title\":\"Video\"},\"related\":");
        mvc.perform(withJson(post("/api/admin/knowledge/modules"), badVideo).cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['video.youtubeId']").exists());
        String noRefs = moduleJson("no-refs", "No references", "https://www.who.int")
                .replaceAll("\"references\":\\[[^\\]]*\\]", "\"references\":[]");
        mvc.perform(withJson(post("/api/admin/knowledge/modules"), noRefs).cookie(admin)).andExpect(status().isBadRequest());
    }

    @Test
    void diagramsArePicturesOnlyAndOtherFilesAreNotServed() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        String body = mvc.perform(multipart("/api/admin/knowledge/images")
                        .file(new MockMultipartFile("image", "d.png", "image/png", PNG)).with(csrf()).cookie(admin))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(body, "$.fileId");
        mvc.perform(get("/api/knowledge/images/" + fileId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));

        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/admin/knowledge/images")
                        .file(new MockMultipartFile("image", "d.svg", "image/png", svg)).with(csrf()).cookie(admin))
                .andExpect(status().isBadRequest());

        // A file stored for something else (a scan, a document) is not reachable through the hub.
        ObjectId scan = gridFs.store(new ByteArrayInputStream(PNG), "scan.png", "image/png");
        mvc.perform(get("/api/knowledge/images/" + scan.toHexString())).andExpect(status().isNotFound());

        String withImage = moduleJson("with-diagram", "With diagram", "https://www.who.int")
                .replace("{\"title\":\"Overview\",", "{\"title\":\"Overview\",\"imageFileId\":\"" + fileId + "\",");
        mvc.perform(withJson(post("/api/admin/knowledge/modules"), withImage).cookie(admin)).andExpect(status().isCreated());
        String foreign = withImage.replace(fileId, scan.toHexString()).replace("with-diagram", "foreign-diagram");
        mvc.perform(withJson(post("/api/admin/knowledge/modules"), foreign).cookie(admin)).andExpect(status().isBadRequest());
    }

    // ----- Breast Surgeon submissions -----

    private void approvedSurgeon(Cookie admin, Cookie doctor) throws Exception {
        String profile = "{\"fullName\":\"Dr. Nasrin Akter\",\"email\":\"chamber@example.com\",\"phone\":\"+8801712345678\","
                + "\"designation\":\"Consultant Breast Surgeon\",\"qualifications\":\"MBBS, FCPS (Surgery)\","
                + "\"experienceYears\":12,\"hospitalName\":\"Dhaka Breast Centre\",\"hospitalAddress\":\"Dhanmondi, Dhaka\","
                + "\"consultationFee\":1500,\"biography\":\"Breast surgeon focused on early cancer care and reconstruction.\","
                + "\"registrationNumber\":\"BMDC-A-1234\",\"availableDays\":[\"MONDAY\"],\"timeSlots\":[\"10:00\"]}";
        String body = mvc.perform(withJson(post("/api/doctors/breast-surgeons/profile"), profile).cookie(doctor))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(multipart("/api/doctors/breast-surgeons/profile/documents")
                        .file(new MockMultipartFile("document", "registration.pdf", "application/pdf", PDF)).with(csrf()).cookie(doctor))
                .andExpect(status().isOk());
        mvc.perform(withJson(patch("/api/admin/breast-surgeons/" + id + "/review"), "{\"status\":\"APPROVED\"}").cookie(admin))
                .andExpect(status().isOk());
    }

    private static String submission(String target, String slug, String title) {
        return "{\"targetSlug\":" + (target == null ? "null" : "\"" + target + "\"") + ",\"content\":"
                + moduleJson(slug, title, "https://www.cancer.gov/types/breast") + "}";
    }

    @Test
    void onlyApprovedBreastSurgeonsCanSubmit() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(withJson(post("/api/knowledge-submissions"), submission(null, "x-lesson", "Lesson")).cookie(patient))
                .andExpect(status().isForbidden());
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        mvc.perform(withJson(post("/api/knowledge-submissions"), submission(null, "x-lesson", "Lesson")).cookie(doctor))
                .andExpect(status().isForbidden());
        assertThat(mongo.count(new Query(), KnowledgeSubmission.class)).isZero();
    }

    @Test
    void submissionsAppearOnlyAfterAdministratorApproval() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        approvedSurgeon(admin, doctor);

        String created = mvc.perform(withJson(post("/api/knowledge-submissions"),
                        submission("stages", "ignored", "Stages (surgeon edit)")).cookie(doctor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.authorName").value("Dr. Nasrin Akter"))
                .andReturn().getResponse().getContentAsString();
        String editId = JsonPath.read(created, "$.id");
        mvc.perform(get("/api/knowledge/modules/stages")).andExpect(jsonPath("$.title").value("Breast Cancer Stages"));

        String created2 = mvc.perform(withJson(post("/api/knowledge-submissions"),
                        submission(null, "lymph-care", "Lymph care")).cookie(doctor))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String newId = JsonPath.read(created2, "$.id");
        mvc.perform(get("/api/knowledge-submissions").cookie(doctor)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/admin/knowledge/submissions?status=PENDING").cookie(admin)).andExpect(jsonPath("$.length()").value(2));

        // Rejecting needs a note; approving applies the content.
        mvc.perform(withJson(put("/api/admin/knowledge/submissions/" + newId + "/review"), "{\"status\":\"REJECTED\"}").cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(put("/api/admin/knowledge/submissions/" + newId + "/review"),
                        "{\"status\":\"REJECTED\",\"note\":\"Please add a second reference.\"}").cookie(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/knowledge/modules/lymph-care")).andExpect(status().isNotFound());

        mvc.perform(withJson(put("/api/admin/knowledge/submissions/" + editId + "/review"), "{\"status\":\"APPROVED\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/knowledge/modules/stages")).andExpect(jsonPath("$.title").value("Stages (surgeon edit)"));
        mvc.perform(withJson(put("/api/admin/knowledge/submissions/" + editId + "/review"), "{\"status\":\"APPROVED\"}").cookie(admin))
                .andExpect(status().isConflict());

        // The surgeon was told about both decisions.
        String doctorId = users.findByEmail("d@example.com").orElseThrow().id();
        long notes = mongo.count(Query.query(Criteria.where("type").is("KNOWLEDGE").and("userId").is(doctorId)), "notifications");
        assertThat(notes).isEqualTo(2);
    }
}
