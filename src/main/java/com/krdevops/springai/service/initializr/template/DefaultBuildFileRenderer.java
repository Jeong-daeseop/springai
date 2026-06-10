package com.krdevops.springai.service.initializr.template;

import com.krdevops.springai.model.ProjectSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultBuildFileRenderer implements BuildFileRenderer {

    private final WarPomBuilder warPomBuilder;
    private final BootPomBuilder bootPomBuilder;
    private final WarBuildGradleBuilder warBuildGradleBuilder;
    private final BootBuildGradleBuilder bootBuildGradleBuilder;
    private final DispatcherServletBuilder dispatcherServletBuilder;
    private final WebXmlBuilder webXmlBuilder;

    @Override public String warPom(ProjectSpec s)            { return warPomBuilder.build(s); }
    @Override public String bootPom(ProjectSpec s)           { return bootPomBuilder.build(s); }
    @Override public String warBuildGradle(ProjectSpec s)    { return warBuildGradleBuilder.build(s); }
    @Override public String bootBuildGradle(ProjectSpec s)   { return bootBuildGradleBuilder.build(s); }
    @Override public String dispatcherServlet(ProjectSpec s) { return dispatcherServletBuilder.build(s); }
    @Override public String webXml(ProjectSpec s)            { return webXmlBuilder.build(s); }
}
