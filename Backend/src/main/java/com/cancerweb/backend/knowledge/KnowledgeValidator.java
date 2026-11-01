package com.cancerweb.backend.knowledge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.cancerweb.backend.exception.FieldValidationException;
import com.cancerweb.backend.knowledge.KnowledgeContent.Item;
import com.cancerweb.backend.knowledge.KnowledgeContent.Reference;
import com.cancerweb.backend.knowledge.KnowledgeContent.Section;
import com.cancerweb.backend.knowledge.KnowledgeContent.Table;

/**
 * Checks and tidies module content before it is stored: trims text, applies size limits, and allows only the
 * known layouts, diagrams and illustrations. Reference links must be https, so no script link can be saved.
 */
final class KnowledgeValidator {

    static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    static final Pattern URL = Pattern.compile("https://[^\\s<>\"'`]+");
    static final Set<String> DISPLAYS = Set.of("timeline", "cards", "steps", "stages", "birads");
    static final Set<String> DIAGRAMS = Set.of("anatomy", "cell-growth", "receptors", "grade", "tnm");
    static final Set<String> ILLUSTRATIONS = Set.of("history", "cells", "classification", "microscope", "stages", "dna",
            "symptoms", "diagnosis", "mammogram", "treatment", "surgery", "survivorship", "book");

    static final int MAX_SECTIONS = 30;
    static final int MAX_ITEMS = 40;
    static final int MAX_REFERENCES = 30;
    static final int MAX_RELATED = 6;
    static final int MAX_TABLE_COLUMNS = 6;
    static final int MAX_TABLE_ROWS = 40;

    private KnowledgeValidator() {
    }

    static KnowledgeContent validate(KnowledgeContent c) {
        if (c == null) {
            throw new FieldValidationException("title", "Add the module content");
        }
        String slug = text(c.slug(), "slug", 2, 60, true).toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(slug).matches()) {
            throw new FieldValidationException("slug", "Use lower-case letters, numbers and single hyphens, like risk-factors");
        }
        int order = c.order() == null ? 99 : c.order();
        if (order < 1 || order > 999) {
            throw new FieldValidationException("order", "Use an order between 1 and 999");
        }
        String title = text(c.title(), "title", 3, 120, true);
        String category = text(c.category(), "category", 2, 40, true);
        String illustration = c.illustration() == null || c.illustration().isBlank() ? "book" : c.illustration().trim();
        if (!ILLUSTRATIONS.contains(illustration)) {
            throw new FieldValidationException("illustration", "Choose one of the listed illustrations");
        }
        int minutes = c.readingMinutes() == null ? 5 : c.readingMinutes();
        if (minutes < 1 || minutes > 120) {
            throw new FieldValidationException("readingMinutes", "Use a reading time between 1 and 120 minutes");
        }
        String description = text(c.description(), "description", 10, 400, true);
        List<Section> sections = sections(c.sections());
        List<Reference> references = references(c.references());
        List<String> related = related(c.related(), slug);
        return new KnowledgeContent(slug, order, title, category, illustration, minutes, description, sections, references,
                related);
    }

    private static List<Section> sections(List<Section> input) {
        if (input == null || input.isEmpty()) {
            throw new FieldValidationException("sections", "Add at least one section");
        }
        if (input.size() > MAX_SECTIONS) {
            throw new FieldValidationException("sections", "Use at most " + MAX_SECTIONS + " sections");
        }
        List<Section> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < input.size(); i++) {
            Section s = input.get(i);
            String at = "sections[" + i + "]";
            if (s == null) {
                throw new FieldValidationException(at, "This section is empty");
            }
            String title = text(s.title(), at + ".title", 2, 120, true);
            String id = s.id() == null || s.id().isBlank() ? slugify(title) : s.id().trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty() || id.length() > 60 || !SLUG.matcher(id).matches()) {
                throw new FieldValidationException(at + ".id", "Use lower-case letters, numbers and hyphens");
            }
            if (!ids.add(id)) {
                throw new FieldValidationException(at + ".id", "Two sections use the id " + id);
            }
            String body = text(s.body(), at + ".body", 0, 10_000, false);
            String advanced = text(s.advanced(), at + ".advanced", 0, 5_000, false);
            String display = blankToNull(s.display());
            if (display != null && !DISPLAYS.contains(display)) {
                throw new FieldValidationException(at + ".display", "Unknown layout: " + display);
            }
            List<Item> items = items(s.items(), at);
            if (display != null && items.isEmpty()) {
                throw new FieldValidationException(at + ".items", "Add items for this layout, or remove the layout");
            }
            if (display == null && !items.isEmpty()) {
                display = "cards";
            }
            Table table = table(s.table(), at);
            String diagram = blankToNull(s.diagram());
            if (diagram != null && !DIAGRAMS.contains(diagram)) {
                throw new FieldValidationException(at + ".diagram", "Unknown diagram: " + diagram);
            }
            String imageFileId = blankToNull(s.imageFileId());
            if (imageFileId != null && !ObjectId.isValid(imageFileId)) {
                throw new FieldValidationException(at + ".imageFileId", "Upload the diagram again");
            }
            String caption = imageFileId == null ? null : text(s.imageCaption(), at + ".imageCaption", 0, 200, false);
            if (body == null && items.isEmpty() && table == null && diagram == null && imageFileId == null) {
                throw new FieldValidationException(at + ".body", "Add some text to this section");
            }
            out.add(new Section(id, title, body, advanced, display, items.isEmpty() ? null : items, table, diagram,
                    imageFileId, caption));
        }
        return List.copyOf(out);
    }

    private static List<Item> items(List<Item> input, String at) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        if (input.size() > MAX_ITEMS) {
            throw new FieldValidationException(at + ".items", "Use at most " + MAX_ITEMS + " items");
        }
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            Item item = input.get(i);
            String where = at + ".items[" + i + "]";
            if (item == null) {
                throw new FieldValidationException(where, "This item is empty");
            }
            String title = text(item.title(), where + ".title", 0, 160, false);
            String text = text(item.text(), where + ".text", 0, 1_500, false);
            if (title == null && text == null) {
                throw new FieldValidationException(where + ".title", "Give this item a title or some text");
            }
            out.add(new Item(text(item.label(), where + ".label", 0, 80, false), title, text,
                    text(item.note(), where + ".note", 0, 800, false)));
        }
        return out;
    }

    private static Table table(Table input, String at) {
        if (input == null || input.columns() == null || input.columns().isEmpty()) {
            return null;
        }
        List<String> columns = input.columns();
        if (columns.size() > MAX_TABLE_COLUMNS) {
            throw new FieldValidationException(at + ".table", "Use at most " + MAX_TABLE_COLUMNS + " columns");
        }
        List<List<String>> rows = input.rows() == null ? List.of() : input.rows();
        if (rows.isEmpty() || rows.size() > MAX_TABLE_ROWS) {
            throw new FieldValidationException(at + ".table", "Use 1 to " + MAX_TABLE_ROWS + " table rows");
        }
        List<String> cols = columns.stream().map(v -> v == null ? "" : clip(v.trim(), 80, at + ".table")).toList();
        List<List<String>> cleanRows = new ArrayList<>();
        for (List<String> row : rows) {
            if (row == null || row.size() != cols.size()) {
                throw new FieldValidationException(at + ".table", "Every row needs " + cols.size() + " cells");
            }
            cleanRows.add(row.stream().map(v -> v == null ? "" : clip(v.trim(), 400, at + ".table")).toList());
        }
        return new Table(cols, List.copyOf(cleanRows));
    }

    private static List<Reference> references(List<Reference> input) {
        if (input == null || input.isEmpty()) {
            throw new FieldValidationException("references", "Add at least one trusted reference");
        }
        if (input.size() > MAX_REFERENCES) {
            throw new FieldValidationException("references", "Use at most " + MAX_REFERENCES + " references");
        }
        List<Reference> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            Reference r = input.get(i);
            String at = "references[" + i + "]";
            if (r == null) {
                throw new FieldValidationException(at, "This reference is empty");
            }
            String url = text(r.url(), at + ".url", 10, 500, true);
            if (!URL.matcher(url).matches()) {
                throw new FieldValidationException(at + ".url", "Use a full https:// link");
            }
            out.add(new Reference(text(r.title(), at + ".title", 2, 250, true), text(r.publisher(), at + ".publisher", 0, 120, false), url));
        }
        return List.copyOf(out);
    }

    private static List<String> related(List<String> input, String slug) {
        if (input == null) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String value : input) {
            String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (!SLUG.matcher(v).matches() || v.length() > 60) {
                throw new FieldValidationException("related", "Related modules must be module slugs");
            }
            if (!v.equals(slug)) {
                out.add(v);
            }
        }
        if (out.size() > MAX_RELATED) {
            throw new FieldValidationException("related", "Link at most " + MAX_RELATED + " related modules");
        }
        return List.copyOf(out);
    }

    /** Trimmed text within the limits; null when empty and not required. */
    private static String text(String value, String field, int min, int max, boolean required) {
        String v = value == null ? "" : value.strip().replace("\r\n", "\n");
        if (v.isEmpty()) {
            if (required) {
                throw new FieldValidationException(field, "This field is required");
            }
            return null;
        }
        if (v.length() < min) {
            throw new FieldValidationException(field, "Use at least " + min + " characters");
        }
        return clip(v, max, field);
    }

    private static String clip(String v, int max, String field) {
        if (v.length() > max) {
            throw new FieldValidationException(field, "Use at most " + max + " characters");
        }
        return v;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    static String slugify(String title) {
        String s = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return s.length() > 60 ? s.substring(0, 60).replaceAll("-+$", "") : s;
    }
}
