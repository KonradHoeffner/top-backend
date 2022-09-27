package care.smith.top.backend.service;

import care.smith.top.backend.model.EntityDao;
import care.smith.top.backend.model.EntityVersionDao;
import care.smith.top.backend.model.LocalisableTextDao;
import care.smith.top.backend.model.RepositoryDao;
import care.smith.top.backend.repository.*;
import care.smith.top.model.*;
import care.smith.top.backend.util.ApiModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EntityService implements ContentService {
  @Value("${spring.paging.page-size:10}")
  private int pageSize;

  @Autowired private EntityRepository entityRepository;
  @Autowired private EntityVersionRepository entityVersionRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private PhenotypeRepository phenotypeRepository;
  @Autowired private RepositoryService repositoryService;

  @Override
  @Cacheable("entityCount")
  public long count() {
    return entityRepository.count();
  }

  @Cacheable("entityCount")
  public long count(EntityType... types) {
    return entityRepository.countByEntityTypeIn(types);
  }

  @Transactional
  @Caching(
      evict = {@CacheEvict("entityCount"), @CacheEvict(value = "entities", key = "#repositoryId")})
  public Entity createEntity(String organisationId, String repositoryId, Entity data) {
    if (entityRepository.existsById(data.getId()))
      throw new ResponseStatusException(HttpStatus.CONFLICT);
    RepositoryDao repository = getRepository(organisationId, repositoryId);

    if (data.getEntityType() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entityType is missing");

    EntityDao entity = new EntityDao(data).repository(repository);
    entity.currentVersion(new EntityVersionDao(data).version(1).entity(entity));

    if (data instanceof Category && ((Category) data).getSuperCategories() != null)
      for (Category category : ((Category) data).getSuperCategories())
        categoryRepository
            .findByIdAndRepositoryId(category.getId(), repositoryId)
            .ifPresent(entity::addSuperEntitiesItem);

    if (data instanceof Phenotype && ((Phenotype) data).getSuperPhenotype() != null)
      phenotypeRepository
          .findByIdAndRepositoryId(((Phenotype) data).getSuperPhenotype().getId(), repositoryId)
          .ifPresent(entity::addSuperEntitiesItem);

    return entityRepository.save(entity).toApiModel();
  }

  @Transactional
  @Caching(
      evict = {@CacheEvict("entityCount"), @CacheEvict(value = "entities", key = "#repositoryId")})
  public List<Entity> createFork(
      String organisationId,
      String repositoryId,
      String id,
      ForkingInstruction forkingInstruction,
      Integer version,
      List<String> include) {
    if (repositoryId.equals(forkingInstruction.getRepositoryId()))
      throw new ResponseStatusException(
          HttpStatus.NOT_ACCEPTABLE,
          String.format("Cannot create fork of entity '%s' in the same repository.", id));

    RepositoryDao originRepo = getRepository(organisationId, repositoryId);

    if (!originRepo.getPrimary())
      throw new ResponseStatusException(
          HttpStatus.NOT_ACCEPTABLE,
          String.format(
              "Cannot create fork of entity '%s' from non-primary repository '%s'.",
              id, originRepo.getId()));

    RepositoryDao destinationRepo =
        getRepository(forkingInstruction.getOrganisationId(), forkingInstruction.getRepositoryId());

    Entity entity = loadEntity(organisationId, repositoryId, id, null);

    List<Entity> origins = new ArrayList<>();
    if (ApiModelMapper.isRestricted(entity)) {
      Entity superPhenotype =
          loadEntity(
              organisationId, repositoryId, ((Phenotype) entity).getSuperPhenotype().getId(), null);
      origins.add(superPhenotype);
      origins.add(entity);
    } else {
      origins.add(entity);
      if (forkingInstruction.isCascade())
        origins.addAll(getSubclasses(organisationId, repositoryId, origins.get(0).getId(), null));
    }

    List<Entity> results = new ArrayList<>();
    //    for (Entity origin : origins) {
    //      String oldId = origin.getId();
    //      Optional<Entity> fork = categoryRepository.getFork(origin, destinationRepo);
    //
    //      if (!forkingInstruction.isUpdate() && fork.isPresent()) continue;
    //
    //      if (forkingInstruction.isUpdate() && fork.isPresent()) {
    //        if (fork.get().getEquivalentEntities().stream()
    //            .anyMatch(e -> e.getId().equals(origin.getId()))) continue;
    //        origin.setId(fork.get().getId());
    //        origin.setVersion(fork.get().getVersion() + 1);
    //        if (origin instanceof Phenotype) {
    //          ((Phenotype) origin).setSuperCategories(((Phenotype)
    // fork.get()).getSuperCategories());
    //        } else if (origin instanceof Category) {
    //          ((Category) origin).setSuperCategories(((Category)
    // fork.get()).getSuperCategories());
    //        }
    //      }
    //
    //      if (!forkingInstruction.isUpdate() || fork.isEmpty()) {
    //        origin.setId(UUID.randomUUID().toString());
    //        origin.setVersion(1);
    //        if (origin instanceof Phenotype) ((Phenotype) origin).setSuperCategories(null);
    //        else if (origin instanceof Category) ((Category) origin).setSuperCategories(null);
    //      }
    //
    //      if (origin instanceof Phenotype) {
    //        Phenotype phenotype = (Phenotype) origin;
    //        if (phenotype.getSuperPhenotype() != null) {
    //          Optional<Entity> superClass =
    //              categoryRepository.getFork(phenotype.getSuperPhenotype(), destinationRepo);
    //          if (superClass.isEmpty()) continue;
    //          phenotype.setSuperPhenotype((Phenotype) new
    // Phenotype().id(superClass.get().getId()));
    //        }
    //      }
    //
    //      if (origin.getVersion() == 1) {
    //        results.add(
    //            createEntity(forkingInstruction.getOrganisationId(), destinationRepo.getId(),
    // origin));
    //        categoryRepository.setFork(origin.getId(), oldId);
    //      } else {
    //        results.add(
    //            updateEntityById(
    //                forkingInstruction.getOrganisationId(),
    //                destinationRepo.getId(),
    //                origin.getId(),
    //                origin,
    //                null));
    //      }
    //
    //      Entity forkVersion = categoryRepository.findCurrentById(origin.getId()).orElseThrow();
    //      categoryRepository.findCurrentById(oldId).ifPresent(e -> e.addForksItem(forkVersion));
    //    }

    return results;
  }

  @Transactional
  @Caching(
      evict = {@CacheEvict("entityCount"), @CacheEvict(value = "entities", key = "#repositoryId")})
  public void deleteEntity(String organisationId, String repositoryId, String id) {
    getRepository(organisationId, repositoryId);
    EntityDao entity =
        entityRepository
            .findByIdAndRepositoryId(id, repositoryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    entityRepository.delete(entity);
  }

  @Transactional
  public void deleteVersion(
      String organisationId, String repositoryId, String id, Integer version) {
    getRepository(organisationId, repositoryId);
    EntityVersionDao entityVersion =
        entityVersionRepository
            .findByEntityIdAndVersion(id, version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!Objects.equals(entityVersion.getEntity().getRepository().getId(), repositoryId))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);

    EntityVersionDao currentVersion = entityVersion.getEntity().getCurrentVersion();
    if (currentVersion == null)
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Class does not have a current version.");

    if (entityVersion.equals(currentVersion))
      throw new ResponseStatusException(
          HttpStatus.NOT_ACCEPTABLE, "Current version of a class cannot be deleted.");

    EntityVersionDao previous = entityVersion.getPreviousVersion();
    EntityVersionDao next = entityVersion.getNextVersion();
    if (previous != null && next != null) entityVersionRepository.save(previous.nextVersion(next));

    entityVersionRepository.delete(entityVersion);
  }

  public StringWriter exportEntity(
      String organisationId, String repositoryId, String id, String format, Integer version) {
    StringWriter writer = new StringWriter();
    //    Collection<Entity> entities =
    //        getEntitiesByRepositoryId(organisationId, repositoryId, null, null, null, null, null);
    //    if ("vnd.r-project.r".equals(format)) {
    //      Collection<Phenotype> phenotypes =
    //          entities.stream()
    //              .filter(e -> !EntityType.CATEGORY.equals(e.getEntityType()))
    //              .map(e -> (Phenotype) e)
    //              .collect(Collectors.toList());
    //      try {
    //        Phenotype2RConverter converter = new Phenotype2RConverter(phenotypes);
    //        Entity entity = loadEntity(organisationId, repositoryId, id, version);
    //        if (EntityType.CATEGORY.equals(entity.getEntityType())) {
    //          for (Entity subClass :
    //              getSubclasses(organisationId, repositoryId, id, null).stream()
    //                  .filter(e -> !EntityType.CATEGORY.equals(e.getEntityType()))
    //                  .collect(Collectors.toList())) {
    //            converter.convert(subClass.getId(), writer);
    //            writer.append(System.lineSeparator());
    //          }
    //        } else {
    //          converter.convert(id, writer);
    //        }
    //      } catch (IOException e) {
    //        throw new RuntimeException(e);
    //      }
    //    } else {
    //      throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
    //    }
    return writer;
  }

  @Transactional
  public List<Entity> getEntities(
      List<String> include, String name, List<EntityType> type, DataType dataType, Integer page) {
    PageRequest pageRequest = PageRequest.of(page != null ? page - 1 : 0, pageSize);
    return phenotypeRepository
        .findAllByTitleAndEntityTypeAndDataType(name, type, dataType, pageRequest)
        .map(EntityDao::toApiModel)
        .getContent();

    //    TODO: add restrictions to resultset
    //    return entityRepository
    //        .findAllByRepositoryIdAndNameAndEntityTypeAndDataTypeAndPrimary(
    //            null, name, type, dataType, true, PageRequest.of(requestedPage, pageSize))
    //        .parallelStream()
    //        .flatMap(
    //            entity -> {
    //              Stream<Entity> result = Stream.of(entity);
    //              if (type == null
    //                  ||
    // type.contains(ApiModelMapper.toRestrictedEntityType(entity.getEntityType())))
    //                result = Stream.concat(result, entityRepository.findBySuperPhenotype(entity));
    //              return result;
    //            })
    //        .filter(distinctByKey(Entity::getId))
    //        .collect(Collectors.toList());
  }

  @Cacheable(
      value = "entities",
      key = "#repositoryId",
      condition = "#name == null && #type == null && #dataType == null")
  public List<Entity> getEntitiesByRepositoryId(
      String organisationId,
      String repositoryId,
      List<String> include,
      String name,
      List<EntityType> type,
      DataType dataType,
      Integer page) {
    getRepository(organisationId, repositoryId);
    PageRequest pageRequest = PageRequest.of(page != null ? page - 1 : 0, pageSize);

    return phenotypeRepository
        .findAllByRepositoryIdAndTitleAndEntityTypeAndDataType(
            repositoryId, name, type, dataType, pageRequest)
        .map(EntityDao::toApiModel)
        .getContent();

    //    TODO: add restrictions to resultset
    //    return entityRepository
    //        .findAllByRepositoryIdAndNameAndEntityTypeAndDataTypeAndPrimary(
    //            repositoryId, name, type, dataType, false, PageRequest.of(requestedPage,
    // pageSize))
    //        .parallelStream()
    //        .flatMap(
    //            entity -> {
    //              Stream<Entity> result = Stream.of(entity);
    //              //              if (type == null
    //              //                  ||
    //              // type.contains(ApiModelMapper.toRestrictedEntityType(entity.getEntityType())))
    //              //                result =
    //              //                    Stream.concat(result,
    //              // entityRepository.findBySuperPhenotype(entity));
    //              return result;
    //            })
    //        .filter(distinctByKey(Entity::getId))
    //        .collect(Collectors.toList());
  }

  public ForkingStats getForkingStats(
      String organisationId, String repositoryId, String id, List<String> include) {
    getRepository(organisationId, repositoryId);
    EntityDao entity =
        entityRepository
            .findByIdAndRepositoryId(id, repositoryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    ForkingStats forkingStats = new ForkingStats();
    EntityDao origin = entity.getOrigin();
    if (origin != null && origin.getCurrentVersion() != null)
      forkingStats.origin(
          new Entity()
              .id(origin.getId())
              .titles(
                  origin.getCurrentVersion().getTitles().stream()
                      .map(LocalisableTextDao::toApiModel)
                      .collect(Collectors.toList())));
    if (entity.getForks() != null)
      entity.getForks().stream()
          .map(
              f -> {
                RepositoryDao repository = f.getRepository();
                return new Entity()
                    .id(f.getId())
                    .repository(new Repository().id(repository.getId()).name(repository.getName()));
              })
          .forEach(forkingStats::addForksItem);
    return forkingStats;
  }

  public List<Entity> getRootEntitiesByRepositoryId(
      String organisationId,
      String repositoryId,
      List<String> include,
      String name,
      List<EntityType> type,
      DataType dataType,
      Integer page) {
    getRepository(organisationId, repositoryId);
    PageRequest pageRequest = PageRequest.of(page == null ? 0 : page - 1, pageSize);
    return entityRepository
        .findAllByRepositoryIdAndSuperEntitiesEmpty(repositoryId, pageRequest)
        .map(EntityDao::toApiModel)
        .getContent();
  }

  public List<Entity> getSubclasses(
      String organisationId, String repositoryId, String id, List<String> include) {
    getRepository(organisationId, repositoryId);
    return categoryRepository.findAllByRepositoryIdAndSuperEntities_Id(repositoryId, id).stream()
        .map(EntityDao::toApiModel)
        .collect(Collectors.toList());
  }

  public List<Entity> getVersions(
      String organisationId, String repositoryId, String id, List<String> include) {
    getRepository(organisationId, repositoryId);
    return entityVersionRepository.findAllByEntityRepositoryIdAndEntityId(repositoryId, id).stream()
        .map(EntityVersionDao::toApiModel)
        .collect(Collectors.toList());
  }

  public Entity loadEntity(String organisationId, String repositoryId, String id, Integer version) {
    getRepository(organisationId, repositoryId);
    if (version == null)
      return entityRepository
          .findByIdAndRepositoryId(id, repositoryId)
          .map(EntityDao::toApiModel)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    return entityVersionRepository
        .findByRepositoryIdAndEntityIdAndVersion(repositoryId, id, version)
        .map(EntityVersionDao::toApiModel)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @CacheEvict(value = "entities", key = "#repositoryId")
  @Transactional
  public Entity setCurrentEntityVersion(
      String organisationId,
      String repositoryId,
      String id,
      Integer version,
      List<String> include) {
    getRepository(organisationId, repositoryId);

    EntityDao entity =
        entityRepository
            .findByIdAndRepositoryId(id, repositoryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    EntityVersionDao entityVersion =
        entityVersionRepository
            .findByRepositoryIdAndEntityIdAndVersion(repositoryId, id, version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    return entityRepository.save(entity.currentVersion(entityVersion)).toApiModel();
  }

  @CacheEvict(value = "entities", key = "#repositoryId")
  @Transactional
  public Entity updateEntityById(
      String organisationId, String repositoryId, String id, Entity data, List<String> include) {
    getRepository(organisationId, repositoryId);
    EntityDao entity =
        entityRepository
            .findByIdAndRepositoryId(id, repositoryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    EntityVersionDao latestVersion = entityVersionRepository.findByEntityIdAndNextVersionIsNull(id);
    EntityVersionDao newVersion = new EntityVersionDao(data).entity(entity);

    if (latestVersion != null)
      newVersion.previousVersion(latestVersion).version(latestVersion.getVersion() + 1);
    else newVersion.version(0);

    newVersion = entityVersionRepository.save(newVersion);

    return entityRepository.save(entity.currentVersion(newVersion)).toApiModel();
  }

  /**
   * Get {@link Repository} by repositoryId and directoryId. If the repository does not exist or is
   * not associated with the directory, this method will throw an exception.
   *
   * @param organisationId ID of the {@link Organisation}
   * @param repositoryId ID of the {@link Repository}
   * @return The matching repository, if it exists.
   */
  private RepositoryDao getRepository(String organisationId, String repositoryId) {
    return repositoryService
        .getRepository(organisationId, repositoryId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    String.format("Repository '%s' does not exist!", repositoryId)));
  }
}
