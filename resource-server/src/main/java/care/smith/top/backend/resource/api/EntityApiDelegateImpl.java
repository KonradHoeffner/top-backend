package care.smith.top.backend.resource.api;

import care.smith.top.backend.api.EntityApiDelegate;
import care.smith.top.backend.model.DataType;
import care.smith.top.backend.model.Entity;
import care.smith.top.backend.model.EntityType;
import care.smith.top.backend.resource.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EntityApiDelegateImpl implements EntityApiDelegate {
  @Autowired EntityService entityService;

  @Override
  public ResponseEntity<Entity> createEntity(
      String organisationId, String repositoryId, Entity entity, List<String> include) {
    return new ResponseEntity<>(
        entityService.createEntity(organisationId, repositoryId, entity), HttpStatus.CREATED);
  }

  @Override
  public ResponseEntity<Entity> getEntityById(
      String organisationId, String repositoryId, UUID id, Integer version, List<String> include) {
    return new ResponseEntity<>(
        entityService.loadEntity(organisationId, repositoryId, id, version), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteEntityById(
      String organisationId,
      String repositoryId,
      UUID id,
      Integer version,
      List<String> include,
      Boolean permanent) {
    entityService.deleteEntity(organisationId, repositoryId, id, version, permanent);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Entity> updateEntityById(
      String organisationId,
      String repositoryId,
      UUID id,
      Entity entity,
      Integer version,
      List<String> include) {
    return new ResponseEntity<>(
        entityService.updateEntityById(organisationId, repositoryId, id, entity, include),
        HttpStatus.OK);
  }

  @Override
  public ResponseEntity<List<Entity>> getEntities(
      String organisationId,
      String repositoryId,
      List<String> include,
      String name,
      EntityType type,
      DataType dataType,
      Integer page) {
    return new ResponseEntity<>(
        entityService.getEntities(
            organisationId, repositoryId, include, name, type, dataType, page),
        HttpStatus.OK);
  }
}
