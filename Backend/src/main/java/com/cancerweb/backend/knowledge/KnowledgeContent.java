package com.cancerweb.backend.knowledge;

import java.util.List;

/**
 * The editable part of a Knowledge Hub module: what the seed file holds, what an administrator saves and what a
 * Breast Surgeon submits for review. Everything is plain text; the site never renders stored HTML.
 *
 * <p>Text conventions (see KnowledgeBody.vue): a blank line starts a paragraph, a line starting with "- " is a
 * bullet and a line starting with "> " is a highlighted note. {@code advanced} is the optional "Learn more" text.
 */
public record KnowledgeContent(
        String slug,
        Integer order,
        String title,
        String category,
        String illustration,
        Integer readingMinutes,
        String description,
        List<Section> sections,
        List<Reference> references,
        List<String> related) {

    /**
     * One part of a module. {@code display} picks how {@code items} are drawn (timeline, cards, steps, stages,
     * birads); {@code diagram} names a built-in diagram and {@code imageFileId} an uploaded one.
     */
    public record Section(
            String id,
            String title,
            String body,
            String advanced,
            String display,
            List<Item> items,
            Table table,
            String diagram,
            String imageFileId,
            String imageCaption) {
    }

    public record Item(String label, String title, String text, String note) {
    }

    public record Table(List<String> columns, List<List<String>> rows) {
    }

    public record Reference(String title, String publisher, String url) {
    }
}
