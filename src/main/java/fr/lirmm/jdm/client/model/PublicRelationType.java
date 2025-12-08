package fr.lirmm.jdm.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Represents a relation type in the JDM network. */
public class PublicRelationType {

  @JsonProperty("id")
  private Integer id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("gpname")
  private String groupName;

  @JsonProperty("quot")
  private Double quot;

  @JsonProperty("quotmin")
  private Double quotMin;

  @JsonProperty("quotmax")
  private Double quotMax;

  @JsonProperty("price")
  private Integer price;

  @JsonProperty("help")
  private String help;

  @JsonProperty("playable")
  private Integer playable;

  @JsonProperty("oppos")
  private Integer opposite;

  @JsonProperty("posyes")
  private String posYes;

  @JsonProperty("posno")
  private String posNo;

  @JsonProperty("constraint_ent")
  private Integer constraintEnt;

  @JsonProperty("constraints_start")
  private String constraintsStart;

  @JsonProperty("constraints_end")
  private String constraintsEnd;

  @JsonProperty("carac")
  private String characteristics;

  // Getters and setters
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

  public String getGroupName() {
    return groupName;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  public Double getQuot() {
    return quot;
  }

  public void setQuot(Double quot) {
    this.quot = quot;
  }

  public Double getQuotMin() {
    return quotMin;
  }

  public void setQuotMin(Double quotMin) {
    this.quotMin = quotMin;
  }

  public Double getQuotMax() {
    return quotMax;
  }

  public void setQuotMax(Double quotMax) {
    this.quotMax = quotMax;
  }

  public Integer getPrice() {
    return price;
  }

  public void setPrice(Integer price) {
    this.price = price;
  }

  public String getHelp() {
    return help;
  }

  public void setHelp(String help) {
    this.help = help;
  }

  public Integer getPlayable() {
    return playable;
  }

  public void setPlayable(Integer playable) {
    this.playable = playable;
  }

  public Integer getOpposite() {
    return opposite;
  }

  public void setOpposite(Integer opposite) {
    this.opposite = opposite;
  }

  public String getPosYes() {
    return posYes;
  }

  public void setPosYes(String posYes) {
    this.posYes = posYes;
  }

  public String getPosNo() {
    return posNo;
  }

  public void setPosNo(String posNo) {
    this.posNo = posNo;
  }

  public Integer getConstraintEnt() {
    return constraintEnt;
  }

  public void setConstraintEnt(Integer constraintEnt) {
    this.constraintEnt = constraintEnt;
  }

  public String getConstraintsStart() {
    return constraintsStart;
  }

  public void setConstraintsStart(String constraintsStart) {
    this.constraintsStart = constraintsStart;
  }

  public String getConstraintsEnd() {
    return constraintsEnd;
  }

  public void setConstraintsEnd(String constraintsEnd) {
    this.constraintsEnd = constraintsEnd;
  }

  public String getCharacteristics() {
    return characteristics;
  }

  public void setCharacteristics(String characteristics) {
    this.characteristics = characteristics;
  }

  @Override
  public String toString() {
    return "PublicRelationType{" + "id=" + id + ", name='" + name + '\'' + ", help='" + help + '\'' + '}';
  }
}
