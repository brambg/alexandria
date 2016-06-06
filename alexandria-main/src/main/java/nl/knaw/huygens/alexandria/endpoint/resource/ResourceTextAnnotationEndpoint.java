package nl.knaw.huygens.alexandria.endpoint.resource;

/*
 * #%L
 * alexandria-main
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.StringUtils;

import nl.knaw.huygens.Log;
import nl.knaw.huygens.alexandria.api.EndpointPaths;
import nl.knaw.huygens.alexandria.api.model.Annotator;
import nl.knaw.huygens.alexandria.api.model.text.TextRangeAnnotation;
import nl.knaw.huygens.alexandria.api.model.text.TextRangeAnnotation.Position;
import nl.knaw.huygens.alexandria.api.model.text.TextRangeAnnotationInfo;
import nl.knaw.huygens.alexandria.endpoint.JSONEndpoint;
import nl.knaw.huygens.alexandria.endpoint.LocationBuilder;
import nl.knaw.huygens.alexandria.endpoint.UUIDParam;
import nl.knaw.huygens.alexandria.exception.ConflictException;
import nl.knaw.huygens.alexandria.exception.NotFoundException;
import nl.knaw.huygens.alexandria.model.AlexandriaResource;
import nl.knaw.huygens.alexandria.service.AlexandriaService;
import nl.knaw.huygens.alexandria.textgraph.TextGraphUtil;
import nl.knaw.huygens.alexandria.util.XMLUtil;
import nl.knaw.huygens.tei.QueryableDocument;

public class ResourceTextAnnotationEndpoint extends JSONEndpoint {

  private LocationBuilder locationBuilder;
  private AlexandriaService service;
  private AlexandriaResource resource;
  private UUID resourceUUID;

  @Inject
  public ResourceTextAnnotationEndpoint(AlexandriaService service, //
      ResourceValidatorFactory validatorFactory, //
      LocationBuilder locationBuilder, //
      @PathParam("uuid") final UUIDParam uuidParam) {
    this.service = service;
    this.locationBuilder = locationBuilder;
    this.resource = validatorFactory.validateExistingResource(uuidParam).notTentative().hasText().get();
    this.resourceUUID = resource.getId();
  }

  @PUT
  @Path("{annotationUUID}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response setAnnotation(//
      @PathParam("annotationUUID") final UUIDParam uuidParam, //
      @NotNull TextRangeAnnotation textRangeAnnotation//
  ) {
    textRangeAnnotation.setId(uuidParam.getValue());
    String xml = getXML();
    String annotated = validateTextRangeAnnotation(textRangeAnnotation, xml);

    service.setTextRangeAnnotation(resourceUUID, textRangeAnnotation);
    URI location = locationBuilder.locationOf(resource, EndpointPaths.TEXT, EndpointPaths.ANNOTATIONS, uuidParam.getValue().toString());
    TextRangeAnnotationInfo info = new TextRangeAnnotationInfo().setAnnotates(annotated);
    return Response.created(location).entity(info).build();
  }

  @GET
  @Path("{annotationUUID}")
  public Response getAnnotation(@PathParam("annotationUUID") final UUIDParam uuidParam) {
    UUID annotationUUID = uuidParam.getValue();
    TextRangeAnnotation textRangeAnnotation = service.readTextRangeAnnotation(resourceUUID, annotationUUID)//
        .orElseThrow(() -> new NotFoundException("No annotation found for this resource with id " + annotationUUID));
    return ok(textRangeAnnotation);
  }

  private String getXML() {
    StreamingOutput outputStream = TextGraphUtil.streamXML(service, resourceUUID);
    return TextGraphUtil.asString(outputStream);
  }

  private String validateTextRangeAnnotation(TextRangeAnnotation textAnnotation, String xml) {
    String annotated = validatePosition(textAnnotation.getPosition(), xml);
    validateName(textAnnotation.getName());
    validateAnnotator(textAnnotation.getAnnotator());
    if (service.overlapsWithExisitingTextRangeAnnotationForResource(textAnnotation, resourceUUID)) {
      throw new ConflictException("Overlapping annotations with the same name and responsibility.");
    }
    return annotated;
  }

  private void validateAnnotator(String annotator) {
    if (StringUtils.isEmpty(annotator)) {
      throw new BadRequestException("No annotator specified.");
    }
    Optional<String> validAnnotator = service.readResourceAnnotators(resourceUUID).parallelStream()//
        .map(Annotator::getCode)//
        .filter(annotator::equals)//
        .findAny();
    if (!validAnnotator.isPresent()) {
      throw new BadRequestException("Resource has no annotator with code '" + annotator + "'.");
    }
  }

  private String validatePosition(Position position, String xml) {
    QueryableDocument qDocument = QueryableDocument.createFromXml(xml, true);
    validate(qDocument, //
        "count(//*[@xml:id='" + position.getXmlId() + "'])", //
        1d, //
        "The text does not contain an element with the specified xml:id."//
    );
    String xpath = "substring(//*[@xml:id='" + position.getXmlId() + "']," + position.getOffset() + "," + position.getLength() + ")";
    validate(qDocument, //
        "string-length(" + xpath + ")", //
        new Double(position.getLength()), //
        "The specified offset/length is illegal."//
    );

    String annotated = "";
    try {
      annotated = qDocument.evaluateXPathToString(xpath);
    } catch (XPathExpressionException e) {
      e.printStackTrace();
    }

    return annotated;
  }

  private void validate(QueryableDocument qDocument, String xpath, Double expectation, String errorMessage) {
    Log.info("xpath = '{}'", xpath);
    try {
      Double evaluation = qDocument.evaluateXPathToDouble(xpath);
      Log.info("evaluation = {}", evaluation);
      if (!evaluation.equals(expectation)) {
        throw new BadRequestException(errorMessage);
      }

    } catch (XPathExpressionException e) {
      e.printStackTrace();
      throw new BadRequestException(errorMessage);
    }
  }

  private void validateName(String elementName) {
    List<String> validationErrors = XMLUtil.validateElementName(elementName);
    if (!validationErrors.isEmpty()) {
      throw new BadRequestException("The specified annotation name is illegal.");
    }

  }

}
