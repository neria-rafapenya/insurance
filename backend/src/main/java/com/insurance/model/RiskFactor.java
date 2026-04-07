package com.insurance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ins_risk_factors")
public class RiskFactor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "tipo_seguro")
  private String tipoSeguro;

  @Column(name = "campo", nullable = false)
  private String campo;

  @Column(name = "fuente", nullable = false)
  private String fuente;

  @Column(name = "tipo_match", nullable = false)
  private String tipoMatch;

  @Column(name = "valor_match", nullable = false, columnDefinition = "TEXT")
  private String valorMatch;

  @Column(name = "valor_resultado")
  private String valorResultado;

  @Column(name = "prioridad")
  private Integer prioridad;

  @Column(name = "activo")
  private Boolean activo;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getTipoSeguro() {
    return tipoSeguro;
  }

  public void setTipoSeguro(String tipoSeguro) {
    this.tipoSeguro = tipoSeguro;
  }

  public String getCampo() {
    return campo;
  }

  public void setCampo(String campo) {
    this.campo = campo;
  }

  public String getFuente() {
    return fuente;
  }

  public void setFuente(String fuente) {
    this.fuente = fuente;
  }

  public String getTipoMatch() {
    return tipoMatch;
  }

  public void setTipoMatch(String tipoMatch) {
    this.tipoMatch = tipoMatch;
  }

  public String getValorMatch() {
    return valorMatch;
  }

  public void setValorMatch(String valorMatch) {
    this.valorMatch = valorMatch;
  }

  public String getValorResultado() {
    return valorResultado;
  }

  public void setValorResultado(String valorResultado) {
    this.valorResultado = valorResultado;
  }

  public Integer getPrioridad() {
    return prioridad;
  }

  public void setPrioridad(Integer prioridad) {
    this.prioridad = prioridad;
  }

  public Boolean getActivo() {
    return activo;
  }

  public void setActivo(Boolean activo) {
    this.activo = activo;
  }
}
