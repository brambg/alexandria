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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.DurationSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import com.google.common.collect.Lists;

import nl.knaw.huygens.alexandria.api.JsonTypeNames;
import nl.knaw.huygens.alexandria.api.model.Entity;
import nl.knaw.huygens.alexandria.api.model.JsonWrapperObject;
import nl.knaw.huygens.alexandria.api.model.PropertyPrefix;

@JsonTypeName(JsonTypeNames.TEXTIMPORTSTATUS)
@JsonInclude(Include.NON_NULL)
public class TextImportStatus extends JsonWrapperObject implements Entity {
  enum State {
    waiting, processing, done
  }

  private boolean done = false;
  private State state = State.waiting;
  private Duration processingTime;
  private Instant startTime;
  private List<String> validationErrors = Lists.newArrayList();
  private URI textURI;
  private Instant expires;

  public State getState() {
    return state;
  }

  public void setStarted() {
    this.state = State.processing;
    this.startTime = Instant.now();
  }

  public void setDone() {
    this.done = true;
    this.state = State.done;
    this.processingTime = Duration.between(startTime, Instant.now());
    this.expires = Instant.now().plus(1l, ChronoUnit.HOURS);
  }

  public boolean isDone() {
    return done;
  }

  @JsonIgnore
  public boolean isExpired() {
    return expires != null && Instant.now().isAfter(expires);
  }

  public void setTextURI(URI textURI) {
    this.textURI = textURI;
  }

  @JsonProperty(PropertyPrefix.LINK + "xml")
  public URI getTextURI() {
    return textURI;
  }

  public List<String> getValidationErrors() {
    return validationErrors;
  }

  @JsonSerialize(using = InstantSerializer.class)
  public Instant getExpires() {
    return expires;
  }

  @JsonSerialize(using = InstantSerializer.class)
  public Instant getStartTime() {
    return startTime;
  }

  @JsonSerialize(using = DurationSerializer.class)
  public Duration getProcessingTime() {
    return processingTime;
  }

}
