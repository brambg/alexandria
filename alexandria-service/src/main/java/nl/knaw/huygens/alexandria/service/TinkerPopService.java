package nl.knaw.huygens.alexandria.service;

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import nl.knaw.huygens.alexandria.endpoint.search.AlexandriaQuery;
import nl.knaw.huygens.alexandria.endpoint.search.SearchResult;
import nl.knaw.huygens.alexandria.exception.BadRequestException;
import nl.knaw.huygens.alexandria.exception.NotFoundException;
import nl.knaw.huygens.alexandria.model.Accountable;
import nl.knaw.huygens.alexandria.model.AlexandriaAnnotation;
import nl.knaw.huygens.alexandria.model.AlexandriaAnnotationBody;
import nl.knaw.huygens.alexandria.model.AlexandriaProvenance;
import nl.knaw.huygens.alexandria.model.AlexandriaResource;
import nl.knaw.huygens.alexandria.model.AlexandriaState;
import nl.knaw.huygens.alexandria.model.IdentifiablePointer;
import nl.knaw.huygens.alexandria.model.TentativeAlexandriaProvenance;
import nl.knaw.huygens.alexandria.storage.Storage;
import nl.knaw.huygens.alexandria.storage.frames.AlexandriaVF;
import nl.knaw.huygens.alexandria.storage.frames.AnnotationBodyVF;
import nl.knaw.huygens.alexandria.storage.frames.AnnotationVF;
import nl.knaw.huygens.alexandria.storage.frames.ResourceVF;
import scala.NotImplementedError;

public class TinkerPopService implements AlexandriaService {
  private static final TemporalAmount TIMEOUT = Duration.ofDays(1);

  private Storage storage;

  public TinkerPopService(Storage storage) {
    setStorage(storage);
  }

  public void setStorage(Storage storage) {
    this.storage = storage;
  }

  // - AlexandriaService methods -//

  @Override
  public boolean createOrUpdateResource(UUID uuid, String ref, TentativeAlexandriaProvenance provenance, AlexandriaState state) {
    storage.startTransaction();
    AlexandriaResource resource;
    boolean newlyCreated;

    if (storage.existsVF(ResourceVF.class, uuid)) {
      resource = readResource(uuid).get();
      newlyCreated = false;

    } else {
      resource = new AlexandriaResource(uuid, provenance);
      newlyCreated = true;
    }
    resource.setCargo(ref);
    resource.setState(state);
    createOrUpdateResource(resource);
    storage.commitTransaction();
    return newlyCreated;
  }

  @Override
  public AlexandriaAnnotation annotate(AlexandriaResource resource, AlexandriaAnnotationBody annotationbody, TentativeAlexandriaProvenance provenance) {
    AlexandriaAnnotation newAnnotation = createAnnotation(annotationbody, provenance);
    annotateResourceWithAnnotation(resource, newAnnotation);
    return newAnnotation;
  }

  @Override
  public AlexandriaAnnotation annotate(AlexandriaAnnotation annotation, AlexandriaAnnotationBody annotationbody, TentativeAlexandriaProvenance provenance) {
    AlexandriaAnnotation newAnnotation = createAnnotation(annotationbody, provenance);
    annotateAnnotationWithAnnotation(annotation, newAnnotation);
    return newAnnotation;
  }

  @Override
  public AlexandriaResource createSubResource(UUID uuid, UUID parentUuid, String sub, TentativeAlexandriaProvenance provenance, AlexandriaState state) {
    AlexandriaResource subresource = new AlexandriaResource(uuid, provenance);
    subresource.setCargo(sub);
    subresource.setParentResourcePointer(new IdentifiablePointer<AlexandriaResource>(AlexandriaResource.class, parentUuid.toString()));
    createSubResource(subresource);
    return subresource;
  }

  @Override
  public Optional<? extends Accountable> dereference(IdentifiablePointer<? extends Accountable> pointer) {
    Class<? extends Accountable> aClass = pointer.getIdentifiableClass();
    UUID uuid = UUID.fromString(pointer.getIdentifier());
    if (AlexandriaResource.class.equals(aClass)) {
      return readResource(uuid);

    } else if (AlexandriaAnnotation.class.equals(aClass)) {
      return readAnnotation(uuid);

    } else {
      throw new RuntimeException("unexpected accountableClass: " + aClass.getName());
    }
  }

  @Override
  public Optional<AlexandriaResource> readResource(UUID uuid) {
    return storage.readVF(ResourceVF.class, uuid).map(this::deframeResource);
  }

  @Override
  public Optional<AlexandriaAnnotation> readAnnotation(UUID uuid) {
    return storage.readVF(AnnotationVF.class, uuid).map(this::deframeAnnotation);
  }

  @Override
  public void removeExpiredTentatives() {
    // Tentative vertices should not have any outgoing or incoming edges!!
    Long threshold = Instant.now().minus(TIMEOUT).getEpochSecond();
    storage.startTransaction();
    storage.removeExpiredTentatives(threshold);
    storage.commitTransaction();
  }

  @Override
  public Optional<AlexandriaAnnotationBody> findAnnotationBodyWithTypeAndValue(String type, String value) {
    final List<AnnotationBodyVF> results = storage.find(AnnotationBodyVF.class)//
        .has("type", type)//
        .has("value", value)//
        .toList();
    if (results.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(deframeAnnotationBody(results.get(0)));
  }

  @Override
  public Set<AlexandriaResource> readSubResources(UUID uuid) {
    ResourceVF resourcevf = storage.readVF(ResourceVF.class, uuid)//
        .orElseThrow(() -> new NotFoundException("no resource found with uuid " + uuid));
    return resourcevf.getSubResources().stream()//
        .map(this::deframeResource)//
        .collect(toSet());
  }

  @Override
  public AlexandriaAnnotation deprecateAnnotation(UUID oldAnnotationId, AlexandriaAnnotation tmpAnnotation) {
    storage.startTransaction();

    // check if there's an annotation with the given id
    AnnotationVF oldAnnotationVF = storage.readVF(AnnotationVF.class, oldAnnotationId)//
        .orElseThrow(annotationNotFound(oldAnnotationId));
    if (oldAnnotationVF.isTentative()) {
      throw incorrectStateException(oldAnnotationId, "tentative");
    } else if (oldAnnotationVF.isDeleted()) {
      throw new BadRequestException("annotation " + oldAnnotationId + " is " + "deleted");
    } else if (oldAnnotationVF.isDeprecated()) {
      throw new BadRequestException("annotation " + oldAnnotationId + " is " + "already deprecated");
    }

    AlexandriaAnnotationBody newBody = tmpAnnotation.getBody();
    Optional<AlexandriaAnnotationBody> optionalBody = findAnnotationBodyWithTypeAndValue(newBody.getType(), newBody.getValue());
    AlexandriaAnnotationBody body;
    if (optionalBody.isPresent()) {
      body = optionalBody.get();
    } else {
      frameAnnotationBody(newBody);
      body = newBody;
    }

    AlexandriaProvenance tmpProvenance = tmpAnnotation.getProvenance();
    TentativeAlexandriaProvenance provenance = new TentativeAlexandriaProvenance(tmpProvenance.getWho(), tmpProvenance.getWhen(), tmpProvenance.getWhy());
    AlexandriaAnnotation newAnnotation = new AlexandriaAnnotation(tmpAnnotation.getId(), body, provenance);
    AnnotationVF newAnnotationVF = createAnnotationVF(newAnnotation);

    AnnotationVF annotatedAnnotation = oldAnnotationVF.getAnnotatedAnnotation();
    if (annotatedAnnotation != null) {
      newAnnotationVF.setAnnotatedAnnotation(annotatedAnnotation);
    } else {
      ResourceVF annotatedResource = oldAnnotationVF.getAnnotatedResource();
      newAnnotationVF.setAnnotatedResource(annotatedResource);
    }
    newAnnotationVF.setDeprecatedAnnotation(oldAnnotationVF);

    AlexandriaAnnotation resultAnnotation = deframeAnnotation(newAnnotationVF);
    storage.commitTransaction();
    return resultAnnotation;
  }

  @Override
  public void confirmAnnotation(UUID uuid) {
    storage.startTransaction();
    AnnotationVF annotationVF = storage.readVF(AnnotationVF.class, uuid)//
        .orElseThrow(annotationNotFound(uuid));
    updateState(annotationVF, AlexandriaState.CONFIRMED);
    updateState(annotationVF.getBody(), AlexandriaState.CONFIRMED);
    AnnotationVF deprecatedAnnotation = annotationVF.getDeprecatedAnnotation();
    if (deprecatedAnnotation != null && !deprecatedAnnotation.isDeprecated()) {
      updateState(deprecatedAnnotation, AlexandriaState.DEPRECATED);
    }
    storage.commitTransaction();
  }

  @Override
  public void deleteAnnotation(AlexandriaAnnotation annotation) {
    storage.startTransaction();
    UUID uuid = annotation.getId();
    AnnotationVF annotationVF = storage.readVF(AnnotationVF.class, uuid).get();
    if (annotation.isTentative()) {
      // remove from database

      AnnotationBodyVF body = annotationVF.getBody();
      List<AnnotationVF> ofAnnotations = body.getOfAnnotationList();
      if (ofAnnotations.size() == 1) {
        String annotationBodyId = body.getUuid().toString();
        storage.removeVertexWithId(annotationBodyId);
      }

      // remove has_body edge
      annotationVF.setBody(null);

      // remove annotates edge
      annotationVF.setAnnotatedAnnotation(null);
      annotationVF.setAnnotatedResource(null);

      String annotationId = uuid.toString();
      storage.removeVertexWithId(annotationId);

    } else {
      // set state
      updateState(annotationVF, AlexandriaState.DELETED);
    }

    storage.commitTransaction();
  }

  @Override
  public AlexandriaAnnotationBody createAnnotationBody(UUID uuid, String type, String value, TentativeAlexandriaProvenance provenance, AlexandriaState state) {
    AlexandriaAnnotationBody body = new AlexandriaAnnotationBody(uuid, type, value, provenance);
    storeAnnotationBody(body);
    return body;
  }

  @Override
  public Optional<AlexandriaAnnotationBody> readAnnotationBody(UUID uuid) {
    throw new NotImplementedError();
  }

  @Override
  public SearchResult execute(AlexandriaQuery query) {
    SearchResult searchResult = new SearchResult();
    searchResult.setId(UUID.randomUUID());
    searchResult.setQuery(query);
    return searchResult;
  }

  // - other public methods -//

  public void createSubResource(AlexandriaResource subResource) {
    storage.startTransaction();

    final ResourceVF rvf;
    final UUID uuid = subResource.getId();
    if (storage.existsVF(ResourceVF.class, uuid)) {
      rvf = storage.readVF(ResourceVF.class, uuid).get();
    } else {
      rvf = storage.createVF(ResourceVF.class);
      rvf.setUuid(uuid.toString());
    }

    rvf.setCargo(subResource.getCargo());
    final UUID parentId = UUID.fromString(subResource.getParentResourcePointer().get().getIdentifier());
    Optional<ResourceVF> parentVF = storage.readVF(ResourceVF.class, parentId);
    rvf.setParentResource(parentVF.get());

    setAlexandriaVFProperties(rvf, subResource);

    storage.commitTransaction();
  }

  public void createOrUpdateAnnotation(AlexandriaAnnotation annotation) {
    storage.startTransaction();

    final AnnotationVF avf;
    final UUID uuid = annotation.getId();
    if (storage.existsVF(AnnotationVF.class, uuid)) {
      avf = storage.readVF(AnnotationVF.class, uuid).get();
    } else {
      avf = storage.createVF(AnnotationVF.class);
      avf.setUuid(uuid.toString());
    }

    setAlexandriaVFProperties(avf, annotation);

    storage.commitTransaction();
  }

  public void annotateResourceWithAnnotation(AlexandriaResource resource, AlexandriaAnnotation newAnnotation) {
    storage.startTransaction();

    AnnotationVF avf = createAnnotationVF(newAnnotation);

    ResourceVF resourceToAnnotate = storage.readVF(ResourceVF.class, resource.getId()).get();
    avf.setAnnotatedResource(resourceToAnnotate);

    storage.commitTransaction();
  }

  public void storeAnnotationBody(AlexandriaAnnotationBody body) {
    storage.startTransaction();
    frameAnnotationBody(body);
    storage.commitTransaction();
  }

  public void annotateAnnotationWithAnnotation(AlexandriaAnnotation annotation, AlexandriaAnnotation newAnnotation) {
    storage.startTransaction();

    AnnotationVF avf = createAnnotationVF(newAnnotation);
    UUID id = annotation.getId();
    annotate(avf, id);

    storage.commitTransaction();
  }

  public void dumpToGraphSON(OutputStream os) throws IOException {
    storage.dumpToGraphSON(os);
  }

  public void dumpToGraphML(OutputStream os) throws IOException {
    storage.dumpToGraphML(os);
  }

  // - package methods -//

  void createOrUpdateResource(AlexandriaResource resource) {
    final ResourceVF rvf;
    final UUID uuid = resource.getId();
    if (storage.existsVF(ResourceVF.class, uuid)) {
      rvf = storage.readVF(ResourceVF.class, uuid).get();
    } else {
      rvf = storage.createVF(ResourceVF.class);
      rvf.setUuid(uuid.toString());
    }

    rvf.setCargo(resource.getCargo());

    setAlexandriaVFProperties(rvf, resource);
  }

  // - private methods -//

  private AlexandriaAnnotation createAnnotation(AlexandriaAnnotationBody annotationbody, TentativeAlexandriaProvenance provenance) {
    UUID id = UUID.randomUUID();
    return new AlexandriaAnnotation(id, annotationbody, provenance);
  }

  AnnotationVF createAnnotationVF(AlexandriaAnnotation newAnnotation) {
    AnnotationVF avf = storage.createVF(AnnotationVF.class);
    setAlexandriaVFProperties(avf, newAnnotation);

    UUID bodyId = newAnnotation.getBody().getId();
    AnnotationBodyVF bodyVF = storage.readVF(AnnotationBodyVF.class, bodyId).get();
    avf.setBody(bodyVF);
    return avf;
  }

  private AlexandriaResource deframeResource(ResourceVF rvf) {
    TentativeAlexandriaProvenance provenance = deframeProvenance(rvf);
    UUID uuid = getUUID(rvf);
    AlexandriaResource resource = new AlexandriaResource(uuid, provenance);
    resource.setCargo(rvf.getCargo());
    resource.setState(AlexandriaState.valueOf(rvf.getState()));
    resource.setStateSince(Instant.ofEpochSecond(rvf.getStateSince()));
    for (AnnotationVF annotationVF : rvf.getAnnotatedBy()) {
      AlexandriaAnnotation annotation = deframeAnnotation(annotationVF);
      resource.addAnnotation(annotation);
    }
    ResourceVF parentResource = rvf.getParentResource();
    if (parentResource != null) {
      resource.setParentResourcePointer(new IdentifiablePointer<>(AlexandriaResource.class, parentResource.getUuid()));
    }
    rvf.getSubResources().stream()//
        .forEach(vf -> resource.addSubResourcePointer(new IdentifiablePointer<>(AlexandriaResource.class, vf.getUuid())));
    return resource;
  }

  private AlexandriaAnnotation deframeAnnotation(AnnotationVF annotationVF) {
    TentativeAlexandriaProvenance provenance = deframeProvenance(annotationVF);
    UUID uuid = getUUID(annotationVF);
    AlexandriaAnnotationBody body = deframeAnnotationBody(annotationVF.getBody());
    AlexandriaAnnotation annotation = new AlexandriaAnnotation(uuid, body, provenance);
    annotation.setState(AlexandriaState.valueOf(annotationVF.getState()));
    annotation.setStateSince(Instant.ofEpochSecond(annotationVF.getStateSince()));
    AnnotationVF annotatedAnnotation = annotationVF.getAnnotatedAnnotation();
    if (annotatedAnnotation == null) {
      ResourceVF annotatedResource = annotationVF.getAnnotatedResource();
      annotation.setAnnotatablePointer(new IdentifiablePointer<>(AlexandriaResource.class, annotatedResource.getUuid()));
    } else {
      annotation.setAnnotatablePointer(new IdentifiablePointer<>(AlexandriaAnnotation.class, annotatedAnnotation.getUuid()));
    }
    for (AnnotationVF avf : annotationVF.getAnnotatedBy()) {
      AlexandriaAnnotation annotationAnnotation = deframeAnnotation(avf);
      annotation.addAnnotation(annotationAnnotation);
    }
    return annotation;
  }

  private AnnotationBodyVF frameAnnotationBody(AlexandriaAnnotationBody body) {
    AnnotationBodyVF abvf = storage.createVF(AnnotationBodyVF.class);
    setAlexandriaVFProperties(abvf, body);
    abvf.setType(body.getType());
    abvf.setValue(body.getValue());
    return abvf;
  }

  private AlexandriaAnnotationBody deframeAnnotationBody(AnnotationBodyVF annotationBodyVF) {
    TentativeAlexandriaProvenance provenance = deframeProvenance(annotationBodyVF);
    UUID uuid = getUUID(annotationBodyVF);
    return new AlexandriaAnnotationBody(uuid, annotationBodyVF.getType(), annotationBodyVF.getValue(), provenance);
  }

  private TentativeAlexandriaProvenance deframeProvenance(AlexandriaVF avf) {
    String provenanceWhen = avf.getProvenanceWhen();
    return new TentativeAlexandriaProvenance(avf.getProvenanceWho(), Instant.parse(provenanceWhen), avf.getProvenanceWhy());
  }

  private void setAlexandriaVFProperties(AlexandriaVF vf, Accountable accountable) {
    vf.setUuid(accountable.getId().toString());

    vf.setState(accountable.getState().toString());
    vf.setStateSince(accountable.getStateSince().getEpochSecond());

    AlexandriaProvenance provenance = accountable.getProvenance();
    vf.setProvenanceWhen(provenance.getWhen().toString());
    vf.setProvenanceWho(provenance.getWho());
    vf.setProvenanceWhy(provenance.getWhy());
  }

  // framedGraph methods

  private Supplier<NotFoundException> annotationNotFound(UUID oldAnnotationId) {
    return () -> new NotFoundException("no annotation found with uuid " + oldAnnotationId);
  }

  private BadRequestException incorrectStateException(UUID oldAnnotationId, String string) {
    return new BadRequestException("annotation " + oldAnnotationId + " is " + string);
  }

  private UUID getUUID(AlexandriaVF vf) {
    return UUID.fromString(vf.getUuid());
  }

  void updateState(AlexandriaVF vf, AlexandriaState newState) {
    vf.setState(newState.name());
    vf.setStateSince(Instant.now().getEpochSecond());
  }

  void annotate(AnnotationVF avf, UUID id) {
    AnnotationVF annotationToAnnotate = storage.readVF(AnnotationVF.class, id).get();
    avf.setAnnotatedAnnotation(annotationToAnnotate);
  }

}