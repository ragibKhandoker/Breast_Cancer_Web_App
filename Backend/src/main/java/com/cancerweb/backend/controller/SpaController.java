package com.cancerweb.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * View controller: hands the Vue app's client-side routes to index.html, so reloading /prediction
 * or /about does not 404. Vue Router then picks the page.
 */
@Controller
public class SpaController {

    @GetMapping({ "/prediction", "/history", "/learn", "/resources", "/support", "/about", "/login",
            "/privacy", "/terms" })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
