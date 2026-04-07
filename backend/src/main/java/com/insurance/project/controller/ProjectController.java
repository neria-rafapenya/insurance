package com.insurance.project.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.insurance.auth.model.AppUser;
import com.insurance.project.dto.CreateProjectRequest;
import com.insurance.project.dto.ProjectDto;
import com.insurance.project.service.ProjectService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/projects")
@Validated
public class ProjectController {
  private final ProjectService projectService;

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  @PostMapping
  public ProjectDto createProject(
      @AuthenticationPrincipal AppUser user,
      @Valid @RequestBody CreateProjectRequest request
  ) {
    return projectService.createProject(user, request);
  }
}
