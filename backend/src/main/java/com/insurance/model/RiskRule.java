package com.insurance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ins_risk_rules")
public class RiskRule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "tipo_seguro", nullable = false)
  private String tipoSeguro;

  @Column(name = "campo", nullable = false)
  private String campo;

  @Column(name = "operador", nullable = false)
  private String operador;

  @Column(name = "valor", nullable = false)
  private String valor;

  @Column(name = "accion", nullable = false)
  private String accion;

  @Column(name = "mensaje", nullable = false)
  private String mensaje;

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

  public String getOperador() {
    return operador;
  }

  public void setOperador(String operador) {
    this.operador = operador;
  }

  public String getValor() {
    return valor;
  }

  public void setValor(String valor) {
    this.valor = valor;
  }

  public String getAccion() {
    return accion;
  }

  public void setAccion(String accion) {
    this.accion = accion;
  }

  public String getMensaje() {
    return mensaje;
  }

  public void setMensaje(String mensaje) {
    this.mensaje = mensaje;
  }
}
