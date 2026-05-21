package com.krdevops.springai.chat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public class EgovWebController {

    @GetMapping("/")
    public String chatPage() {
        return "chat";
    }
}
