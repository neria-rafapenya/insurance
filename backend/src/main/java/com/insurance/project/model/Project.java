package com.insurance.project.model;

import java.time.Instant;

import com.insurance.auth.model.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ins_projects")
public class Project {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private AppUser user;

  @Column(name = "nombre", nullable = false)
  private String nombre;

  @Column(name = "tipo_seguro")
  private String tipoSeguro;

  @Column(name = "estado", nullable = false)
  private ProjectStatus estado = ProjectStatus.DRAFT;

  @Column(name = "current_step", nullable = false)
  private Integer currentStep = 0;

  @Column(name = "data", columnDefinition = "json")
  private String data;

  @Column(name = "result", columnDefinition = "json")
  private String result;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private Instant updatedAt;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public AppUser getUser() {
    return user;
  }

  public void setUser(AppUser user) {
    this.user = user;
  }

  public String getNombre() {
    return nombre;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public String getTipoSeguro() {
    return tipoSeguro;
  }

  public void setTipoSeguro(String tipoSeguro) {
    this.tipoSeguro = tipoSeguro;
  }

  public ProjectStatus getEstado() {
    return estado;
  }

  public void setEstado(ProjectStatus estado) {
    this.estado = estado;
  }

  public Integer getCurrentStep() {
    return currentStep;
  }

  public void setCurrentStep(Integer currentStep) {
    this.currentStep = currentStep;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
