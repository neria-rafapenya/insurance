package com.insurance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ins_coverages")
public class Coverage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "tipo_seguro", nullable = false)
  private String tipoSeguro;

  @Column(name = "nombre", nullable = false)
  private String nombre;

  @Column(name = "descripcion", columnDefinition = "LONGTEXT")
  private String descripcion;

  @Column(name = "incluido", nullable = false)
  private Boolean incluido;

  @Column(name = "precio_extra", nullable = false)
  private String precioExtra;

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

  public String getNombre() {
    return nombre;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public void setDescripcion(String descripcion) {
    this.descripcion = descripcion;
  }

  public Boolean getIncluido() {
    return incluido;
  }

  public void setIncluido(Boolean incluido) {
    this.incluido = incluido;
  }

  public String getPrecioExtra() {
    return precioExtra;
  }

  public void setPrecioExtra(String precioExtra) {
    this.precioExtra = precioExtra;
  }
}
