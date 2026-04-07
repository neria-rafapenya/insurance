package com.insurance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ins_base_prices")
public class BasePrice {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "tipo_seguro", nullable = false)
  private String tipoSeguro;

  @Column(name = "segmento", nullable = false)
  private String segmento;

  @Column(name = "precio_base", nullable = false)
  private Double precioBase;

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

  public String getSegmento() {
    return segmento;
  }

  public void setSegmento(String segmento) {
    this.segmento = segmento;
  }

  public Double getPrecioBase() {
    return precioBase;
  }

  public void setPrecioBase(Double precioBase) {
    this.precioBase = precioBase;
  }
}
