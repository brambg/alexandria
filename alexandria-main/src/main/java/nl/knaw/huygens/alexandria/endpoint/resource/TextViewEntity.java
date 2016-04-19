package nl.knaw.huygens.alexandria.endpoint.resource;

import java.net.URI;
import java.util.UUID;

import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import nl.knaw.huygens.alexandria.api.model.Entity;
import nl.knaw.huygens.alexandria.api.model.JsonWrapperObject;
import nl.knaw.huygens.alexandria.api.model.PropertyPrefix;
import nl.knaw.huygens.alexandria.endpoint.LocationBuilder;
import nl.knaw.huygens.alexandria.model.AlexandriaResource;

@JsonTypeName("resourceTextView")
@JsonInclude(Include.NON_NULL)
public class TextViewEntity extends JsonWrapperObject implements Entity {
  @JsonIgnore
  private String viewId;
  @JsonIgnore
  private LocationBuilder locationBuilder;
  @JsonIgnore
  private UUID resourceId;

  public TextViewEntity(UUID resourceId, String viewId, LocationBuilder locationBuilder) {
    this.resourceId = resourceId;
    this.viewId = viewId;
    this.locationBuilder = locationBuilder;
  }

  public String getId() {
    return viewId;
  }

  @JsonProperty(PropertyPrefix.LINK + "xml")
  public URI getXml() {
    URI uri = locationBuilder.locationOf(AlexandriaResource.class, resourceId, "text", "xml");
    return UriBuilder.fromUri(uri).queryParam("view", viewId).build();
  }

  @JsonProperty(PropertyPrefix.LINK + "definition")
  public URI getDefinition() {
    return locationBuilder.locationOf(AlexandriaResource.class, resourceId, "text", "views", viewId);
  }

}
