package nl.knaw.huygens.alexandria.api.model.text;

/*
 * #%L
 * alexandria-api
 * =======
 * Copyright (C) 2015 - 2016 Huygens ING (KNAW)
 * =======
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import nl.knaw.huygens.alexandria.api.JsonTypeNames;
import nl.knaw.huygens.alexandria.api.model.JsonWrapperObject;

@JsonTypeName(JsonTypeNames.TEXTANNOTATION)
@JsonInclude(Include.NON_NULL)
public class TextRangeAnnotation extends JsonWrapperObject {
  public static final String RESPONSIBILITY_ATTRIBUTE = "resp";

  public static class Position {
    @JsonProperty("xml:id")
    String xmlId;

    Integer offset;
    Integer length;

    public String getXmlId() {
      return xmlId;
    }

    public Position setXmlId(String xmlId) {
      this.xmlId = xmlId;
      return this;
    }

    public Integer getOffset() {
      return offset;
    }

    public Position setOffset(Integer offset) {
      this.offset = offset;
      return this;
    }

    public Integer getLength() {
      return length;
    }

    public Position setLength(Integer length) {
      this.length = length;
      return this;
    }
  }

  @JsonProperty("id")
  private UUID uuid;

  private String name;
  private String annotator;
  private Position position;

  public TextRangeAnnotation setId(UUID uuid) {
    this.uuid = uuid;
    return this;
  }

  public UUID getId() {
    return uuid;
  }

  public TextRangeAnnotation setName(String name) {
    this.name = name;
    return this;
  }

  public String getName() {
    return name;
  }

  public TextRangeAnnotation setAnnotator(String annotator) {
    this.annotator = annotator;
    return this;
  }

  public String getAnnotator() {
    return annotator;
  }

  public TextRangeAnnotation setPosition(Position position) {
    this.position = position;
    return this;
  }

  public Position getPosition() {
    return position;
  }

}
