package care.smith.top.backend.neo4j_ontology_access.model;

import org.springframework.data.annotation.*;
import org.springframework.data.neo4j.core.schema.DynamicLabels;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Directories are used to group up repositories and ontologies. They can represent organisations,
 * projects, etc.
 */
@Node
public class Directory {
  /** Unique string to identify this directory. */
  @Id private final String id;
  /** Version number, autogenerated by Neo4j */
  @Version private Long nodeVersion;

  @CreatedDate private Instant createdAt;
  @CreatedBy private User createdBy;
  @LastModifiedDate private Instant updatedAt;
  @LastModifiedBy private User updatedBy;

  /** Humand-readable name. */
  private String name;
  /** Further descriptions. */
  private String description;
  /** Determins, what this directory is representing. */
  @DynamicLabels
  private Set<String> types;
  /** Custom properties can be stored in this field for instance in JSON format. */
  private String properties;

  /** Directories, where this one is included in. */
  @Relationship(type = "BELONGS_TO")
  private Set<Directory> superDirectories;

  public Directory() {
    id = UUID.randomUUID().toString();
  }

  @PersistenceConstructor
  public Directory(String id) {
    this.id = id;
  }

  public Directory addSuperDirectory(Directory superDirectory) {
    return addSuperDirectories(Collections.singleton(superDirectory));
  }

  public Directory addSuperDirectories(Set<Directory> superDirectories) {
    if (this.superDirectories == null) this.superDirectories = new HashSet<>();
    this.superDirectories.addAll(superDirectories);
    return this;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Directory setName(String name) {
    this.name = name;
    return this;
  }

  public Long getNodeVersion() {
    return nodeVersion;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public User getUpdatedBy() {
    return updatedBy;
  }

  public String getDescription() {
    return description;
  }

  public Directory setDescription(String description) {
    this.description = description;
    return this;
  }

  public Set<String> getTypes() {
    return types;
  }

  public Directory setTypes(Set<String> types) {
    this.types = types;
    return this;
  }

  public String getProperties() {
    return properties;
  }

  public Directory setProperties(String properties) {
    this.properties = properties;
    return this;
  }

  public Set<Directory> getSuperDirectories() {
    return superDirectories;
  }

  public Directory setSuperDirectories(Set<Directory> superDirectories) {
    this.superDirectories = superDirectories;
    return this;
  }
}
