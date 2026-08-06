package com.krdevops.springai.service.resilience;

import com.krdevops.springai.chat.controller.EgovWebController;
import com.krdevops.springai.config.OperationalResilienceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class Wp9FailureIsolationIntegrationTest {

    @Test
    void figma_circuit이_열려도_무관한_UI_endpoint는_정상이다() throws Exception {
        OperationalResilienceProperties properties = new OperationalResilienceProperties();
        properties.getCircuitBreaker().setFailureThreshold(1);
        ExternalCallGuard guard = new ExternalCallGuard(properties);
        assertThatThrownBy(() -> guard.execute(ExternalDependency.FIGMA, () -> {
            throw new IllegalStateException("injected Figma outage");
        })).isInstanceOf(IllegalStateException.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EgovWebController()).build();
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("chat"));
    }
}

