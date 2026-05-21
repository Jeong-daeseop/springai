package com.krdevops.springai.chat.service;

import java.util.List;

public interface EgovOllamaModelService {
    List<String> getInstalledModels();
    boolean isOllamaAvailable();
}
