package org.wickedsource.coderadar.analyzer.rest;

import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.wickedsource.coderadar.analyzer.domain.AnalyzerConfiguration;
import org.wickedsource.coderadar.project.rest.ProjectController;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

public class AnalyzerConfigurationResourceAssembler extends ResourceAssemblerSupport<AnalyzerConfiguration, AnalyzerConfigurationResource> {

    private Long projectId;

    AnalyzerConfigurationResourceAssembler(Long projectId) {
        super(AnalyzerConfigurationController.class, AnalyzerConfigurationResource.class);
        this.projectId = projectId;
    }

    @Override
    public AnalyzerConfigurationResource toResource(AnalyzerConfiguration entity) {
        AnalyzerConfigurationResource resource = new AnalyzerConfigurationResource(entity.getAnalyzerName(), entity.getEnabled());
        resource.add(linkTo(methodOn(ProjectController.class).getProject(projectId)).withRel("project"));
        resource.add(linkTo(methodOn(AnalyzerConfigurationController.class).getAnalyzerConfigurationsForProject(projectId)).withRel("list"));
        return resource;
    }

    public List<AnalyzerConfigurationResource> toResourceList(List<AnalyzerConfiguration> entities) {
        List<AnalyzerConfigurationResource> resultList = new ArrayList<>();
        for (AnalyzerConfiguration entity : entities) {
            resultList.add(toResource(entity));
        }
        return resultList;
    }

    public AnalyzerConfiguration updateEntity(AnalyzerConfiguration entity, AnalyzerConfigurationResource resource) {
        entity.setAnalyzerName(resource.getAnalyzerName());
        entity.setEnabled(resource.isEnabled());
        return entity;
    }
}
