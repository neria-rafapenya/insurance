package com.insurance.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.project.model.Project;

public interface ProjectRepository extends JpaRepository<Project, Integer> {
  List<Project> findByUserId(Integer userId);
}
