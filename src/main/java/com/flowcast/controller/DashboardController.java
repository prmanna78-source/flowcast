package com.flowcast.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
    @GetMapping({"/", "/dashboard", "/traffic", "/routes", "/predictions", "/status", "/reports"})
    public String dashboard() { return "index"; }
}
