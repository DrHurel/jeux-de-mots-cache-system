package fr.lirmm.jdm.client.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a semantic relation between two nodes in the JDM network.
 *
 * <p>Relations define how words/concepts are connected (synonymy, hypernymy, association, etc.).
 */
public class PublicRelation {

  @JsonProperty("id")
  private Integer id;

  @JsonProperty("node1")
  private Integer node1;

  @JsonProperty("node2")
  private Integer node2;

  @JsonProperty("type")
  private Integer type;

  @JsonProperty("w")
  private Double weight;

  @JsonProperty("c")
  private Double c;

  @JsonProperty("infoid")
  private Integer infoId;

  @JsonProperty("creationdate")
  private LocalDate creationDate;

  @JsonProperty("touchdate")
  private LocalDateTime touchDate;

  @JsonProperty("nw")
  private Double normalizedWeight;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getNode1() {
    return node1;
  }

  public void setNode1(Integer node1) {
    this.node1 = node1;
  }

  public Integer getNode2() {
    return node2;
  }

  public void setNode2(Integer node2) {
    this.node2 = node2;
  }

  public Integer getType() {
    return type;
  }

  public void setType(Integer type) {
    this.type = type;
  }

  public Double getWeight() {
    return weight;
  }

  public void setWeight(Double weight) {
    this.weight = weight;
  }

  public Double getC() {
    return c;
  }

  public void setC(Double c) {
    this.c = c;
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

  public Double getNormalizedWeight() {
    return normalizedWeight;
  }

  public void setNormalizedWeight(Double normalizedWeight) {
    this.normalizedWeight = normalizedWeight;
  }

  @Override
  public String toString() {
    return "PublicRelation{"
        + "id="
        + id
        + ", node1="
        + node1
        + ", node2="
        + node2
        + ", type="
        + type
        + ", weight="
        + weight
        + '}';
  }
}
