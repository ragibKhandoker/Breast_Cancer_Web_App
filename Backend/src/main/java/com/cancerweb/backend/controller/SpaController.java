package com.cancerweb.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * View controller: hands the Vue app's client-side routes to index.html, so opening /about or /faq directly
 * (or reloading it) works. Vue Router then picks the page, or shows its Not Found page for unknown paths.
 * Only single-segment paths without a dot match (plus /reports/{id}, /breast-surgeons/{id}[/book] and
 * /appointments/{id}/confirmation), so /api/**, static files and /actuator are unaffected.
 */
@Controller
public class SpaController {

    @GetMapping({ "/{path:[^.]+}", "/reports/{id:[^.]+}", "/breast-surgeons/{id:[^.]+}", "/breast-surgeons/{id:[^.]+}/book",
            "/appointments/{id:[^.]+}/confirmation" })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
