package com.insurance.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.insurance.auth.model.AppUser;
import com.insurance.auth.model.UserRole;
import com.insurance.project.dto.CreateProjectRequest;
import com.insurance.project.dto.ProjectDto;
import com.insurance.project.model.Project;
import com.insurance.project.model.ProjectStatus;
import com.insurance.project.repository.ProjectRepository;

@Service
public class ProjectService {
  private final ProjectRepository projectRepository;

  public ProjectService(ProjectRepository projectRepository) {
    this.projectRepository = projectRepository;
  }

  public ProjectDto createProject(AppUser user, CreateProjectRequest request) {
    if (user.getRole() == UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin no puede crear proyectos");
    }

    Project project = new Project();
    project.setUser(user);
    project.setNombre(request.nombre());
    project.setTipoSeguro(request.tipoSeguro());
    project.setEstado(ProjectStatus.DRAFT);
    project.setCurrentStep(0);

    Project saved = projectRepository.save(project);
    return toDto(saved);
  }

  private ProjectDto toDto(Project project) {
    return new ProjectDto(
        project.getId(),
        project.getNombre(),
        project.getTipoSeguro(),
        project.getEstado().name().toLowerCase(),
        project.getCurrentStep()
    );
  }
}
