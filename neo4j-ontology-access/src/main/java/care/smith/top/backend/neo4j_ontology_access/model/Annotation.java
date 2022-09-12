package care.smith.top.backend.neo4j_ontology_access.model;

import org.springframework.data.neo4j.core.schema.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Set;

/** Example: (:Annotation:String:Title { value: 'Weight', language: 'en', index: 1 }) */
@Node
public class Annotation extends Annotatable {
  @Id @GeneratedValue private Long id;

  @DynamicLabels private Set<String> dynamicLabels;
  private String datatype;
  private String property;
  private String  stringValue;
  private Instant dateValue;
  private Double numberValue;
  private Boolean booleanValue;

  @Relationship(type = "HAS_CLASS_VALUE")
  private Class classValue;

  private String language;
  private Integer index;

  public Annotation() {}

  public Annotation(String property, String stringValue, String language) {
    this(property, stringValue, language, null);
  }

  public Annotation(String property, String stringValue, String language, Integer index) {
    this(property, language, index);
    this.setDatatype("string");
    this.setStringValue(stringValue);
  }

  public Annotation(String property, Instant instant, String language) {
    this(property, instant, language, null);
  }

  public Annotation(String property, Instant instant, String language, Integer index) {
    this(property, language, index);
    this.setDatatype("date_time");
    this.setDateValue(instant);
  }

  public Annotation(String property, LocalDateTime localDateTime, String language) {
    this(property, localDateTime, language, null);
  }

  public Annotation(String property, LocalDateTime localDateTime, String language, Integer index) {
    this(property, language, index);
    this.setDatatype("date_time");
    this.setDateValue(localDateTime.toInstant(ZoneOffset.UTC));
  }

  public Annotation(String property, Double doubleValue, String language) {
    this(property, doubleValue, language, null);
  }

  public Annotation(String property, Double numberValue, String language, Integer index) {
    this(property, language, index);
    this.setDatatype("number");
    this.setNumberValue(numberValue);
  }

  public Annotation(String property, Boolean booleanValue, String language) {
    this(property, booleanValue, language, null);
  }

  public Annotation(String property, Boolean booleanValue, String language, Integer index) {
    this(property, language, index);
    this.setDatatype("number");
    this.setBooleanValue(booleanValue);
  }

  public Annotation(String property, Class classValue, String language) {
    this(property, classValue, language, null);
  }

  public Annotation(String property, Class classValue, String language, Integer index) {
    this(property, language, index);
    this.setDatatype("class");
    this.setClassValue(classValue);
  }

  private Annotation(String property, String language, Integer index) {
    this.setProperty(property);
    this.language = language;
    this.setIndex(index);
  }

  public Annotation(String property, String datatype) {
    this.setProperty(property);
    this.setDatatype(datatype);
  }

  public Long getId() {
    return id;
  }

  public Object getValue() {
    switch (datatype) {
      case "string":
        return stringValue;
      case "date_time":
        return dateValue;
      case "number":
        return numberValue;
      case "boolean":
        return booleanValue;
      case "class":
        return classValue;
      default:
        return null;
    }
  }

  public String getDatatype() {
    return datatype;
  }

  public Annotation setDatatype(String datatype) {
    this.datatype = datatype;
    return this;
  }

  public Double getNumberValue() {
    return numberValue;
  }

  public Annotation setNumberValue(Double numberValue) {
    this.numberValue = numberValue;
    return this;
  }

  public String getProperty() {
    return property;
  }

  public Annotation setProperty(String property) {
    this.property = property;
    dynamicLabels = Collections.singleton(property);
    return this;
  }

  public String getStringValue() {
    return stringValue;
  }

  public Annotation setStringValue(String stringValue) {
    this.stringValue = stringValue;
    return this;
  }

  public Instant getDateValue() {
    return dateValue;
  }

  public Annotation setDateValue(Instant dateValue) {
    this.dateValue = dateValue;
    return this;
  }

  public String getLanguage() {
    return language;
  }

  public Annotation setLanguage(String language) {
    this.language = language;
    return this;
  }

  public Integer getIndex() {
    return index;
  }

  public Annotation setIndex(Integer index) {
    this.index = index;
    return this;
  }

  public Boolean getBooleanValue() {
    return booleanValue;
  }

  public Annotation setBooleanValue(Boolean booleanValue) {
    this.booleanValue = booleanValue;
    return this;
  }

  public Class getClassValue() {
    return classValue;
  }

  public Annotation setClassValue(Class classValue) {
    this.classValue = classValue;
    return this;
  }

  public Set<String> getDynamicLabels() {
    return dynamicLabels;
  }
}
