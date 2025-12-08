package fr.lirmm.jdm.client.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response wrapper for relations API calls. */
public class RelationsResponse {

  @JsonProperty("nodes")
  private List<PublicNode> nodes;

  @JsonProperty("relations")
  private List<PublicRelation> relations;

  public List<PublicNode> getNodes() {
    return nodes;
  }

  public void setNodes(List<PublicNode> nodes) {
    this.nodes = nodes;
  }

  public List<PublicRelation> getRelations() {
    return relations;
  }

  public void setRelations(List<PublicRelation> relations) {
    this.relations = relations;
  }

  @Override
  public String toString() {
    return "RelationsResponse{"
        + "nodes="
        + (nodes != null ? nodes.size() : 0)
        + ", relations="
        + (relations != null ? relations.size() : 0)
        + '}';
  }
}
