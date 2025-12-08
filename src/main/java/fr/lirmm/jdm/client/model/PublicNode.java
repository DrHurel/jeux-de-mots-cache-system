package fr.lirmm.jdm.client.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a node in the JDM (Jeux de Mots) semantic network.
 *
 * <p>A node represents a word, concept, or term in the lexical-semantic network with associated
 * metadata and relationships.
 */
public class PublicNode {

  @JsonProperty("id")
  private Integer id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("type")
  private Integer type;

  @JsonProperty("w")
  private Integer weight;

  @JsonProperty("c")
  private Integer c;

  @JsonProperty("level")
  private Double level;

  @JsonProperty("infoid")
  private Integer infoId;

  @JsonProperty("creationdate")
  private LocalDate creationDate;

  @JsonProperty("touchdate")
  private LocalDateTime touchDate;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getType() {
    return type;
  }

  public void setType(Integer type) {
    this.type = type;
  }

  public Integer getWeight() {
    return weight;
  }

  public void setWeight(Integer weight) {
    this.weight = weight;
  }

  public Integer getC() {
    return c;
  }

  public void setC(Integer c) {
    this.c = c;
  }

  public Double getLevel() {
    return level;
  }

  public void setLevel(Double level) {
    this.level = level;
  }

  public Integer getInfoId() {
    return infoId;
  }

  public void setInfoId(Integer infoId) {
    this.infoId = infoId;
  }

  public LocalDate getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(LocalDate creationDate) {
    this.creationDate = creationDate;
  }

  public LocalDateTime getTouchDate() {
    return touchDate;
  }

  public void setTouchDate(LocalDateTime touchDate) {
    this.touchDate = touchDate;
  }

  @Override
  public String toString() {
    return "PublicNode{"
        + "id="
        + id
        + ", name='"
        + name
        + '\''
        + ", type="
        + type
        + ", weight="
        + weight
        + '}';
  }
}
