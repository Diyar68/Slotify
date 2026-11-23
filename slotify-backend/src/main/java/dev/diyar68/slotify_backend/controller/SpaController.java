package dev.diyar68.slotify_backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Only known React routes fall back to HTML; APIs and missing assets keep their 404. */
@Controller
public class SpaController {

    @GetMapping({
        "/",
        "/login",
        "/register",
        "/register/complete",
        "/dashboard",
        "/dashboard/services",
        "/dashboard/availabilities",
        "/dashboard/appointments",
        "/book/{bookingSlug:[a-z0-9-]+}",
    })
    public String index() {
        return "forward:/index.html";
    }
}
