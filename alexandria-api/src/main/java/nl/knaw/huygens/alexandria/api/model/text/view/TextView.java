package nl.knaw.huygens.alexandria.api.model.text.view;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TextView {
  private String description = "";

  @JsonProperty("elements")
  private Map<String, ElementView> elementViewMap = new HashMap<>();

  @JsonIgnore
  private UUID textViewDefiningResourceId;

  @JsonIgnore
  private String name;

  public TextView() {
    elementViewMap.clear();
    elementViewMap.put(TextViewDefinition.DEFAULT_ATTRIBUTENAME, ElementView.DEFAULT);
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public void putElementView(String elementName, ElementView elementView) {
    elementViewMap.put(elementName, elementView);
  }

  public Map<String, ElementView> getElementViewMap() {
    return elementViewMap;
  }

  public TextView setName(String name) {
    this.name = name;
    return this;
  }

  public String getName() {
    return this.name;
  }

  public void setTextViewDefiningResourceId(UUID uuid) {
    this.textViewDefiningResourceId = uuid;
  }

  public UUID getTextViewDefiningResourceId() {
    return this.textViewDefiningResourceId;
  }

}
