package fr.lirmm.jdm.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Represents a node type in the JDM network. */
public class PublicNodeType {

  @JsonProperty("id")
  private Integer id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("help")
  private String help;

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

  public String getHelp() {
    return help;
  }

  public void setHelp(String help) {
    this.help = help;
  }

  @Override
  public String toString() {
    return "PublicNodeType{" + "id=" + id + ", name='" + name + '\'' + ", help='" + help + '\'' + '}';
  }
}
